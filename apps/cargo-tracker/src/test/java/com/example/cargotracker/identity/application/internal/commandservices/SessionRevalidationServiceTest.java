package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserStanding;
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditResult;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * session の確かめのサービス（Bolt 32c、US-18 AC6、IA-INV-04・IA-INV-08、ADR-012 の見直しの 3）。今のドメインの規則で判定し、
 * 拒否なら SESSION_REVOKED を 1 つのトランザクションで書き、拒否でなければ今の役割・表示名・企業名の主体を返す。
 */
class SessionRevalidationServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-14T00:00:00Z");

    /** 何もしないトランザクションマネージャー。始めた・ロールバックした回数を覚える。 */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int begun;
        private int rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begun++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // 何もしない
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack++;
        }
    }

    private final UserId userId = new UserId(UUID.randomUUID());
    private final CompanyId companyId = new CompanyId(UUID.randomUUID());
    private final MutableClock clock = new MutableClock();
    private final List<AuditRecord> audit = new ArrayList<>();
    private final AtomicInteger reads = new AtomicInteger();
    private Optional<UserStanding> stored = Optional.empty();
    private RuntimeException appendFailure;
    private final AuditRecordRepository auditRecords = new AuditRecordRepository() {
        @Override
        public void append(AuditRecord auditRecord) {
            if (appendFailure != null) {
                throw appendFailure;
            }
            audit.add(auditRecord);
        }

        @Override
        public List<AuditRecord> findByActorUserId(UserId actor) {
            return audit.stream().filter(r -> actor.equals(r.actorUserId())).toList();
        }

        @Override
        public Optional<SuspensionRecord> findLatestUserSuspension(UserId target) {
            return Optional.empty();
        }
    };
    private final UserStandingRepository standings = id -> {
        reads.incrementAndGet();
        return stored.filter(standing -> standing.user().id().equals(id));
    };
    private final RecordingTransactionManager transactions = new RecordingTransactionManager();
    private final SessionRevalidationService service =
            new SessionRevalidationService(standings, auditRecords, clock, new TransactionTemplate(transactions));

    @BeforeEach
    void 時計を合わせる() {
        clock.setInstant(T0);
    }

    private void store(UserStatus status, boolean companyActive, Set<Role> roles) {
        Company company = Company.of(companyId, "荷主 A", CompanyKind.SHIPPER, companyActive);
        User user =
                User.of(userId, companyId, EmailAddress.of("shipper@example.com"), "荷主 太郎", "{noop}x", status, roles);
        stored = Optional.of(new UserStanding(user, company));
    }

    @Test
    void 認証できる利用者は今の役割と表示名と企業名の主体を返し監査記録を書かない() {
        store(UserStatus.ACTIVE, true, Set.of(Role.SHIPPER, Role.SALES));

        SessionStanding standing = service.revalidate(userId, companyId);

        assertThat(standing).isInstanceOfSatisfying(SessionStanding.Current.class, current -> {
            assertThat(current.actor().userId()).isEqualTo(userId);
            assertThat(current.actor().companyId()).isEqualTo(companyId);
            assertThat(current.actor().roles()).containsExactlyInAnyOrder(Role.SHIPPER, Role.SALES);
            assertThat(current.actor().displayName()).isEqualTo("荷主 太郎");
            assertThat(current.actor().companyName()).isEqualTo("荷主 A");
            assertThat(current.email()).isEqualTo(EmailAddress.of("shipper@example.com"));
        });
        assertThat(audit).isEmpty();
        assertThat(transactions.begun).as("拒否でなければトランザクションを始めない").isZero();
        assertThat(reads).as("読み出しは 1 回").hasValue(1);
    }

    @Test
    void 利用停止の利用者はSUSPENDEDで取り消し監査記録を1行書く() {
        store(UserStatus.SUSPENDED, true, Set.of(Role.SHIPPER));

        SessionStanding standing = service.revalidate(userId, companyId);

        assertThat(standing).isEqualTo(new SessionStanding.Revoked(AuthenticationRejection.SUSPENDED));
        assertThat(audit).singleElement().satisfies(r -> {
            assertThat(r.action()).isEqualTo(AuditAction.SESSION_REVOKED);
            assertThat(r.result()).isEqualTo(AuditResult.FAILURE);
            assertThat(r.reason()).isEqualTo("SUSPENDED");
            assertThat(r.actorUserId()).isEqualTo(userId);
            assertThat(r.actorCompanyId()).isEqualTo(companyId);
            assertThat(r.occurredAt()).isEqualTo(new UtcInstant(T0));
        });
        assertThat(transactions.begun).as("監査記録はトランザクションで書く").isEqualTo(1);
        assertThat(reads).hasValue(1);
    }

    @Test
    void 所属企業が無効な利用者はCOMPANY_INACTIVEで取り消す() {
        store(UserStatus.ACTIVE, false, Set.of(Role.SHIPPER));

        SessionStanding standing = service.revalidate(userId, companyId);

        assertThat(standing).isEqualTo(new SessionStanding.Revoked(AuthenticationRejection.COMPANY_INACTIVE));
        assertThat(audit).singleElement().satisfies(r -> assertThat(r.reason()).isEqualTo("COMPANY_INACTIVE"));
    }

    @Test
    void 見つからない利用者はUNKNOWN_USERで取り消しsessionの主体で記録する() {
        SessionStanding standing = service.revalidate(userId, companyId);

        assertThat(standing).isEqualTo(new SessionStanding.Revoked(AuthenticationRejection.UNKNOWN_USER));
        assertThat(audit).singleElement().satisfies(r -> {
            assertThat(r.reason()).isEqualTo("UNKNOWN_USER");
            assertThat(r.actorUserId()).isEqualTo(userId);
            assertThat(r.actorCompanyId()).isEqualTo(companyId);
        });
    }

    @Test
    void 監査記録の追記が失敗したら例外を投げロールバックする() {
        store(UserStatus.SUSPENDED, true, Set.of(Role.SHIPPER));
        appendFailure = new IllegalStateException("DB の誤り");

        assertThatThrownBy(() -> service.revalidate(userId, companyId)).isSameAs(appendFailure);
        assertThat(transactions.rolledBack).isEqualTo(1);
    }

    @Test
    void 読み出しの誤りはそのまま投げ監査記録を書かない() {
        RuntimeException failure = new IllegalStateException("DB の誤り");
        SessionRevalidationService failing = new SessionRevalidationService(
                id -> {
                    throw failure;
                },
                auditRecords,
                clock,
                new TransactionTemplate(transactions));

        assertThatThrownBy(() -> failing.revalidate(userId, companyId)).isSameAs(failure);
        assertThat(audit).isEmpty();
    }
}

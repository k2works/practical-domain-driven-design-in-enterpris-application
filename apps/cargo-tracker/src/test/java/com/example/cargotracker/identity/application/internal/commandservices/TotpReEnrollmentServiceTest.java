package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.acceptance.InMemoryRecoveryCodeRepository;
import com.example.cargotracker.identity.acceptance.InMemoryTotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpSecretIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.UnreadableCredentialException;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.SessionId;
import com.example.cargotracker.identity.domain.model.valueobjects.TotpProvisioning;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 認証アプリの再登録（Bolt 33b、US-18 AC9、SEC-10、IA-INV-10・11）。新しいコードを確かめてから、資格を置き換え、回復コード 10 個を
 * 発行し直し、監査を書き、確定の後に今の session のほかを失効させる。
 */
class TotpReEnrollmentServiceTest {

    /** 今の刻みは 100（epoch 3000 秒 / 30）。 */
    private static final Instant NOW = Instant.ofEpochSecond(3_000);

    private static final UtcInstant STARTED = new UtcInstant(NOW.minus(Duration.ofMinutes(1)));
    private static final SessionId CURRENT_SESSION = new SessionId("current-session");
    private static final String CODE = "000100";

    /** 偽の秘密の発行。暗号文は持ち主の ID を含み、別の持ち主では表示の値を作らない（AAD の代わり）。 */
    private static final TotpSecretIssuer ISSUER = new TotpSecretIssuer() {
        @Override
        public EncryptedSecret issue(UserId owner) {
            return new EncryptedSecret("v1:" + owner.value() + ":" + UUID.randomUUID());
        }

        @Override
        public TotpProvisioning provisioning(UserId owner, EncryptedSecret secret, String accountName) {
            requireOwner(owner, secret);
            return new TotpProvisioning("otpauth://" + accountName, "KEY " + secret.value(), "<svg/>");
        }
    };

    private static void requireOwner(UserId owner, EncryptedSecret secret) {
        if (!secret.value().contains(owner.value().toString())) {
            throw new UnreadableCredentialException("別の利用者の暗号文");
        }
    }

    /** 偽の生成器。持ち主の違う暗号文は復号できず、コードは刻みの番号を 6 桁にしたもの。 */
    private static final TotpCodeGenerator GENERATOR = (owner, secret, step) -> {
        requireOwner(owner, secret);
        return String.format("%06d", step % 1_000_000);
    };

    private static final RecoveryCodeHasher HASHER = new RecoveryCodeHasher() {
        @Override
        public String hash(String code) {
            return "hashed:" + code;
        }

        @Override
        public boolean matches(String code, String hash) {
            return hash.equals("hashed:" + code);
        }
    };

    private static final List<String> RECOVERY_CODES = IntStream.rangeClosed(1, RecoveryCode.CODES_PER_USER)
            .mapToObj(i -> String.format("bbbb-%04d", i))
            .toList();

    private final Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
    private final User user = User.of(
            new UserId(UUID.randomUUID()),
            company.id(),
            EmailAddress.of("shipper@example.com"),
            "荷主 太郎",
            "{bcrypt}hash",
            UserStatus.ACTIVE,
            Set.of(Role.SHIPPER));
    private final EncryptedSecret oldSecret =
            new EncryptedSecret("v1:" + user.id().value() + ":old");

    private final InMemoryTotpCredentialRepository credentials = new InMemoryTotpCredentialRepository();
    private final InMemoryRecoveryCodeRepository recoveryCodes = new InMemoryRecoveryCodeRepository();
    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final UserSessionRevoker sessions = mock(UserSessionRevoker.class);
    private final List<AuditRecord> audit = new ArrayList<>();
    private final AuditRecordRepository auditRecords = new AuditRecordRepository() {
        @Override
        public void append(AuditRecord auditRecord) {
            if (failAudit) {
                throw new IllegalStateException("監査の表に届かない");
            }
            audit.add(auditRecord);
        }

        @Override
        public List<AuditRecord> findByActorUserId(UserId actor) {
            return audit.stream().filter(r -> actor.equals(r.actorUserId())).toList();
        }
    };
    private boolean failAudit;
    private final RecordingTransactions transactions = new RecordingTransactions();

    private final TotpReEnrollmentService service = new TotpReEnrollmentService(
            users,
            companies,
            credentials,
            recoveryCodes,
            auditRecords,
            ISSUER,
            () -> RECOVERY_CODES,
            HASHER,
            GENERATOR,
            sessions,
            Clock.fixed(NOW, ZoneOffset.UTC),
            transactions);

    private EncryptedSecret givenEnrolledUser() {
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(companies.findById(company.id())).thenReturn(Optional.of(company));
        credentials.add(TotpCredential.enroll(user.id(), oldSecret, new UtcInstant(NOW.minus(Duration.ofDays(30)))));
        return ISSUER.issue(user.id());
    }

    private ReEnrollmentResult complete(EncryptedSecret secret, String code, UtcInstant startedAt) {
        return service.complete(user.id(), secret, code, startedAt, CURRENT_SESSION);
    }

    @Test
    void 新しいコードを確かめると資格を置き換え回復コード10個を発行し直し監査を書き確定の後にほかのsessionを失効させる() {
        EncryptedSecret renewed = givenEnrolledUser();
        when(sessions.revokeOthers(any(), any(SessionId.class))).thenAnswer(invocation -> {
            assertThat(transactions.committed).as("失効の時点で確定している").isTrue();
            return 1;
        });

        ReEnrollmentResult result = complete(renewed, CODE, STARTED);

        assertThat(result).isEqualTo(new ReEnrollmentResult.ReEnrolled(RECOVERY_CODES));
        TotpCredential replaced = credentials.findByUserId(user.id()).orElseThrow();
        assertThat(replaced.secret()).isEqualTo(renewed);
        assertThat(replaced.lastUsedStep()).as("確かめたコードの刻みは使ったことにする").isEqualTo(100);
        assertThat(recoveryCodes.findUnusedByUserId(user.id())).hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(audit).singleElement().satisfies(written -> {
            assertThat(written.action()).isEqualTo(AuditAction.TOTP_RE_ENROLLED);
            assertThat(written.actorUserId()).isEqualTo(user.id());
            assertThat(written.actorCompanyId()).isEqualTo(company.id());
        });
        verify(sessions).revokeOthers(user.email(), CURRENT_SESSION);
    }

    @Test
    void コードが違えば置き換えず失効もさせない() {
        EncryptedSecret renewed = givenEnrolledUser();

        assertThat(complete(renewed, "123456", STARTED)).isEqualTo(new ReEnrollmentResult.CodeRejected());

        assertThat(credentials.findByUserId(user.id()).orElseThrow().secret()).isEqualTo(oldSecret);
        assertThat(audit).isEmpty();
        verify(sessions, never()).revokeOthers(any(), any(SessionId.class));
    }

    @Test
    void 始めてから15分を過ぎたらやり直す() {
        EncryptedSecret renewed = givenEnrolledUser();

        assertThat(complete(renewed, CODE, new UtcInstant(NOW.minus(Duration.ofMinutes(15)))))
                .as("ちょうど 15 分")
                .isEqualTo(new ReEnrollmentResult.Expired());
        assertThat(complete(
                        renewed,
                        CODE,
                        new UtcInstant(NOW.minus(Duration.ofMinutes(15)).plusSeconds(1))))
                .as("15 分の 1 秒前")
                .isInstanceOf(ReEnrollmentResult.ReEnrolled.class);
    }

    @Test
    void 別の利用者の暗号文は使えない() {
        givenEnrolledUser();

        assertThat(complete(ISSUER.issue(new UserId(UUID.randomUUID())), CODE, STARTED))
                .isEqualTo(new ReEnrollmentResult.SecretUnusable());
    }

    @Test
    void 資格のない利用者と止められた利用者は再登録できない() {
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(companies.findById(company.id())).thenReturn(Optional.of(company));
        EncryptedSecret renewed = ISSUER.issue(user.id());

        assertThat(complete(renewed, CODE, STARTED)).as("資格がない").isEqualTo(new ReEnrollmentResult.Unavailable());
        assertThat(service.begin(user.id(), Optional.empty())).isEqualTo(new ReEnrollmentStart.Unavailable());

        credentials.add(TotpCredential.enroll(user.id(), oldSecret, STARTED));
        when(users.findById(user.id()))
                .thenReturn(Optional.of(User.of(
                        user.id(),
                        company.id(),
                        user.email(),
                        "荷主 太郎",
                        "{bcrypt}hash",
                        UserStatus.SUSPENDED,
                        Set.of(Role.SHIPPER))));
        assertThat(complete(renewed, CODE, STARTED)).as("利用停止").isEqualTo(new ReEnrollmentResult.Unavailable());
        assertThat(service.begin(user.id(), Optional.empty())).isEqualTo(new ReEnrollmentStart.Unavailable());
    }

    @Test
    void 同じ秘密でもう一度送っても置き換えも発行し直しもしない() {
        EncryptedSecret renewed = givenEnrolledUser();
        assertThat(complete(renewed, CODE, STARTED)).isInstanceOf(ReEnrollmentResult.ReEnrolled.class);

        // 二度押し・別のタブ。先の再登録で示した回復コードを無効にしない
        assertThat(complete(renewed, CODE, STARTED)).isEqualTo(new ReEnrollmentResult.Superseded());

        assertThat(audit).hasSize(1);
        verify(sessions).revokeOthers(user.email(), CURRENT_SESSION);
    }

    @Test
    void 失効に失敗しても再登録は終える() {
        EncryptedSecret renewed = givenEnrolledUser();
        when(sessions.revokeOthers(any(), any(SessionId.class)))
                .thenThrow(new IllegalStateException("session の表に届かない"));

        assertThat(complete(renewed, CODE, STARTED)).isInstanceOf(ReEnrollmentResult.ReEnrolled.class);
        assertThat(credentials.findByUserId(user.id()).orElseThrow().secret()).isEqualTo(renewed);
    }

    @Test
    void 監査を書けなければ例外になり失効させない() {
        EncryptedSecret renewed = givenEnrolledUser();
        failAudit = true;

        assertThatThrownBy(() -> complete(renewed, CODE, STARTED)).isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).revokeOthers(any(), any(SessionId.class));
    }

    @Test
    void 外側のトランザクションの中では呼べない() {
        EncryptedSecret renewed = givenEnrolledUser();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> complete(renewed, CODE, STARTED)).isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(sessions, never()).revokeOthers(any(), any(SessionId.class));
    }

    @Test
    void 始めるときは登録の途中の暗号文を使い続け無ければ新しく発行する() {
        EncryptedSecret pending = givenEnrolledUser();

        ReEnrollmentStart reused = service.begin(user.id(), Optional.of(pending));
        ReEnrollmentStart issued = service.begin(user.id(), Optional.empty());
        ReEnrollmentStart othersPending =
                service.begin(user.id(), Optional.of(ISSUER.issue(new UserId(UUID.randomUUID()))));

        assertThat(reused).isInstanceOfSatisfying(ReEnrollmentStart.Started.class, started -> {
            assertThat(started.secret()).isEqualTo(pending);
            assertThat(started.provisioning().otpauthUri()).isEqualTo("otpauth://shipper@example.com");
        });
        assertThat(issued)
                .isInstanceOfSatisfying(
                        ReEnrollmentStart.Started.class,
                        started -> assertThat(started.secret()).isNotEqualTo(pending));
        assertThat(othersPending)
                .isInstanceOfSatisfying(
                        ReEnrollmentStart.Started.class,
                        started -> assertThat(started.secret().value())
                                .contains(user.id().value().toString()));
    }

    /** 確定したことを覚える、何もしないトランザクションマネージャー（単体テスト用）。 */
    private static final class RecordingTransactions extends TransactionTemplate {
        private boolean committed;

        RecordingTransactions() {
            super(new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                    // 何もしない
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                    // 何もしない
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                    // 何もしない
                }
            });
        }

        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            T result = super.execute(action);
            committed = true;
            return result;
        }
    }
}

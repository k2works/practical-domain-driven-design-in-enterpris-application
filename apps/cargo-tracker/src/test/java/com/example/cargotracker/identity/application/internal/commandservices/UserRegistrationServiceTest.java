package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.domain.events.UserRegistered;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UnusablePasswordHashIssuer;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserRegistrationRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 利用者の登録のサービス（Bolt 38b2。US-16 AC1・AC2・AC3）。拒否の理由ごとに 1 行（T-161・T-182）。 */
class UserRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");
    private static final EmailAddress EMAIL = EmailAddress.of("hanako@example.com");
    private static final String HASH = "{bcrypt}unusable";

    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
    private final UserAssignmentRepository assignments = mock(UserAssignmentRepository.class);
    private final UnusablePasswordHashIssuer hashes = mock(UnusablePasswordHashIssuer.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserRegistrationService service = new UserRegistrationService(
            users,
            companies,
            assignments,
            auditRecords,
            hashes,
            events,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new NoTransactions());

    private final Company operatorCompany =
            Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
    private final AuthenticatedActor admin = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");

    private void given(Company company, boolean inScope) {
        when(companies.findById(company.id())).thenReturn(Optional.of(company));
        when(assignments.scopeOf(admin.userId()))
                .thenReturn(new AdministrationScope(inScope ? Set.of(company.id()) : Set.of()));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(hashes.issue()).thenReturn(HASH);
    }

    private UserRegistrationOutcome register(Company company, String displayName, Set<Role> roles) {
        return service.register(admin, company.id(), EMAIL, displayName, roles);
    }

    private void verifyNothingWritten() {
        verify(users, never()).addRegistered(any(), any(), any());
        verify(auditRecords, never()).append(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void 管理範囲の有効な企業に登録して利用者と監査を書く() {
        // 行 1
        given(operatorCompany, true);

        UserRegistrationOutcome outcome = register(operatorCompany, "  新人 花子 ", Set.of(Role.SALES));

        assertThat(outcome).isInstanceOf(UserRegistrationOutcome.Registered.class);
        UserId registered = ((UserRegistrationOutcome.Registered) outcome).userId();
        ArgumentCaptor<UserRegistered> event = ArgumentCaptor.forClass(UserRegistered.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(registered);
        assertThat(event.getValue().registeredAt()).isEqualTo(new UtcInstant(NOW));
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users)
                .addRegistered(
                        user.capture(),
                        org.mockito.ArgumentMatchers.eq(admin.userId()),
                        org.mockito.ArgumentMatchers.eq(new UtcInstant(NOW)));
        assertThat(user.getValue().id()).isEqualTo(registered);
        assertThat(user.getValue().companyId()).isEqualTo(operatorCompany.id());
        assertThat(user.getValue().displayName()).isEqualTo("新人 花子");
        assertThat(user.getValue().passwordHash()).isEqualTo(HASH);
        assertThat(user.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getValue().roles()).containsExactly(Role.SALES);
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_REGISTERED);
        assertThat(audit.getValue().target())
                .isEqualTo(new AuditTarget("USER", registered.value().toString(), "0", null, "SALES"));
    }

    @Test
    void システム管理者でなければ拒み何も書かない() {
        // 行 2
        given(operatorCompany, true);
        AuthenticatedActor sales =
                new AuthenticatedActor(admin.userId(), operatorCompany.id(), Set.of(Role.SALES), "営業 一郎", "A 社");

        assertThat(service.register(sales, operatorCompany.id(), EMAIL, "新人 花子", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.NotPermitted()));
        verifyNothingWritten();

        // 対照: 同じ準備でシステム管理者なら書く（T-106）
        assertThat(register(operatorCompany, "新人 花子", Set.of(Role.SALES)))
                .isInstanceOf(UserRegistrationOutcome.Registered.class);
        verify(users).addRegistered(any(), any(), any());
    }

    @Test
    void 企業がなければ拒み何も書かない() {
        // 行 3
        CompanyId missing = new CompanyId(UUID.randomUUID());
        when(companies.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.register(admin, missing, EMAIL, "新人 花子", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.CompanyNotFound()));
        verifyNothingWritten();
    }

    @Test
    void 管理範囲の外の無効な企業は範囲外として拒み試行だけを記録する() {
        // 行 3b（無効でも InactiveCompany にしない。範囲を先に見る）
        Company inactive = Company.of(new CompanyId(UUID.randomUUID()), "B 社", CompanyKind.SHIPPER, false);
        given(inactive, false);

        assertThat(register(inactive, "新人 花子", Set.of(Role.SHIPPER)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.CompanyOutOfScope()));
        verify(users, never()).addRegistered(any(), any(), any());
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.ADMIN_SCOPE_DENIED);
        assertThat(audit.getValue().target())
                .isEqualTo(new AuditTarget("COMPANY", inactive.id().value().toString(), null, null, null));
    }

    @Test
    void 確かめるときは範囲外の試行を記録しない() {
        // 行 3b・行 11
        Company other = Company.of(new CompanyId(UUID.randomUUID()), "B 社", CompanyKind.SHIPPER, true);
        given(other, false);

        assertThat(service.check(admin, other.id(), EMAIL, "新人 花子", Set.of(Role.SHIPPER)))
                .isEqualTo(new UserRegistrationCheck.Refused(new UserRegistrationRefusal.CompanyOutOfScope()));
        verifyNothingWritten();
    }

    @Test
    void 範囲外の企業への確定の試行は値を解釈する前に記録できる() {
        // Bolt 38b3b の開発レビューの高-1（画面が入力の誤りや役割の名前を解釈する前に呼ぶ）
        Company other = Company.of(new CompanyId(UUID.randomUUID()), "B 社", CompanyKind.SHIPPER, true);
        given(other, false);

        assertThat(service.denyOutOfScope(admin, other.id())).isTrue();

        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.ADMIN_SCOPE_DENIED);
        assertThat(audit.getValue().target())
                .isEqualTo(new AuditTarget("COMPANY", other.id().value().toString(), null, null, null));
    }

    @Test
    void 範囲内の企業とない企業とシステム管理者でない操作者では範囲外の試行を記録しない() {
        // Bolt 38b3b の開発レビューの高-1（サービスが操作者と管理範囲を確かめ直す）
        given(operatorCompany, true);
        CompanyId missing = new CompanyId(UUID.randomUUID());
        when(companies.findById(missing)).thenReturn(Optional.empty());
        AuthenticatedActor sales = new AuthenticatedActor(
                new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SALES), "営業 一郎", "A 社");

        assertThat(service.denyOutOfScope(admin, operatorCompany.id())).isFalse();
        assertThat(service.denyOutOfScope(admin, missing)).isFalse();
        assertThat(service.denyOutOfScope(sales, operatorCompany.id())).isFalse();
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 管理範囲の無効な企業は理由を示して拒む() {
        // 行 5
        Company inactive = Company.of(new CompanyId(UUID.randomUUID()), "B 社", CompanyKind.SHIPPER, false);
        given(inactive, true);

        assertThat(register(inactive, "新人 花子", Set.of(Role.SHIPPER)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.InactiveCompany()));
        verifyNothingWritten();
    }

    @ParameterizedTest(name = "[{index}] 空や見分けにくい表示名を拒む")
    @ValueSource(
            strings = {
                "",
                "   ",
                "新人\u0007花子",
                "改行\n入り",
                "\u00A0\u00A0",
                "\u202F",
                "\u2007",
                "\u3000",
                "新人\u202E花子",
                "新人\u200B花子",
                "\uFEFF新人",
                "新人\u2028花子",
                "新人\u2029花子",
                "新人\uDB40\uDC61花子"
            })
    void 空や制御文字の表示名を拒む(String displayName) {
        // 行 6（Bolt 38b2）。Bolt 38b3 の行 1・行 2（NBSP などの空白だけ、書式文字、行と段落の区切り。U+3000 だけは今も拒む）
        given(operatorCompany, true);

        assertThat(register(operatorCompany, displayName, Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.InvalidDisplayName()));
        verifyNothingWritten();
    }

    @Test
    void 表示名の前後のNBSPを除いて受け付ける() {
        // Bolt 38b3 の行 1（T-206。保存した表示名の全文で確かめる）
        given(operatorCompany, true);

        register(operatorCompany, "\u00A0\u3000新人 花子\u202F", Set.of(Role.SALES));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).addRegistered(user.capture(), any(), any());
        assertThat(user.getValue().displayName()).isEqualTo("新人 花子");
    }

    @Test
    void 表示名は200文字まで受け付け201文字を拒む() {
        // 行 6（コードポイントで数える。サロゲートペアの文字は 1 文字）
        given(operatorCompany, true);
        String max = "𠮷".repeat(200);

        assertThat(register(operatorCompany, max, Set.of(Role.SALES)))
                .isInstanceOf(UserRegistrationOutcome.Registered.class);
        assertThat(register(operatorCompany, max + "あ", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.InvalidDisplayName()));
        // 前後の空白を除いてから数える（Bolt 38b3）
        assertThat(register(operatorCompany, "\u00A0" + max + "\u202F", Set.of(Role.SALES)))
                .isInstanceOf(UserRegistrationOutcome.Registered.class);
    }

    @Test
    void 登録の判定が拒めばそのまま返す() {
        // 行 7
        given(operatorCompany, true);

        assertThat(register(operatorCompany, "新人 花子", Set.of(Role.SYSTEM_ADMIN)))
                .isEqualTo(new UserRegistrationOutcome.Refused(
                        new UserRegistrationRefusal.Rejected(new UserRegistrationRejection(
                                UserRegistrationRejectionReason.OVERSIGHT_ROLE, Set.of(Role.SYSTEM_ADMIN)))));
        verifyNothingWritten();
    }

    @Test
    void 既にいるメールアドレスは確かめるときも登録するときも拒みハッシュを作らない() {
        // 行 8（範囲の外の利用者でも同じ）
        given(operatorCompany, true);
        User existing = User.of(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                EMAIL,
                "別の 人",
                "{bcrypt}hash",
                UserStatus.ACTIVE,
                Set.of(Role.SHIPPER));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        assertThat(service.check(admin, operatorCompany.id(), EMAIL, "新人 花子", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationCheck.Refused(new UserRegistrationRefusal.DuplicateEmail()));
        assertThat(register(operatorCompany, "新人 花子", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.DuplicateEmail()));
        verify(hashes, never()).issue();
        verifyNothingWritten();
    }

    @Test
    void 受け付けられる登録を確かめても何も書かない() {
        // 行 11
        given(operatorCompany, true);

        assertThat(service.check(admin, operatorCompany.id(), EMAIL, "新人 花子", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationCheck.Acceptable());
        verify(hashes, never()).issue();
        verifyNothingWritten();
    }

    /** トランザクションを持たない TransactionTemplate（単体テストの偽物）。 */
    private static final class NoTransactions extends TransactionTemplate {

        NoTransactions() {
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
    }
}

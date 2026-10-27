package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.domain.events.TotpResetByAdmin;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailure;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.rules.IdentityVerificationPolicy.Violation;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.identity.domain.model.valueobjects.IdentityVerificationMethod;
import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** ログインの資格の管理のサービス（Bolt 38c。US-18 AC3・AC9。行 3・4・6・8a・8b・9）。 */
class UserLoginCredentialServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:40:00Z");
    private static final UtcInstant ENROLLED_AT = new UtcInstant(Instant.parse("2026-10-01T00:00:00Z"));
    private static final UtcInstant VERIFIED_AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));

    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final UserAssignmentRepository assignments = mock(UserAssignmentRepository.class);
    private final LoginFailureRepository loginFailures = mock(LoginFailureRepository.class);
    private final TotpCredentialRepository totpCredentials = mock(TotpCredentialRepository.class);
    private final RecoveryCodeRepository recoveryCodes = mock(RecoveryCodeRepository.class);
    private final AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
    private final UserSessionRevoker sessions = mock(UserSessionRevoker.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final UserLoginCredentialService service = new UserLoginCredentialService(
            users,
            companies,
            assignments,
            loginFailures,
            totpCredentials,
            recoveryCodes,
            auditRecords,
            sessions,
            events,
            Clock.fixed(NOW, ZoneOffset.UTC),
            transactions);

    private final Company operatorCompany =
            Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
    private final AuthenticatedActor admin = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
    private final User sales = user(new UserId(UUID.randomUUID()), Set.of(Role.SALES));

    private User user(UserId id, Set<Role> roles) {
        return User.of(
                id,
                operatorCompany.id(),
                EmailAddress.of("sales@example.com"),
                "営業 一郎",
                "{bcrypt}hash",
                UserStatus.ACTIVE,
                roles,
                3L);
    }

    private void given(User user) {
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(companies.findById(operatorCompany.id())).thenReturn(Optional.of(operatorCompany));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(operatorCompany.id())));
    }

    private void locked(User user, boolean locked) {
        when(loginFailures.findByUserId(user.id()))
                .thenReturn(LoginFailure.reconstitute(
                        user.id(), 5, new UtcInstant(NOW.plus(Duration.ofMinutes(locked ? 5 : -5)))));
        when(loginFailures.unlock(user.id(), new UtcInstant(NOW))).thenReturn(locked);
    }

    private void enrolled(User user, UtcInstant enrolledAt) {
        when(totpCredentials.findByUserId(user.id()))
                .thenReturn(
                        Optional.of(TotpCredential.enroll(user.id(), new EncryptedSecret("ciphertext"), enrolledAt)));
        when(totpCredentials.remove(user.id(), enrolledAt)).thenReturn(true);
    }

    private AuditRecord audited() {
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        return audit.getValue();
    }

    private static <R> UserAdministrationRefusal<R> rejected(R rejection) {
        return new UserAdministrationRefusal.Rejected<>(rejection);
    }

    // ---- ロックの解除 ----

    @Test
    void ロック中の利用者のロックを解除して監査を書きsessionは消さない() {
        given(sales);
        locked(sales, true);

        assertThat(service.unlock(admin, sales.id())).isEqualTo(new UnlockOutcome.Unlocked());

        verify(loginFailures).unlock(sales.id(), new UtcInstant(NOW));
        AuditRecord audit = audited();
        assertThat(audit.action()).isEqualTo(AuditAction.ACCOUNT_UNLOCKED);
        assertThat(audit.actorUserId()).isEqualTo(admin.userId());
        assertThat(audit.reason()).isNull();
        assertThat(audit.target())
                .isEqualTo(new AuditTarget("USER", sales.id().value().toString(), "3", "LOCKED", "UNLOCKED"));
        assertThat(transactions.rollbackOnly).isFalse();
        verify(sessions, never()).revokeAll(any());
    }

    @Test
    void ロック中でなければ既に解除されているとして書かない() {
        given(sales);
        locked(sales, false);

        assertThat(service.unlock(admin, sales.id()))
                .isEqualTo(new UnlockOutcome.Refused(rejected(LoginCredentialRejection.NOT_LOCKED)));
        assertThat(service.checkUnlock(admin, sales.id()))
                .isEqualTo(new UnlockCheck.Refused(rejected(LoginCredentialRejection.NOT_LOCKED)));
        verify(auditRecords, never()).append(any());
        assertThat(transactions.rollbackOnly).isTrue();
    }

    @Test
    void ロック中の利用者は解除を受け付けられることを確かめるだけなら書かない() {
        given(sales);
        locked(sales, true);

        assertThat(service.checkUnlock(admin, sales.id())).isEqualTo(new UnlockCheck.Acceptable());
        verify(loginFailures, never()).unlock(any(), any());
        verify(auditRecords, never()).append(any());
    }

    // ---- TOTP の解除 ----

    @Test
    void 確認した登録の資格と回復コードを消して監査を書き確定の後にsessionを消しロックは触らない() {
        given(sales);
        enrolled(sales, ENROLLED_AT);

        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Reset());

        InOrder order = inOrder(totpCredentials, recoveryCodes, auditRecords, sessions);
        order.verify(totpCredentials).remove(sales.id(), ENROLLED_AT);
        order.verify(recoveryCodes).removeAll(sales.id());
        order.verify(auditRecords).append(any());
        order.verify(sessions).revokeAll(sales.email());
        AuditRecord audit = audited();
        assertThat(audit.action()).isEqualTo(AuditAction.TOTP_RESET_BY_ADMIN);
        assertThat(audit.reason()).isEqualTo("IDENTITY_VERIFIED");
        assertThat(audit.target())
                .isEqualTo(new AuditTarget(
                        "USER",
                        sales.id().value().toString(),
                        "3",
                        "ENROLLED",
                        "method=IN_PERSON;verifiedAt=2026-11-09T01:30:00Z"));
        assertThat(transactions.rollbackOnly).isFalse();
        verify(loginFailures, never()).unlock(any(), any());
        // Bolt 38d の行 1・14: 監査記録の ID と監査の時刻で DE-26 を出す（同じトランザクションの中）
        verify(events).publishEvent(new TotpResetByAdmin(audit.id(), sales.id(), new UtcInstant(NOW)));
    }

    @Test
    void 解除しなければ解除のイベントを出さない() {
        // Bolt 38d の行 4a（拒否・未登録・登録が変わった・0 件・日時の誤り。変異 V4）
        User auditor = user(new UserId(UUID.randomUUID()), Set.of(Role.AUDITOR));
        given(auditor);
        enrolled(auditor, ENROLLED_AT);
        given(sales);
        when(totpCredentials.findByUserId(sales.id()))
                .thenReturn(Optional.of(TotpCredential.enroll(sales.id(), new EncryptedSecret("c"), ENROLLED_AT)));
        when(totpCredentials.remove(sales.id(), ENROLLED_AT)).thenReturn(false);
        UtcInstant renewed = new UtcInstant(NOW.minusSeconds(30));
        UtcInstant future = new UtcInstant(NOW.plusSeconds(60));

        assertThat(service.resetTotp(
                        admin, auditor.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isInstanceOf(TotpResetOutcome.Refused.class);
        assertThat(service.resetTotp(admin, sales.id(), renewed, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.ENROLLMENT_CHANGED)));
        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, future))
                .isEqualTo(new TotpResetOutcome.Unverifiable(Violation.FUTURE));
        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isInstanceOf(TotpResetOutcome.Refused.class);
        when(totpCredentials.findByUserId(sales.id())).thenReturn(Optional.empty());
        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.NOT_ENROLLED)));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void 資格がなければ既に解除されているとして書かない() {
        given(sales);
        when(totpCredentials.findByUserId(sales.id())).thenReturn(Optional.empty());

        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.NOT_ENROLLED)));
        assertThat(service.checkTotpReset(admin, sales.id()))
                .isEqualTo(new TotpResetCheck.Refused(rejected(LoginCredentialRejection.NOT_ENROLLED)));
        verify(recoveryCodes, never()).removeAll(any());
        verify(auditRecords, never()).append(any());
        verify(sessions, never()).revokeAll(any());
        assertThat(transactions.rollbackOnly).isTrue();
    }

    @Test
    void 確認の後に登録し直していれば登録が変わったとして新しい登録を消さない() {
        given(sales);
        UtcInstant renewed = new UtcInstant(NOW.minusSeconds(10));
        enrolled(sales, renewed);

        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.ENROLLMENT_CHANGED)));
        verify(totpCredentials, never()).remove(sales.id(), renewed);
        verify(recoveryCodes, never()).removeAll(any());
        verify(auditRecords, never()).append(any());
        verify(sessions, never()).revokeAll(any());
    }

    @Test
    void 読んだ後に消す前に資格が変わり削除が0件なら書かずに読み直して理由を返す() {
        given(sales);
        when(totpCredentials.findByUserId(sales.id()))
                .thenReturn(Optional.of(TotpCredential.enroll(sales.id(), new EncryptedSecret("c"), ENROLLED_AT)))
                .thenReturn(Optional.empty());
        when(totpCredentials.remove(sales.id(), ENROLLED_AT)).thenReturn(false);

        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.NOT_ENROLLED)));
        verify(recoveryCodes, never()).removeAll(any());
        verify(auditRecords, never()).append(any());
        assertThat(transactions.rollbackOnly).isTrue();
    }

    @Test
    void 登録済みなら解除を受け付けられることと登録の時刻を返し書かない() {
        given(sales);
        enrolled(sales, ENROLLED_AT);

        assertThat(service.checkTotpReset(admin, sales.id())).isEqualTo(new TotpResetCheck.Acceptable(ENROLLED_AT));
        verify(totpCredentials, never()).remove(any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 本人確認の日時が未来か7日より前なら書かずに理由を返す() {
        given(sales);
        enrolled(sales, ENROLLED_AT);
        UtcInstant future = new UtcInstant(NOW.plusSeconds(60));
        UtcInstant old = new UtcInstant(NOW.minus(Duration.ofDays(8)));

        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, future))
                .isEqualTo(new TotpResetOutcome.Unverifiable(Violation.FUTURE));
        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, old))
                .isEqualTo(new TotpResetOutcome.Unverifiable(Violation.TOO_OLD));
        assertThat(service.verificationViolation(future)).contains(Violation.FUTURE);
        assertThat(service.verificationViolation(VERIFIED_AT)).isEmpty();
        verify(totpCredentials, never()).remove(any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 規則の拒否と登録の変化は本人確認の日時の誤りより先に返す() {
        // 確定の順は判定 → 登録の確かめ → 日時の規則（拒否は入力の誤りより先に示す。Bolt 38c の開発レビュー M-3）
        User auditor = user(new UserId(UUID.randomUUID()), Set.of(Role.AUDITOR));
        given(auditor);
        enrolled(auditor, ENROLLED_AT);
        given(sales);
        enrolled(sales, ENROLLED_AT);
        UtcInstant future = new UtcInstant(NOW.plusSeconds(60));
        UtcInstant renewed = new UtcInstant(NOW.minusSeconds(30));

        assertThat(service.resetTotp(admin, auditor.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, future))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.OVERSIGHT_ROLE)));
        assertThat(service.resetTotp(admin, sales.id(), renewed, IdentityVerificationMethod.IN_PERSON, future))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.ENROLLMENT_CHANGED)));
        verify(totpCredentials, never()).remove(any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void sessionを消せなくても解除した結果を返す() {
        given(sales);
        enrolled(sales, ENROLLED_AT);
        doThrow(new IllegalStateException("session の表に届かない")).when(sessions).revokeAll(any());

        assertThat(service.resetTotp(admin, sales.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Reset());
    }

    @Test
    void 監査を書けなければ例外になりsessionは消さない() {
        given(sales);
        enrolled(sales, ENROLLED_AT);
        doThrow(new IllegalStateException("監査の表に届かない")).when(auditRecords).append(any());
        UserId target = sales.id();

        assertThatThrownBy(() -> service.resetTotp(
                        admin, target, ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).revokeAll(any());
    }

    // ---- 共通の判定（行 9・管理範囲） ----

    @Test
    void 自分と監督の役割の利用者は状態によらず拒み書かない() {
        User self = user(admin.userId(), Set.of(Role.SALES));
        User auditor = user(new UserId(UUID.randomUUID()), Set.of(Role.AUDITOR));
        given(self);
        given(auditor);
        locked(auditor, true);
        enrolled(auditor, ENROLLED_AT);

        assertThat(service.unlock(admin, self.id()))
                .isEqualTo(new UnlockOutcome.Refused(rejected(LoginCredentialRejection.SELF)));
        assertThat(service.unlock(admin, auditor.id()))
                .isEqualTo(new UnlockOutcome.Refused(rejected(LoginCredentialRejection.OVERSIGHT_ROLE)));
        assertThat(service.resetTotp(
                        admin, auditor.id(), ENROLLED_AT, IdentityVerificationMethod.IN_PERSON, VERIFIED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(rejected(LoginCredentialRejection.OVERSIGHT_ROLE)));
        assertThat(service.checkTotpReset(admin, self.id()))
                .isEqualTo(new TotpResetCheck.Refused(rejected(LoginCredentialRejection.SELF)));
        verify(loginFailures, never()).unlock(any(), any());
        verify(totpCredentials, never()).remove(any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 管理範囲外とシステム管理者でない操作者は拒み書かない() {
        given(sales);
        locked(sales, true);
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of()));
        AuthenticatedActor salesActor = new AuthenticatedActor(
                new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SALES), "営業 二郎", "A 社");

        assertThat(service.unlock(admin, sales.id()))
                .isEqualTo(new UnlockOutcome.Refused(new UserAdministrationRefusal.OutOfScope<>()));
        assertThat(service.checkTotpReset(salesActor, sales.id()))
                .isEqualTo(new TotpResetCheck.Refused(new UserAdministrationRefusal.NotPermitted<>()));
        verify(loginFailures, never()).unlock(any(), any());
        verify(auditRecords, never()).append(any());
    }

    private static final class RecordingTransactions extends TransactionTemplate {
        private boolean rollbackOnly;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            TransactionStatus status = new SimpleTransactionStatus();
            T result = action.doInTransaction(status);
            rollbackOnly = status.isRollbackOnly();
            return result;
        }
    }
}

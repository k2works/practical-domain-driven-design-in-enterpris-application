package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserSessionRevoker;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.StatusChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
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
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 利用状態の変更のサービス（Bolt 34d。US-16 AC5、UC-16 の 5a・4b）。 */
class UserStatusChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T05:00:00Z");

    private static final Optional<SuspensionReason> TRANSFERRED = Optional.of(SuspensionReason.TRANSFERRED);

    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
    private final UserAssignmentRepository assignments = mock(UserAssignmentRepository.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final UserSessionRevoker sessions = mock(UserSessionRevoker.class);
    private final UserStatusChangeService service = new UserStatusChangeService(
            users,
            companies,
            assignments,
            auditRecords,
            tokens,
            sessions,
            Clock.fixed(NOW, ZoneOffset.UTC),
            transactions);

    private final Company operatorCompany =
            Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
    private final AuthenticatedActor admin = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
    private final User sales = sales(UserStatus.ACTIVE);

    private User sales(UserStatus status) {
        return User.of(
                new UserId(UUID.fromString("00000000-0000-0000-0000-00000000aa01")),
                operatorCompany.id(),
                EmailAddress.of("sales@example.com"),
                "営業 一郎",
                "{bcrypt}hash",
                status,
                Set.of(Role.SALES),
                3L);
    }

    private void given(User user) {
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(companies.findById(operatorCompany.id())).thenReturn(Optional.of(operatorCompany));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(operatorCompany.id())));
        when(users.changeStatus(any(), anyLong(), any())).thenReturn(true);
    }

    @Test
    void 利用を停止して版と監査を書く() {
        given(sales);

        assertThat(service.change(admin, sales.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.Changed(4L));

        ArgumentCaptor<User> changed = ArgumentCaptor.forClass(User.class);
        verify(users).changeStatus(changed.capture(), eq(3L), eq(new UtcInstant(NOW)));
        assertThat(changed.getValue().status()).isEqualTo(UserStatus.SUSPENDED);
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_SUSPENDED);
        assertThat(audit.getValue().reason()).isEqualTo("TRANSFERRED");
        assertThat(audit.getValue().actorUserId()).isEqualTo(admin.userId());
        assertThat(audit.getValue().target())
                .isEqualTo(new AuditTarget("USER", sales.id().value().toString(), "4", "ACTIVE", "SUSPENDED"));
        assertThat(transactions.rollbackOnly).isFalse();
    }

    @Test
    void 停止中の利用者を再開して監査を書く() {
        User suspended = sales(UserStatus.SUSPENDED);
        given(suspended);

        assertThat(service.change(admin, suspended.id(), 3L, UserStatus.ACTIVE, Optional.empty()))
                .isEqualTo(new StatusChangeResult.Changed(4L));

        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.USER_REACTIVATED);
        assertThat(audit.getValue().reason()).isNull();
    }

    @Test
    void 拒否は書かずに理由を返す() {
        given(sales);

        assertThat(service.change(admin, sales.id(), 3L, UserStatus.SUSPENDED, Optional.empty()))
                .isEqualTo(new StatusChangeResult.Rejected(StatusChangeRejection.REASON_REQUIRED));
        verify(users, never()).changeStatus(any(), anyLong(), any());
        verify(auditRecords, never()).append(any());
        assertThat(transactions.rollbackOnly).isTrue();
    }

    @Test
    void 読んだ版が期待と違えば古い版として書かない() {
        given(sales);

        assertThat(service.change(admin, sales.id(), 2L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.Stale());
        verify(users, never()).changeStatus(any(), anyLong(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 版を条件にした更新が0件なら古い版としてロールバックする() {
        given(sales);
        when(users.changeStatus(any(), anyLong(), any())).thenReturn(false);

        assertThat(service.change(admin, sales.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.Stale());
        verify(auditRecords, never()).append(any());
        assertThat(transactions.rollbackOnly).isTrue();
    }

    @Test
    void 管理範囲外の利用者は範囲外として書かない() {
        given(sales);
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of()));

        assertThat(service.change(admin, sales.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.OutOfScope());
        verify(users, never()).changeStatus(any(), anyLong(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 管理範囲を規則より先に判定する() {
        given(sales);
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of()));

        assertThat(service.check(admin, sales.id(), 3L, UserStatus.ACTIVE, Optional.empty()))
                .as("範囲外なら同じ状態の拒否より先に範囲外と答える（範囲外の利用者の状態を漏らさない）")
                .isEqualTo(new StatusChangeResult.OutOfScope());
    }

    @Test
    void 規則を版より先に判定する() {
        given(sales);

        assertThat(service.check(admin, sales.id(), 2L, UserStatus.ACTIVE, Optional.empty()))
                .isEqualTo(new StatusChangeResult.Rejected(StatusChangeRejection.NO_CHANGE));
    }

    @Test
    void 利用者がいなければ見つからない() {
        when(users.findById(any())).thenReturn(Optional.empty());

        assertThat(service.change(admin, new UserId(UUID.randomUUID()), 0L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.NotFound());
    }

    @Test
    void 所属企業がなければ見つからない() {
        when(users.findById(sales.id())).thenReturn(Optional.of(sales));
        when(companies.findById(any())).thenReturn(Optional.empty());

        assertThat(service.check(admin, sales.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.NotFound());
    }

    @Test
    void システム管理者でない操作者は変えられない() {
        AuthenticatedActor salesActor = new AuthenticatedActor(
                new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SALES), "営業 二郎", "A 社");

        assertThat(service.change(salesActor, sales.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.NotPermitted());
        verify(users, never()).findById(any());
    }

    @Test
    void 監査を書けなければ例外になる() {
        given(sales);
        doThrow(new IllegalStateException("監査の表に届かない")).when(auditRecords).append(any());
        UserId target = sales.id();

        assertThatThrownBy(() -> service.change(admin, target, 3L, UserStatus.SUSPENDED, TRANSFERRED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 確かめるだけなら受け付けられることを返し書かない() {
        given(sales);

        assertThat(service.check(admin, sales.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED))
                .isEqualTo(new StatusChangeResult.Acceptable());
        verify(users, never()).changeStatus(any(), anyLong(), any());
        verify(auditRecords, never()).append(any());
    }

    // 開発レビューの後の決定（2026-10-17、human:kakimomokuri）

    @Test
    void 停止では同じトランザクションで再設定のトークンを無効にしsessionはすぐに消さない() {
        given(sales);

        service.change(admin, sales.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED);

        verify(tokens).invalidateUnused(sales.id(), new UtcInstant(NOW));
        verify(sessions, never()).revokeAll(any());
    }

    @Test
    void 再開では確定の後に前のsessionをすべて消しトークンには触れない() {
        User suspended = sales(UserStatus.SUSPENDED);
        given(suspended);

        service.change(admin, suspended.id(), 3L, UserStatus.ACTIVE, Optional.empty());

        verify(sessions).revokeAll(suspended.email());
        verify(tokens, never()).invalidateUnused(any(), any());
    }

    @Test
    void 拒否と古い版ではトークンもsessionも触れない() {
        User suspended = sales(UserStatus.SUSPENDED);
        given(suspended);

        service.change(admin, suspended.id(), 2L, UserStatus.ACTIVE, Optional.empty());
        service.change(admin, suspended.id(), 3L, UserStatus.SUSPENDED, TRANSFERRED);

        verify(sessions, never()).revokeAll(any());
        verify(tokens, never()).invalidateUnused(any(), any());
    }

    @Test
    void 再開の後にsessionを消せなくても再開した結果を返す() {
        User suspended = sales(UserStatus.SUSPENDED);
        given(suspended);
        when(sessions.revokeAll(any())).thenThrow(new IllegalStateException("session の表に届かない"));

        assertThat(service.change(admin, suspended.id(), 3L, UserStatus.ACTIVE, Optional.empty()))
                .isEqualTo(new StatusChangeResult.Changed(4L));
    }

    /** トランザクションを開かずにそのまま呼び、setRollbackOnly を覚える（単体テスト用）。 */
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

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
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
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

/** 役割の変更のサービス（Bolt 34。US-16 AC1 の役割の分・AC3、UC-16 の 4b）。 */
class UserRoleChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
    private final UserAssignmentRepository assignments = mock(UserAssignmentRepository.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final UserRoleChangeService service = new UserRoleChangeService(
            users, companies, assignments, auditRecords, Clock.fixed(NOW, ZoneOffset.UTC), transactions);

    private final Company operatorCompany =
            Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
    private final AuthenticatedActor admin = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
    private final User sales = User.of(
            new UserId(UUID.randomUUID()),
            operatorCompany.id(),
            EmailAddress.of("sales@example.com"),
            "営業 一郎",
            "{bcrypt}hash",
            UserStatus.ACTIVE,
            Set.of(Role.SALES),
            3L);

    private void givenSales() {
        when(users.findById(sales.id())).thenReturn(Optional.of(sales));
        when(companies.findById(operatorCompany.id())).thenReturn(Optional.of(operatorCompany));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(operatorCompany.id())));
        when(users.changeRoles(any(), anyLong(), any(), any())).thenReturn(true);
    }

    @Test
    void 役割を変えて版と監査を書く() {
        givenSales();

        assertThat(service.change(admin, sales.id(), 3L, Set.of(Role.SALES, Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.Changed(4L));

        ArgumentCaptor<User> changed = ArgumentCaptor.forClass(User.class);
        verify(users).changeRoles(changed.capture(), eq(3L), eq(admin.userId()), eq(new UtcInstant(NOW)));
        assertThat(changed.getValue().roles()).containsExactlyInAnyOrder(Role.SALES, Role.ROUTE_DESIGNER);
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.ROLES_CHANGED);
        assertThat(audit.getValue().actorUserId()).isEqualTo(admin.userId());
        assertThat(audit.getValue().target())
                .isEqualTo(
                        new AuditTarget("USER", sales.id().value().toString(), "4", "SALES", "SALES,ROUTE_DESIGNER"));
        assertThat(transactions.rollbackOnly).isFalse();
    }

    @Test
    void 拒否は書かずに理由を返す() {
        givenSales();

        assertThat(service.change(admin, sales.id(), 3L, Set.of(Role.SALES, Role.SYSTEM_ADMIN)))
                .isEqualTo(new RoleChangeResult.Rejected(
                        new RoleChangeRejection(RoleChangeRejectionReason.OVERSIGHT_ROLE, Set.of(Role.SYSTEM_ADMIN))));
        verify(users, never()).changeRoles(any(), anyLong(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 読んだ版が期待と違えば古い版として書かない() {
        givenSales();

        assertThat(service.change(admin, sales.id(), 2L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.Stale());
        verify(users, never()).changeRoles(any(), anyLong(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 版を条件にした更新が0件なら古い版としてロールバックする() {
        givenSales();
        when(users.changeRoles(any(), anyLong(), any(), any())).thenReturn(false);

        assertThat(service.change(admin, sales.id(), 3L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.Stale());
        verify(auditRecords, never()).append(any());
        assertThat(transactions.rollbackOnly).isTrue();
    }

    @Test
    void 管理範囲外の利用者は範囲外として書かない() {
        givenSales();
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of()));

        assertThat(service.change(admin, sales.id(), 3L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.OutOfScope());
        verify(users, never()).changeRoles(any(), anyLong(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 確かめるときも管理範囲外の利用者は範囲外() {
        givenSales();
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of()));

        assertThat(service.check(admin, sales.id(), 3L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.OutOfScope());
    }

    @Test
    void 管理範囲を規則より先に判定する() {
        givenSales();
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of()));

        assertThat(service.check(admin, sales.id(), 3L, Set.of()))
                .as("範囲外なら役割なしの拒否より先に範囲外と答える（範囲外の利用者の様子を漏らさない）")
                .isEqualTo(new RoleChangeResult.OutOfScope());
    }

    @Test
    void 利用者がいなければ見つからない() {
        when(users.findById(any())).thenReturn(Optional.empty());

        assertThat(service.change(admin, new UserId(UUID.randomUUID()), 0L, Set.of(Role.SALES)))
                .isEqualTo(new RoleChangeResult.NotFound());
    }

    @Test
    void システム管理者でない操作者は変えられない() {
        AuthenticatedActor salesActor = new AuthenticatedActor(
                new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SALES), "営業 二郎", "A 社");

        assertThat(service.change(salesActor, sales.id(), 3L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.NotPermitted());
        verify(users, never()).findById(any());
    }

    @Test
    void 監査を書けなければ例外になる() {
        givenSales();
        doThrow(new IllegalStateException("監査の表に届かない")).when(auditRecords).append(any());
        UserId target = sales.id();
        Set<Role> roles = Set.of(Role.ROUTE_DESIGNER);

        assertThatThrownBy(() -> service.change(admin, target, 3L, roles)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 確かめるだけなら受け付けられることを返し書かない() {
        givenSales();

        assertThat(service.check(admin, sales.id(), 3L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.Acceptable());
        verify(users, never()).changeRoles(any(), anyLong(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 確かめるときも同じ判定で拒む() {
        givenSales();

        assertThat(service.check(admin, sales.id(), 3L, Set.of(Role.SHIPPER)))
                .isEqualTo(new RoleChangeResult.Rejected(
                        new RoleChangeRejection(RoleChangeRejectionReason.UNFIT_FOR_COMPANY, Set.of(Role.SHIPPER))));
        assertThat(service.check(admin, sales.id(), 2L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeResult.Stale());
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

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
import com.example.cargotracker.identity.domain.model.valueobjects.AssignedCompanies;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignmentChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditTarget;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 担当範囲の変更のサービス（Bolt 38e。US-16 AC1 の担当の関係と変更履歴、IA-INV-02・IA-INV-14）。 */
class UserAssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:30:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
    private final UserAssignmentRepository assignments = mock(UserAssignmentRepository.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final UserAssignmentService service = new UserAssignmentService(
            users, companies, assignments, auditRecords, Clock.fixed(NOW, ZoneOffset.UTC), transactions);

    private final Company operatorCompany = Company.of(
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000003")), "A 社", CompanyKind.OPERATOR, true);
    private final Company shipperA = Company.of(
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001")), "荷主 A", CompanyKind.SHIPPER, true);
    private final Company shipperB = Company.of(
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002")), "荷主 B", CompanyKind.SHIPPER, true);
    private final Company consignee =
            Company.of(new CompanyId(UUID.randomUUID()), "荷受人 C", CompanyKind.CONSIGNEE, true);
    private final Company inactiveShipper =
            Company.of(new CompanyId(UUID.randomUUID()), "荷主 D（無効）", CompanyKind.SHIPPER, false);
    private final CompanyId outsideCompany = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
    private final AuthenticatedActor admin = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
    private final User sales = userWith(Set.of(Role.SALES));

    private User userWith(Set<Role> roles) {
        return User.of(
                new UserId(UUID.randomUUID()),
                operatorCompany.id(),
                EmailAddress.of("sales-" + UUID.randomUUID() + "@example.com"),
                "営業 一郎",
                "{bcrypt}hash",
                UserStatus.ACTIVE,
                roles,
                3L);
    }

    private AdministrationScope scope() {
        return new AdministrationScope(
                Set.of(operatorCompany.id(), shipperA.id(), shipperB.id(), consignee.id(), inactiveShipper.id()));
    }

    private void given(User user, AssignedCompanies assigned) {
        when(users.findById(user.id())).thenReturn(Optional.of(user));
        when(companies.findById(operatorCompany.id())).thenReturn(Optional.of(operatorCompany));
        when(assignments.scopeOf(admin.userId())).thenReturn(scope());
        when(companies.findAllIn(scope().companies()))
                .thenReturn(List.of(operatorCompany, shipperA, shipperB, consignee, inactiveShipper));
        when(assignments.assignedCompaniesOf(user.id())).thenReturn(assigned);
        when(assignments.replaceCompanies(any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    void 範囲の中の荷主を追加と解除して版を上げ監査に範囲の外を含む全体を書く() {
        // 行 3（AC-2）
        given(sales, new AssignedCompanies(Set.of(shipperA.id(), outsideCompany)));

        assertThat(service.change(admin, sales.id(), 3L, Set.of(shipperB.id())))
                .isEqualTo(new AssignmentChangeOutcome.Changed(4L));

        ArgumentCaptor<AdministrationScope> changeable = ArgumentCaptor.forClass(AdministrationScope.class);
        verify(assignments)
                .replaceCompanies(
                        eq(sales.id()),
                        eq(3L),
                        changeable.capture(),
                        eq(new AssignedCompanies(Set.of(shipperB.id()))),
                        eq(admin.userId()),
                        eq(new UtcInstant(NOW)));
        // 置き換えてよいのは選べる企業（範囲の中の有効な荷主）だけ
        assertThat(changeable.getValue().companies()).containsExactlyInAnyOrder(shipperA.id(), shipperB.id());
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecords).append(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.ASSIGNMENTS_CHANGED);
        assertThat(audit.getValue().actorUserId()).isEqualTo(admin.userId());
        assertThat(audit.getValue().target())
                .isEqualTo(new AuditTarget(
                        "USER",
                        sales.id().value().toString(),
                        "4",
                        shipperA.id().value() + "," + outsideCompany.value(),
                        shipperB.id().value() + "," + outsideCompany.value()));
        assertThat(transactions.rollbackOnly).isFalse();
    }

    @Test
    void 確かめは受け付けられれば何も書かない() {
        given(sales, AssignedCompanies.none());

        assertThat(service.check(admin, sales.id(), 3L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeCheck.Acceptable());
        verify(assignments, never()).replaceCompanies(any(), anyLong(), any(), any(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void すべて解除する変更も受け付ける() {
        given(sales, new AssignedCompanies(Set.of(shipperA.id())));

        assertThat(service.change(admin, sales.id(), 3L, Set.of())).isEqualTo(new AssignmentChangeOutcome.Changed(4L));
    }

    @Test
    void 範囲の外の企業は選べず何も書かない() {
        // 行 5（AC-3、V1）
        given(sales, AssignedCompanies.none());

        assertThat(service.change(admin, sales.id(), 3L, Set.of(shipperA.id(), outsideCompany)))
                .isEqualTo(rejected(AssignmentChangeRejection.UNSELECTABLE_COMPANY));
        verify(assignments, never()).replaceCompanies(any(), anyLong(), any(), any(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 運用会社と荷受人と無効な荷主は範囲の中でも選べない() {
        // 行 5 の同値クラス（決定 (11)）
        given(sales, AssignedCompanies.none());

        for (Company unselectable : List.of(operatorCompany, consignee, inactiveShipper)) {
            assertThat(service.check(admin, sales.id(), 3L, Set.of(unselectable.id())))
                    .as(unselectable.name())
                    .isEqualTo(new AssignmentChangeCheck.Refused(
                            new UserAdministrationRefusal.Rejected<>(AssignmentChangeRejection.UNSELECTABLE_COMPANY)));
        }
    }

    @Test
    void 選べる企業の中で今と同じなら変更がないとして書かない() {
        // 行 7（V6）。範囲の外の担当企業は比べない
        given(sales, new AssignedCompanies(Set.of(shipperA.id(), outsideCompany)));

        assertThat(service.change(admin, sales.id(), 3L, Set.of(shipperA.id())))
                .isEqualTo(rejected(AssignmentChangeRejection.NO_CHANGE));
        verify(assignments, never()).replaceCompanies(any(), anyLong(), any(), any(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @ParameterizedTest(name = "{0} だけの利用者には担当範囲がない")
    @EnumSource(
            value = Role.class,
            names = {"DATA_STEWARD", "SYSTEM_ADMIN", "AUDITOR"})
    void 担当範囲のない役割の利用者は拒み書かない(Role unscoped) {
        // 行 8（V14）。画面からは来ない（運用の手順で役割が変わった場合も同じ）
        User user = userWith(Set.of(unscoped));
        given(user, AssignedCompanies.none());

        assertThat(service.change(admin, user.id(), 3L, Set.of(shipperA.id())))
                .isEqualTo(rejected(AssignmentChangeRejection.NOT_ASSIGNMENT_SCOPED));
        verify(assignments, never()).replaceCompanies(any(), anyLong(), any(), any(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 担当範囲のない役割の拒否は選べない企業より先に返す() {
        User admin2 = userWith(Set.of(Role.SYSTEM_ADMIN));
        given(admin2, AssignedCompanies.none());

        assertThat(service.check(admin, admin2.id(), 3L, Set.of(outsideCompany)))
                .isEqualTo(new AssignmentChangeCheck.Refused(
                        new UserAdministrationRefusal.Rejected<>(AssignmentChangeRejection.NOT_ASSIGNMENT_SCOPED)));
    }

    @Test
    void 読んだ版が期待と違えば古い版として書かない() {
        // 行 9
        given(sales, AssignedCompanies.none());

        assertThat(service.change(admin, sales.id(), 2L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));
        verify(assignments, never()).replaceCompanies(any(), anyLong(), any(), any(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 確かめでも読んだ版が期待と違えば古い版として何も書かない() {
        // 行 9（V4）。確認の領域を開く時の判定。確定ではリポジトリの版の条件も止めるので、版の確かめが効くのはここ
        given(sales, AssignedCompanies.none());

        assertThat(service.check(admin, sales.id(), 2L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeCheck.Refused(new UserAdministrationRefusal.Stale<>()));
        verify(assignments, never()).replaceCompanies(any(), anyLong(), any(), any(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void 版を条件にした置き換えが0件なら古い版としてロールバックする() {
        given(sales, AssignedCompanies.none());
        when(assignments.replaceCompanies(any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(false);

        assertThat(service.change(admin, sales.id(), 3L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));
        verify(auditRecords, never()).append(any());
        assertThat(transactions.rollbackOnly).isTrue();
    }

    @Test
    void 管理範囲外の利用者は範囲外として書かない() {
        given(sales, AssignedCompanies.none());
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(shipperA.id())));

        assertThat(service.change(admin, sales.id(), 3L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.OutOfScope<>()));
        verify(assignments, never()).replaceCompanies(any(), anyLong(), any(), any(), any(), any());
        verify(auditRecords, never()).append(any());
    }

    @Test
    void システム管理者でない操作者は権限なしとして書かない() {
        given(sales, AssignedCompanies.none());
        AuthenticatedActor support = new AuthenticatedActor(
                new UserId(UUID.randomUUID()), operatorCompany.id(), Set.of(Role.CUSTOMER_SUPPORT), "支援 花子", "A 社");

        assertThat(service.check(support, sales.id(), 3L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeCheck.Refused(new UserAdministrationRefusal.NotPermitted<>()));
    }

    @Test
    void 監査を書けなければ例外で全体を戻す() {
        given(sales, AssignedCompanies.none());
        doThrow(new IllegalStateException("監査の表に届かない")).when(auditRecords).append(any());
        UserId target = sales.id();
        Set<CompanyId> selected = Set.of(shipperA.id());

        assertThatThrownBy(() -> service.change(admin, target, 3L, selected)).isInstanceOf(IllegalStateException.class);
    }

    private static AssignmentChangeOutcome rejected(AssignmentChangeRejection rejection) {
        return new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.Rejected<>(rejection));
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

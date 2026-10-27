package com.example.cargotracker.identity.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RegistrableCompany;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionRecord;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 利用者の管理の照会（Bolt 34b。管理範囲で絞る。US-16 AC2、IA-INV-14）。 */
class UserAdministrationQueryServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserAssignmentRepository assignments = mock(UserAssignmentRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AuditRecordRepository auditRecords = mock(AuditRecordRepository.class);
    private static final Instant NOW = Instant.parse("2026-11-09T01:30:00Z");
    private final UserAdministrationQueryService service = new UserAdministrationQueryService(
            users, assignments, companies, auditRecords, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CompanyId inside = new CompanyId(UUID.randomUUID());
    private final CompanyId outside = new CompanyId(UUID.randomUUID());
    private final AuthenticatedActor admin =
            new AuthenticatedActor(new UserId(UUID.randomUUID()), inside, Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
    private final CompanyId shipper = new CompanyId(UUID.randomUUID());
    private final CompanyId consignee = new CompanyId(UUID.randomUUID());
    private final CompanyId inactive = new CompanyId(UUID.randomUUID());
    private final Company operatorCompany = Company.of(inside, "A 社", CompanyKind.OPERATOR, true);
    private final Company shipperCompany = Company.of(shipper, "荷主 B", CompanyKind.SHIPPER, true);
    private final Company consigneeCompany = Company.of(consignee, "荷受人 C", CompanyKind.CONSIGNEE, true);
    private final Company inactiveCompany = Company.of(inactive, "D 社", CompanyKind.OPERATOR, false);
    private final Set<CompanyId> scope = Set.of(inside, shipper, consignee, inactive);
    private final AuthenticatedActor sales =
            new AuthenticatedActor(new UserId(UUID.randomUUID()), inside, Set.of(Role.SALES), "営業 一郎", "A 社");

    private UserDetail detailIn(CompanyId company) {
        return new UserDetail(
                new UserId(UUID.randomUUID()),
                company,
                "A 社",
                CompanyKind.OPERATOR,
                "営業 一郎",
                EmailAddress.of("sales@example.com"),
                UserStatus.ACTIVE,
                Set.of(Role.SALES),
                0L,
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void 一覧は管理範囲の企業で絞る() {
        UserSummary summary = new UserSummary(
                new UserId(UUID.randomUUID()),
                "A 社",
                "営業 一郎",
                EmailAddress.of("sales@example.com"),
                UserStatus.ACTIVE,
                Set.of(Role.SALES));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));
        when(users.findSummariesIn(Set.of(inside))).thenReturn(List.of(summary));

        assertThat(service.list(admin)).containsExactly(summary);
    }

    @Test
    void システム管理者でなければ一覧は空で読まない() {

        assertThat(service.list(sales)).isEmpty();
        verify(users, never()).findSummariesIn(any());
    }

    @Test
    void 管理範囲の利用者の詳細は見つかる() {
        UserDetail detail = detailIn(inside);
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        assertThat(service.detail(admin, detail.id())).isEqualTo(new UserLookup.Found(detail));
    }

    @Test
    void 管理範囲外の利用者の詳細は範囲外() {
        UserDetail detail = detailIn(outside);
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        assertThat(service.detail(admin, detail.id())).isEqualTo(new UserLookup.OutOfScope());
    }

    @Test
    void いない利用者は見つからない() {
        UserId missing = new UserId(UUID.randomUUID());
        when(users.findDetail(missing)).thenReturn(Optional.empty());

        assertThat(service.detail(admin, missing)).isEqualTo(new UserLookup.NotFound());
    }

    @Test
    void システム管理者でなければ詳細は権限なしで読まない() {
        UserId target = new UserId(UUID.randomUUID());

        assertThat(service.detail(sales, target)).isEqualTo(new UserLookup.NotPermitted());
        verify(users, never()).findDetail(any());
    }

    private void givenScope() {
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(scope));
        for (Company company : List.of(operatorCompany, shipperCompany, consigneeCompany, inactiveCompany)) {
            when(companies.findById(company.id())).thenReturn(Optional.of(company));
        }
    }

    @Test
    void 登録できる企業は管理範囲の有効で付けられる役割がある企業だけをリポジトリの順で返す() {
        // Bolt 38b3 の行 4（T-106。準備に荷受人の企業と無効な企業があることを先に確かめる）
        givenScope();
        List<Company> inScope = List.of(operatorCompany, consigneeCompany, inactiveCompany, shipperCompany);
        when(companies.findAllIn(scope)).thenReturn(inScope);
        // 荷受人の企業には付けられる役割がなく、無効な企業は付けられる役割のある種類（無効を除かなければ出てしまう）
        assertThat(RoleAssignmentPolicy.assignableRoles(consigneeCompany.kind()))
                .isEmpty();
        assertThat(RoleAssignmentPolicy.assignableRoles(inactiveCompany.kind())).isNotEmpty();

        assertThat(service.registrableCompanies(admin))
                .containsExactly(
                        new RegistrableCompany(
                                inside,
                                "A 社",
                                CompanyKind.OPERATOR,
                                Set.of(
                                        Role.SALES,
                                        Role.ROUTE_DESIGNER,
                                        Role.TRACKING_MANAGER,
                                        Role.DATA_STEWARD,
                                        Role.CUSTOMER_SUPPORT)),
                        new RegistrableCompany(shipper, "荷主 B", CompanyKind.SHIPPER, Set.of(Role.SHIPPER)));
        verify(companies).findAllIn(scope);
    }

    @Test
    void システム管理者でなければ登録できる企業は空で管理範囲も企業も読まない() {
        // Bolt 38b3 の行 5
        assertThat(service.registrableCompanies(sales)).isEmpty();
        assertThat(service.registrableCompany(sales, inside)).isEqualTo(new RegistrableCompanyLookup.NotPermitted());
        verify(assignments, never()).scopeOf(any());
        verify(companies, never()).findAllIn(any());
        verify(companies, never()).findById(any());
    }

    @Test
    void 一社の照会は範囲の企業を見つけ無効と荷受人とない企業と範囲外を分ける() {
        // Bolt 38b3 の行 6
        givenScope();
        CompanyId missing = new CompanyId(UUID.randomUUID());
        Company other = Company.of(outside, "範囲外の会社", CompanyKind.OPERATOR, true);
        when(companies.findById(outside)).thenReturn(Optional.of(other));
        when(companies.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.registrableCompany(admin, shipper))
                .isEqualTo(new RegistrableCompanyLookup.Found(
                        new RegistrableCompany(shipper, "荷主 B", CompanyKind.SHIPPER, Set.of(Role.SHIPPER))));
        assertThat(service.registrableCompany(admin, inactive)).isEqualTo(new RegistrableCompanyLookup.Inactive());
        assertThat(service.registrableCompany(admin, consignee))
                .isEqualTo(new RegistrableCompanyLookup.NoAssignableRoles());
        assertThat(service.registrableCompany(admin, missing)).isEqualTo(new RegistrableCompanyLookup.NotFound());
        // ない企業でも管理範囲を先に読み、範囲外の企業と読み出しの量をそろえる（前例 detail）
        verify(assignments, org.mockito.Mockito.times(4)).scopeOf(admin.userId());
        assertThat(service.registrableCompany(admin, outside)).isEqualTo(new RegistrableCompanyLookup.OutOfScope());
    }

    @Test
    void 範囲外の無効な企業と範囲外の荷受人の企業は範囲外として返す() {
        // Bolt 38b3 の行 6b（範囲外の企業の状態を示さない）
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));
        when(companies.findById(inactive)).thenReturn(Optional.of(inactiveCompany));
        when(companies.findById(consignee)).thenReturn(Optional.of(consigneeCompany));

        assertThat(service.registrableCompany(admin, inactive)).isEqualTo(new RegistrableCompanyLookup.OutOfScope());
        assertThat(service.registrableCompany(admin, consignee)).isEqualTo(new RegistrableCompanyLookup.OutOfScope());
    }

    // 詳細のログインの資格（Bolt 38c。行 1）

    private UserDetail lockedUntil(Instant until) {
        UserDetail detail = detailIn(inside);
        return new UserDetail(
                detail.id(),
                detail.companyId(),
                detail.companyName(),
                detail.companyKind(),
                detail.displayName(),
                detail.email(),
                detail.status(),
                detail.roles(),
                detail.version(),
                Optional.of(new UtcInstant(until)),
                Optional.of(new UtcInstant(NOW.minus(Duration.ofDays(30)))));
    }

    @Test
    void 詳細のロックの期限は今より後ならそのまま示し登録の時刻も返す() {
        UserDetail detail = lockedUntil(NOW.plusSeconds(1));
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        assertThat(service.detail(admin, detail.id())).isEqualTo(new UserLookup.Found(detail));
    }

    @Test
    void 詳細のロックの期限がちょうど今か過ぎていればロックなしにする() {
        UserDetail detail = lockedUntil(NOW);
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        UserLookup found = service.detail(admin, detail.id());

        assertThat(found).isInstanceOfSatisfying(UserLookup.Found.class, each -> {
            assertThat(each.detail().lockedUntil()).isEmpty();
            assertThat(each.detail().totpEnrolledAt()).isEqualTo(detail.totpEnrolledAt());
        });
    }

    // 詳細の停止の記録（Bolt 38c2。US-16 AC5、#50）

    private UserDetail suspendedIn(CompanyId company, Optional<UtcInstant> lockedUntil) {
        UserDetail detail = detailIn(company);
        return new UserDetail(
                detail.id(),
                detail.companyId(),
                detail.companyName(),
                detail.companyKind(),
                detail.displayName(),
                detail.email(),
                UserStatus.SUSPENDED,
                detail.roles(),
                detail.version(),
                lockedUntil,
                Optional.empty());
    }

    private final SuspensionRecord suspension = new SuspensionRecord(
            SuspensionReason.TRANSFERRED,
            new UserId(UUID.randomUUID()),
            Optional.of("管理 次郎"),
            new UtcInstant(NOW.minus(Duration.ofHours(1))));

    @Test
    void 停止中の利用者の詳細には今の停止の記録を載せる() {
        // 行 1 の照会の側
        UserDetail detail = suspendedIn(inside, Optional.empty());
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));
        when(auditRecords.findLatestUserSuspension(detail.id())).thenReturn(Optional.of(suspension));

        assertThat(service.detail(admin, detail.id()))
                .isEqualTo(new UserLookup.Found(detail.withSuspension(Optional.of(suspension))));
    }

    @Test
    void 停止中で記録がなければ停止の記録は空() {
        // 行 2（DB を直接書き換えた停止。画面は「記録がありません」と示す）
        UserDetail detail = suspendedIn(inside, Optional.empty());
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));
        when(auditRecords.findLatestUserSuspension(detail.id())).thenReturn(Optional.empty());

        assertThat(service.detail(admin, detail.id()))
                .isInstanceOfSatisfying(
                        UserLookup.Found.class,
                        found -> assertThat(found.detail().suspension()).isEmpty());
        verify(auditRecords).findLatestUserSuspension(detail.id());
    }

    @Test
    void 利用中の利用者の詳細は監査記録を読まない() {
        // 行 3（再開した後を含む。V1）
        UserDetail detail = detailIn(inside);
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        assertThat(service.detail(admin, detail.id())).isEqualTo(new UserLookup.Found(detail));
        verify(auditRecords, never()).findLatestUserSuspension(any());
    }

    @Test
    void 停止中でロックの期限が切れても停止の記録は残る() {
        // 行 8b（asOf が作り直すので、記録は asOf の後に載せる）
        UserDetail detail = suspendedIn(inside, Optional.of(new UtcInstant(NOW)));
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));
        when(auditRecords.findLatestUserSuspension(detail.id())).thenReturn(Optional.of(suspension));

        assertThat(service.detail(admin, detail.id())).isInstanceOfSatisfying(UserLookup.Found.class, found -> {
            assertThat(found.detail().lockedUntil()).isEmpty();
            assertThat(found.detail().suspension()).contains(suspension);
        });
    }

    @Test
    void 管理範囲外の停止中の利用者は監査記録を読まない() {
        // 行 9（範囲の判定の後で読む。いない利用者と範囲外の利用者で読み出しの量をそろえる守りを保つ）
        UserDetail detail = suspendedIn(outside, Optional.empty());
        when(users.findDetail(detail.id())).thenReturn(Optional.of(detail));
        when(assignments.scopeOf(admin.userId())).thenReturn(new AdministrationScope(Set.of(inside)));

        assertThat(service.detail(admin, detail.id())).isEqualTo(new UserLookup.OutOfScope());
        verify(auditRecords, never()).findLatestUserSuspension(any());
    }
}

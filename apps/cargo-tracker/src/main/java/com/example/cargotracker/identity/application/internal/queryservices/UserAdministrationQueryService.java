package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.RegistrableCompany;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 利用者の管理の照会。S-20 の一覧と詳細を読む（Bolt 34。US-16 の一部）。`password_hash` を読まない射影で受ける。Bolt 34b から操作者を
 * 受け、管理範囲（`user_assignment` の `COMPANY`。IA-INV-14）の企業の利用者だけを返す。照会は書かない（範囲外の試行の記録は
 * 確定の POST でだけ S-20 の画面の層の詳細の部品 {@code UserDetailPage} が {@code AdministrationScopeDenialService} で書く）。Bolt 38c2
 * から、詳細で停止中の利用者の今の停止の記録を監査記録のリポジトリから読む（照会のサービスが監査記録を読む最初の例）。
 */
@Service // JIG がユースケースとして読む印（組み立ては IdentityConfiguration）
public class UserAdministrationQueryService {

    private final UserRepository users;
    private final UserAssignmentRepository assignments;
    private final CompanyRepository companies;
    private final AuditRecordRepository auditRecords;
    private final AssignableCompanyQuery assignable;
    private final Clock clock;

    public UserAdministrationQueryService(
            UserRepository users,
            UserAssignmentRepository assignments,
            CompanyRepository companies,
            AuditRecordRepository auditRecords,
            Clock clock) {
        this.users = users;
        this.assignments = assignments;
        this.companies = companies;
        this.auditRecords = auditRecords;
        this.assignable = new AssignableCompanyQuery(companies, assignments);
        this.clock = clock;
    }

    /**
     * 登録できる企業（管理範囲の、有効で、付けられる業務の役割がある企業）を、リポジトリの順（名前・ID）で返す（Bolt 38b3。S-20 の登録の
     * 企業の選択肢）。システム管理者でなければ権限なしを値で返す（Bolt 38e。#50 (b)。画面の層が 403 にする）。空の管理範囲はリポジトリが
     * 空を返す（前例 {@link #list}）。
     */
    public AdministrationListing<RegistrableCompany> registrableCompanies(AuthenticatedActor operator) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new AdministrationListing.NotPermitted<>();
        }
        return new AdministrationListing.Permitted<>(
                companies.findAllIn(assignments.scopeOf(operator.userId()).companies()).stream()
                        .filter(Company::active)
                        .map(UserAdministrationQueryService::registrable)
                        .flatMap(Optional::stream)
                        .toList());
    }

    /**
     * 登録できる企業を 1 社照会する（Bolt 38b3。S-20 の登録の 2 段目）。判定の順は登録のサービスと同じ（操作者 → 企業の有無 → 管理範囲 →
     * 有効性 → 付けられる役割）で、範囲外の企業は状態によらず範囲外として返す。管理範囲を先に読み、ない企業と範囲外の企業で読み出しの量を
     * そろえる（前例 {@link #detail}）。
     */
    public RegistrableCompanyLookup registrableCompany(AuthenticatedActor operator, CompanyId companyId) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new RegistrableCompanyLookup.NotPermitted();
        }
        AdministrationScope scope = assignments.scopeOf(operator.userId());
        Optional<Company> company = companies.findById(companyId);
        if (company.isEmpty()) {
            return new RegistrableCompanyLookup.NotFound();
        }
        if (!scope.covers(companyId)) {
            return new RegistrableCompanyLookup.OutOfScope();
        }
        if (!company.get().active()) {
            return new RegistrableCompanyLookup.Inactive();
        }
        return registrable(company.get())
                .<RegistrableCompanyLookup>map(RegistrableCompanyLookup.Found::new)
                .orElseGet(RegistrableCompanyLookup.NoAssignableRoles::new);
    }

    /** 付けられる役割がある企業なら、登録できる企業にする（荷受人の企業は空。IA-INV-13）。 */
    private static Optional<RegistrableCompany> registrable(Company company) {
        Set<Role> roles = RoleAssignmentPolicy.assignableRoles(company.kind());
        return roles.isEmpty()
                ? Optional.empty()
                : Optional.of(new RegistrableCompany(company.id(), company.name(), company.kind(), roles));
    }

    /**
     * 管理範囲の企業の利用者の一覧の行を、所属企業の名前・表示名の順に返す。システム管理者でなければ権限なしを値で返す（Bolt 38e。#50 (b)。
     * 空の一覧と詳細の権限なしで扱いがそろっていなかった。詳細の照会の {@link UserLookup.NotPermitted} と同じく、画面の層が 403（A-04）に
     * する。URL の規則（`/staff/users/**`）が先に拒むので、利用者には見えない守り）。
     */
    public AdministrationListing<UserSummary> list(AuthenticatedActor operator) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new AdministrationListing.NotPermitted<>();
        }
        return new AdministrationListing.Permitted<>(
                users.findSummariesIn(assignments.scopeOf(operator.userId()).companies()));
    }

    /**
     * 詳細を探す。管理範囲の外の利用者は範囲外として返す。管理範囲を先に読み、いない利用者と範囲外の利用者で読み出しの量をそろえる
     * （応答の時間から範囲外の利用者の存在を見分けにくくする。Bolt 34b の開発レビュー）。ロックの期限は今の時刻で判定し、切れていれば
     * 空にする（Bolt 38c。リポジトリは期限をそのまま返す）。停止中の利用者のときだけ、範囲の判定の後に今の停止の記録を監査記録から読み、
     * {@link UserDetail#asOf} の後に載せる（Bolt 38c2。asOf が詳細を作り直すので順を固定する。利用中なら読まない）。担当範囲のある役割の
     * 利用者のときだけ、担当企業を読んで載せる（Bolt 38e）。
     */
    public UserLookup detail(AuthenticatedActor operator, UserId id) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new UserLookup.NotPermitted();
        }
        AdministrationScope scope = assignments.scopeOf(operator.userId());
        Optional<UserDetail> found = users.findDetail(id);
        if (found.isEmpty()) {
            return new UserLookup.NotFound();
        }
        if (!scope.covers(found.get().companyId())) {
            return new UserLookup.OutOfScope();
        }
        UserDetail current = found.get().asOf(new UtcInstant(clock.instant()));
        UserDetail withSuspension = current.status() == UserStatus.SUSPENDED
                ? current.withSuspension(auditRecords.findLatestUserSuspension(id))
                : current;
        return new UserLookup.Found(
                RoleAssignmentPolicy.holdsAssignmentScopedRole(withSuspension.roles())
                        ? withSuspension.withAssignedCompanies(assignments.assignedCompaniesOf(id))
                        : withSuspension);
    }

    /**
     * 担当企業にできる企業（操作者の管理範囲の中の有効な荷主の企業）を、リポジトリの順（名前・ID）で返す（Bolt 38e。S-20 の担当範囲の
     * 領域の選択肢）。システム管理者でなければ権限なしを値で返す。選べる企業は担当範囲の変更のサービスと同じ口
     * （{@link AssignableCompanyQuery}）で読む。
     */
    public AdministrationListing<Company> assignableCompanies(AuthenticatedActor operator) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return new AdministrationListing.NotPermitted<>();
        }
        return new AdministrationListing.Permitted<>(assignable.of(operator.userId()));
    }
}

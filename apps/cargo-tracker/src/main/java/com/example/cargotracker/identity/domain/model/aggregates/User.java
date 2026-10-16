package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.rules.RoleAssignmentPolicy;
import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.RoleChangeRejectionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 利用者。事前登録され、固定役割を持つ人（BR-14、BR-15）。
 * password の照合は Spring Security に任せ、この集約は業務上の規則（利用停止・所属企業の有効性。IA-INV-09）だけを持つ。
 * 利用者の登録・利用停止・役割の付与は利用者の管理（US-16）で作る。Bolt 34 で役割の変更の判定と版（`app_user.version`）を足した。
 */
@AggregateRoot
public final class User {

    private final UserId id;
    private final CompanyId companyId;
    private final EmailAddress email;
    private final String displayName;
    private final String passwordHash;
    private final UserStatus status;
    private final Set<Role> roles;
    private final long version;

    private User(
            UserId id,
            CompanyId companyId,
            EmailAddress email,
            String displayName,
            String passwordHash,
            UserStatus status,
            Set<Role> roles,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.companyId = Objects.requireNonNull(companyId, "companyId");
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("利用者には役割が 1 つ以上要る（BR-15）");
        }
        this.roles = EnumSet.copyOf(roles);
        this.version = version;
    }

    /** 利用者を組み立てる（登録とリポジトリの復元が使う）。password はハッシュ（DelegatingPasswordEncoder の形式。SEC-07）で受け取る。 */
    public static User of(
            UserId id,
            CompanyId companyId,
            EmailAddress email,
            String displayName,
            String passwordHash,
            UserStatus status,
            Set<Role> roles) {
        return of(id, companyId, email, displayName, passwordHash, status, roles, 0L);
    }

    /** 版（`app_user.version`）を持つ利用者を組み立てる（リポジトリの復元が使う。Bolt 34）。 */
    public static User of(
            UserId id,
            CompanyId companyId,
            EmailAddress email,
            String displayName,
            String passwordHash,
            UserStatus status,
            Set<Role> roles,
            long version) {
        return new User(id, companyId, email, displayName, passwordHash, status, roles, version);
    }

    /**
     * 認証できない理由を返す（IA-INV-09）。認証できるなら空。password の照合の後に確かめる（照合の前に拒否すると、誤った password でも利用停止が応答の時間から分かるため）。
     *
     * @param company 所属企業
     */
    public Optional<AuthenticationRejection> authenticationRejection(Company company) {
        if (!company.id().equals(companyId)) {
            throw new IllegalArgumentException("所属企業で確かめる");
        }
        if (status == UserStatus.SUSPENDED) {
            return Optional.of(AuthenticationRejection.SUSPENDED);
        }
        if (!company.active()) {
            return Optional.of(AuthenticationRejection.COMPANY_INACTIVE);
        }
        return Optional.empty();
    }

    /**
     * password を変えた利用者を返す（Bolt 33a、US-18 AC8）。新しい password はハッシュ（DelegatingPasswordEncoder の形式。SEC-07）で
     * 受け取る。方針（SEC-04・SEC-05）は呼ぶ側が先に確かめる。
     */
    public User changePassword(String newPasswordHash) {
        return new User(id, companyId, email, displayName, newPasswordHash, status, roles, version);
    }

    /**
     * 役割の変更を受け付けない理由を返す（Bolt 34。US-16 AC3、UC-16 の主シナリオ 4）。受け付けるなら空。判定は、自分の変更 → 役割なし →
     * 変更なし → 監督の役割 → 企業の種類 → 職務分離の順。同時の更新は版を条件にした更新で判定するので、ここでは見ない。
     *
     * @param operator 変更する操作者
     * @param company 所属企業
     * @param newRoles 新しい役割
     */
    public Optional<RoleChangeRejection> roleChangeRejection(UserId operator, Company company, Set<Role> newRoles) {
        if (!company.id().equals(companyId)) {
            throw new IllegalArgumentException("所属企業で確かめる");
        }
        if (id.equals(operator)) {
            return Optional.of(RoleChangeRejection.of(RoleChangeRejectionReason.SELF_CHANGE));
        }
        if (newRoles.isEmpty()) {
            return Optional.of(RoleChangeRejection.of(RoleChangeRejectionReason.NO_ROLES));
        }
        if (roles.equals(EnumSet.copyOf(newRoles))) {
            return Optional.of(RoleChangeRejection.of(RoleChangeRejectionReason.NO_CHANGE));
        }
        Set<Role> oversight = RoleAssignmentPolicy.oversightChanges(roles, newRoles);
        if (!oversight.isEmpty()) {
            return Optional.of(new RoleChangeRejection(RoleChangeRejectionReason.OVERSIGHT_ROLE, oversight));
        }
        Set<Role> unfit = RoleAssignmentPolicy.unfitRoles(company.kind(), newRoles);
        if (!unfit.isEmpty()) {
            return Optional.of(new RoleChangeRejection(RoleChangeRejectionReason.UNFIT_FOR_COMPANY, unfit));
        }
        Set<Role> conflicts = RoleAssignmentPolicy.conflicts(newRoles);
        if (!conflicts.isEmpty()) {
            return Optional.of(new RoleChangeRejection(RoleChangeRejectionReason.CONFLICTING_ROLES, conflicts));
        }
        return Optional.empty();
    }

    /** 役割を変えた利用者を返す（Bolt 34）。判定（{@link #roleChangeRejection}）は呼ぶ側が先に通す。版は変えない（リポジトリが上げる）。 */
    public User changeRoles(Set<Role> newRoles) {
        return new User(id, companyId, email, displayName, passwordHash, status, newRoles, version);
    }

    public UserId id() {
        return id;
    }

    public CompanyId companyId() {
        return companyId;
    }

    public EmailAddress email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
        return status;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    /** 版（`app_user.version`。同時の更新の検出に使う。Bolt 34）。 */
    public long version() {
        return version;
    }
}

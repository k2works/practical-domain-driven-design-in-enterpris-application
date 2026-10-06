package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.AuthenticationRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
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
 * 利用者の登録・利用停止・役割の付与は利用者の管理（US-16）で作る。ロック（IA-INV-03）は W5 で足す。
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

    private User(
            UserId id,
            CompanyId companyId,
            EmailAddress email,
            String displayName,
            String passwordHash,
            UserStatus status,
            Set<Role> roles) {
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
        return new User(id, companyId, email, displayName, passwordHash, status, roles);
    }

    /**
     * 認証できない理由を返す（IA-INV-09）。認証できるなら空。password の照合の前に確かめる理由だけを返す。
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
}

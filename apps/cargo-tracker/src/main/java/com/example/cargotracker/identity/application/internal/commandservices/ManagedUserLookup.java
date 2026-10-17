package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Objects;
import java.util.Optional;

/**
 * 利用者の管理の判定の前半（Bolt 34e で役割の変更 {@link UserRoleChangeService} と利用状態の変更 {@link UserStatusChangeService}
 * の重複を寄せた）。操作者がシステム管理者か → 利用者 → 所属企業 → 管理範囲（IA-INV-14）の順に確かめ、管理する利用者と所属企業か、
 * 受け付けない理由を返す。管理範囲は規則より先に見る（範囲外の利用者の様子を規則の拒否で漏らさない）。Bean にせず、2 つのサービスが
 * 組み立てる。
 */
final class ManagedUserLookup {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final UserAssignmentRepository assignments;

    ManagedUserLookup(UserRepository users, CompanyRepository companies, UserAssignmentRepository assignments) {
        this.users = Objects.requireNonNull(users, "users");
        this.companies = Objects.requireNonNull(companies, "companies");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
    }

    /**
     * 操作者が管理する利用者を探す。
     *
     * @param operator 操作者
     * @param target 対象の利用者
     */
    Result find(AuthenticatedActor operator, UserId target) {
        if (!operator.hasRole(Role.SYSTEM_ADMIN)) {
            return Refusal.NOT_PERMITTED;
        }
        Optional<User> user = users.findById(target);
        Optional<Company> company = user.flatMap(found -> companies.findById(found.companyId()));
        if (user.isEmpty() || company.isEmpty()) {
            return Refusal.NOT_FOUND;
        }
        if (!assignments.scopeOf(operator.userId()).covers(user.get().companyId())) {
            return Refusal.OUT_OF_SCOPE;
        }
        return new Managed(user.get(), company.get());
    }

    /** 探した結果。 */
    sealed interface Result permits Managed, Refusal {}

    /**
     * 操作者が管理する利用者と、その所属企業。
     *
     * @param user 利用者
     * @param company 所属企業
     */
    record Managed(User user, Company company) implements Result {

        Managed {
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(company, "company");
        }
    }

    /** 受け付けない理由（規則の拒否を持たない）。各サービスが共通の拒否の型に写す。 */
    enum Refusal implements Result {
        /** 操作者がシステム管理者でない。 */
        NOT_PERMITTED,
        /** 利用者（またはその所属企業）がいない。 */
        NOT_FOUND,
        /** 対象の利用者が操作者の管理範囲の外。 */
        OUT_OF_SCOPE;

        /** 共通の拒否の型に写す。 */
        <R> UserAdministrationRefusal<R> toRefusal() {
            return switch (this) {
                case NOT_PERMITTED -> new UserAdministrationRefusal.NotPermitted<>();
                case NOT_FOUND -> new UserAdministrationRefusal.NotFound<>();
                case OUT_OF_SCOPE -> new UserAdministrationRefusal.OutOfScope<>();
            };
        }
    }
}

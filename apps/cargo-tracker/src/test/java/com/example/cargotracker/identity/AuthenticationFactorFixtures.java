package com.example.cargotracker.identity;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;

/** 認証の要素のテストで、外部キーのための企業と利用者を作る（並行のテストと共有する。Bolt 30、Bolt 31 で抜き出した）。 */
public final class AuthenticationFactorFixtures {

    private AuthenticationFactorFixtures() {}

    public static UserId newUser(CompanyRepository companies, UserRepository users) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of("user-" + UUID.randomUUID() + "@example.com"),
                "荷主 太郎",
                "{noop}x",
                UserStatus.ACTIVE,
                Set.of(Role.SHIPPER)));
        return userId;
    }
}

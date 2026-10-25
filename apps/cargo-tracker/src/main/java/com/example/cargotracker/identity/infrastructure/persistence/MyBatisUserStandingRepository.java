package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserStanding;
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 利用者の立場のリポジトリの MyBatis 実装（Bolt 32c、ADR-012 の見直しの 3）。`app_user` と `company` を内部結合し、`user_role` を
 * 外部結合する 1 つの SQL で読み、役割の数だけ出る行を 1 人の利用者にたたむ。役割が 0 件の利用者はデータの不整合で、利用者の
 * 不変条件（{@code User} のコンストラクター）の例外になる（利用者には役割が 1 つ以上要る。BR-15。守りは 1 か所。Bolt 38b、#50）。
 */
@Repository
public class MyBatisUserStandingRepository implements UserStandingRepository {

    private final IdentityMapper mapper;

    public MyBatisUserStandingRepository(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserStanding> findById(UserId id) {
        List<UserStandingRow> rows = mapper.selectUserStanding(id.value());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Set<Role> roles = rows.stream()
                .map(UserStandingRow::role)
                .filter(Objects::nonNull)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        UserStandingRow first = rows.getFirst();
        Company company = Company.of(
                new CompanyId(first.companyId()),
                first.companyName(),
                CompanyKind.valueOf(first.companyKind()),
                first.companyActive());
        User user = User.of(
                new UserId(first.id()),
                company.id(),
                EmailAddress.of(first.email()),
                first.displayName(),
                first.passwordHash(),
                UserStatus.valueOf(first.status()),
                roles);
        return Optional.of(new UserStanding(user, company));
    }
}

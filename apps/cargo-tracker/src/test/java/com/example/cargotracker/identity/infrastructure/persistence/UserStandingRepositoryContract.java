package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 利用者の立場のリポジトリの契約（Bolt 32c、ADR-012 の見直しの 3）。利用者・役割・所属企業を 1 回で読む。H2（dev）と
 * PostgreSQL の両方で同じ SQL が動くことを確かめる（ADR-007）。実装は MyBatis だけなので、この契約は persistence に置く。
 */
abstract class UserStandingRepositoryContract {

    protected abstract UserStandingRepository standings();

    protected abstract CompanyRepository companies();

    protected abstract UserRepository users();

    protected abstract JdbcTemplate jdbc();

    private Company newCompany() {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies().add(company);
        return company;
    }

    private User newUser(Company company, Set<Role> roles) {
        User user = User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of("user-" + UUID.randomUUID() + "@example.com"),
                "荷主 太郎",
                "{noop}x",
                UserStatus.ACTIVE,
                roles);
        users().add(user);
        return user;
    }

    @Test
    void 利用者と役割と所属企業を読む() {
        Company company = newCompany();
        User user = newUser(company, Set.of(Role.SHIPPER));

        assertThat(standings().findById(user.id())).hasValueSatisfying(standing -> {
            assertThat(standing.user().id()).isEqualTo(user.id());
            assertThat(standing.user().companyId()).isEqualTo(company.id());
            assertThat(standing.user().email()).isEqualTo(user.email());
            assertThat(standing.user().displayName()).isEqualTo("荷主 太郎");
            assertThat(standing.user().status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(standing.user().roles()).containsExactly(Role.SHIPPER);
            assertThat(standing.company().id()).isEqualTo(company.id());
            assertThat(standing.company().name()).isEqualTo("荷主 A");
            assertThat(standing.company().kind()).isEqualTo(CompanyKind.SHIPPER);
            assertThat(standing.company().active()).isTrue();
        });
    }

    @Test
    void 役割が複数でも1人の利用者になる() {
        User user = newUser(newCompany(), Set.of(Role.SALES, Role.ROUTE_DESIGNER, Role.TRACKING_MANAGER));

        assertThat(standings().findById(user.id()))
                .hasValueSatisfying(standing -> assertThat(standing.user().roles())
                        .containsExactlyInAnyOrder(Role.SALES, Role.ROUTE_DESIGNER, Role.TRACKING_MANAGER));
    }

    @Test
    void 利用者がいなければ空() {
        newUser(newCompany(), Set.of(Role.SHIPPER));

        assertThat(standings().findById(new UserId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void 利用停止の利用者もそのまま読める() {
        Company company = newCompany();
        User user = newUser(company, Set.of(Role.SHIPPER));
        jdbc().update(
                        "UPDATE identity.app_user SET status = 'SUSPENDED' WHERE id = ?",
                        user.id().value());

        assertThat(standings().findById(user.id())).hasValueSatisfying(standing -> {
            assertThat(standing.user().status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(standing.company().active()).isTrue();
        });
    }

    @Test
    void 無効な企業の利用者もそのまま読める() {
        Company company = newCompany();
        User user = newUser(company, Set.of(Role.SHIPPER));
        jdbc().update(
                        "UPDATE identity.company SET active = FALSE WHERE id = ?",
                        company.id().value());

        assertThat(standings().findById(user.id())).hasValueSatisfying(standing -> {
            assertThat(standing.user().status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(standing.company().active()).isFalse();
        });
    }

    @Test
    void 今の役割の行だけを読む() {
        // 変更の前後の読み比べは統合テストに任せる（ここはトランザクションの中で、MyBatis の 1 次キャッシュが前の結果を返すため）
        User user = newUser(newCompany(), Set.of(Role.SHIPPER, Role.SALES));
        jdbc().update(
                        "DELETE FROM identity.user_role WHERE user_id = ? AND role = 'SALES'",
                        user.id().value());
        jdbc().update(
                        "INSERT INTO identity.user_role (user_id, role, granted_at) VALUES (?, 'TRACKING_MANAGER', CURRENT_TIMESTAMP)",
                        user.id().value());

        assertThat(standings().findById(user.id()))
                .hasValueSatisfying(standing -> assertThat(standing.user().roles())
                        .containsExactlyInAnyOrder(Role.SHIPPER, Role.TRACKING_MANAGER));
    }

    @Test
    void 役割が0件の利用者は利用者の不変条件の例外で利用者IDを示す() {
        // 行 11（Bolt 38b。#50 の役割 0 件の検査の二重を、利用者のコンストラクターの 1 か所にした）
        User user = newUser(newCompany(), Set.of(Role.SHIPPER));
        jdbc().update(
                        "DELETE FROM identity.user_role WHERE user_id = ?",
                        user.id().value());
        UserId id = user.id();
        UserStandingRepository repository = standings();

        assertThatThrownBy(() -> repository.findById(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("役割")
                .hasMessageContaining(id.value().toString());
    }
}

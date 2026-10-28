package com.example.cargotracker.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserAssignmentRepository;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
import com.example.cargotracker.identity.domain.model.valueobjects.AssignedCompanies;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 担当範囲のリポジトリの契約（Bolt 38e。担当企業の読み出しと置き換え）。H2（dev）と PostgreSQL の両方で同じ SQL が動くことを確かめる
 * （ADR-007）。実装は MyBatis だけなので、この契約は persistence に置く（前例 {@link UserStandingRepositoryContract}）。同じ
 * `COMPANY` の行は、業務の役割の利用者のときだけ担当企業として読む（決定 (9)）。
 */
abstract class UserAssignmentRepositoryContract {

    private static final UtcInstant GRANTED_BEFORE = new UtcInstant(Instant.parse("2026-11-01T00:00:00Z"));
    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));

    protected abstract UserAssignmentRepository assignments();

    protected abstract CompanyRepository companies();

    protected abstract UserRepository users();

    protected abstract JdbcTemplate jdbc();

    private CompanyId newCompany(CompanyKind kind) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "企業 " + UUID.randomUUID(), kind, true);
        companies().add(company);
        return company.id();
    }

    private User newUser(CompanyId company, Set<Role> roles) {
        User user = User.of(
                new UserId(UUID.randomUUID()),
                company,
                EmailAddress.of("user-" + UUID.randomUUID() + "@example.com"),
                "営業 太郎",
                "{noop}x",
                UserStatus.ACTIVE,
                roles);
        users().add(user);
        return user;
    }

    private void assign(UserId user, String kind, UUID scopeId, UserId grantedBy) {
        jdbc().update(
                        "INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_by, granted_at)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        user.value(),
                        kind,
                        scopeId,
                        grantedBy == null ? null : grantedBy.value(),
                        Timestamp.from(GRANTED_BEFORE.instant()));
    }

    private Map<UUID, Map<String, Object>> companyRowsOf(UserId user) {
        return jdbc()
                .queryForList(
                        "SELECT scope_id, granted_by, granted_at FROM identity.user_assignment"
                                + " WHERE user_id = ? AND scope_kind = 'COMPANY'",
                        user.value())
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row.get("scope_id"), row -> row));
    }

    private long versionOf(UserId user) {
        return jdbc().queryForObject("SELECT version FROM identity.app_user WHERE id = ?", Long.class, user.value());
    }

    private static Instant instantOf(Object timestamp) {
        return timestamp instanceof java.time.OffsetDateTime offset
                ? offset.toInstant()
                : ((Timestamp) timestamp).toInstant();
    }

    @Test
    void 業務の役割の利用者の担当企業を読む() {
        CompanyId operator = newCompany(CompanyKind.OPERATOR);
        CompanyId shipperA = newCompany(CompanyKind.SHIPPER);
        CompanyId shipperB = newCompany(CompanyKind.SHIPPER);
        User sales = newUser(operator, Set.of(Role.SALES, Role.ROUTE_DESIGNER));
        assign(sales.id(), "COMPANY", shipperA.value(), null);
        assign(sales.id(), "COMPANY", shipperB.value(), null);

        assertThat(assignments().assignedCompaniesOf(sales.id()).companies())
                .containsExactlyInAnyOrder(shipperA, shipperB);
    }

    @Test
    void 割り当てのない業務担当者の担当企業は空() {
        User sales = newUser(newCompany(CompanyKind.OPERATOR), Set.of(Role.CUSTOMER_SUPPORT));

        assertThat(assignments().assignedCompaniesOf(sales.id()).companies()).isEmpty();
    }

    @Test
    void システム管理者の管理範囲の行は担当企業として読まず管理範囲は今のまま() {
        // 行 11（決定 (9)、V11）。同じ COMPANY の行を、役割で読み分ける
        CompanyId operator = newCompany(CompanyKind.OPERATOR);
        CompanyId shipper = newCompany(CompanyKind.SHIPPER);
        User admin = newUser(operator, Set.of(Role.SYSTEM_ADMIN));
        assign(admin.id(), "COMPANY", shipper.value(), null);

        assertThat(assignments().assignedCompaniesOf(admin.id()).companies()).isEmpty();
        assertThat(assignments().scopeOf(admin.id()).companies()).containsExactly(shipper);
    }

    @Test
    void データ責任者と荷主の行も担当企業として読まない() {
        CompanyId operator = newCompany(CompanyKind.OPERATOR);
        CompanyId shipper = newCompany(CompanyKind.SHIPPER);
        User steward = newUser(operator, Set.of(Role.DATA_STEWARD));
        User shipperUser = newUser(shipper, Set.of(Role.SHIPPER));
        assign(steward.id(), "COMPANY", shipper.value(), null);
        assign(shipperUser.id(), "COMPANY", shipper.value(), null);

        assertThat(assignments().assignedCompaniesOf(steward.id()).companies()).isEmpty();
        assertThat(assignments().assignedCompaniesOf(shipperUser.id()).companies())
                .isEmpty();
    }

    @Test
    void 置き換えは版を上げ範囲の中の差だけを書き変わらない行はそのまま残す() {
        // 行 3・4（V12）。変わらない行の付与の時刻と付与した人は元のまま、足した行は新しい値
        CompanyId operator = newCompany(CompanyKind.OPERATOR);
        CompanyId kept = newCompany(CompanyKind.SHIPPER);
        CompanyId removed = newCompany(CompanyKind.SHIPPER);
        CompanyId added = newCompany(CompanyKind.SHIPPER);
        User admin = newUser(operator, Set.of(Role.SYSTEM_ADMIN));
        User earlierAdmin = newUser(operator, Set.of(Role.SYSTEM_ADMIN));
        User sales = newUser(operator, Set.of(Role.SALES));
        assign(sales.id(), "COMPANY", kept.value(), earlierAdmin.id());
        assign(sales.id(), "COMPANY", removed.value(), earlierAdmin.id());
        long version = versionOf(sales.id());

        boolean replaced = assignments()
                .replaceCompanies(
                        sales.id(),
                        version,
                        new AdministrationScope(Set.of(kept, removed, added)),
                        new AssignedCompanies(Set.of(kept, added)),
                        admin.id(),
                        NOW);

        assertThat(replaced).isTrue();
        assertThat(versionOf(sales.id())).isEqualTo(version + 1);
        Map<UUID, Map<String, Object>> rows = companyRowsOf(sales.id());
        assertThat(rows).containsOnlyKeys(kept.value(), added.value());
        assertThat(rows.get(kept.value()))
                .containsEntry("granted_by", earlierAdmin.id().value());
        assertThat(instantOf(rows.get(kept.value()).get("granted_at"))).isEqualTo(GRANTED_BEFORE.instant());
        assertThat(rows.get(added.value()))
                .containsEntry("granted_by", admin.id().value());
        assertThat(instantOf(rows.get(added.value()).get("granted_at"))).isEqualTo(NOW.instant());
    }

    @Test
    void 解除した企業を追加し直すと付与の時刻と付与した人は新しい値になる() {
        // 行 4（T-216。打ち消す操作の後の場面）
        CompanyId operator = newCompany(CompanyKind.OPERATOR);
        CompanyId shipper = newCompany(CompanyKind.SHIPPER);
        User earlierAdmin = newUser(operator, Set.of(Role.SYSTEM_ADMIN));
        User admin = newUser(operator, Set.of(Role.SYSTEM_ADMIN));
        User sales = newUser(operator, Set.of(Role.SALES));
        assign(sales.id(), "COMPANY", shipper.value(), earlierAdmin.id());
        AdministrationScope scope = new AdministrationScope(Set.of(shipper));

        assignments()
                .replaceCompanies(sales.id(), versionOf(sales.id()), scope, AssignedCompanies.none(), admin.id(), NOW);
        UtcInstant later = new UtcInstant(NOW.instant().plusSeconds(60));
        assignments()
                .replaceCompanies(
                        sales.id(),
                        versionOf(sales.id()),
                        scope,
                        new AssignedCompanies(Set.of(shipper)),
                        admin.id(),
                        later);

        Map<String, Object> row = companyRowsOf(sales.id()).get(shipper.value());
        assertThat(row).containsEntry("granted_by", admin.id().value());
        assertThat(instantOf(row.get("granted_at"))).isEqualTo(later.instant());
    }

    @Test
    void 範囲の外の担当企業と担当案件の行は置き換えでも残る() {
        // 行 6・6b（V2・V13）
        CompanyId operator = newCompany(CompanyKind.OPERATOR);
        CompanyId inside = newCompany(CompanyKind.SHIPPER);
        CompanyId outside = newCompany(CompanyKind.SHIPPER);
        // 担当案件の行は、解除する企業と同じ scope_id で置く（DELETE が scope_kind を見ないと、この行も消える。V13）
        UUID booking = inside.value();
        User admin = newUser(operator, Set.of(Role.SYSTEM_ADMIN));
        User sales = newUser(operator, Set.of(Role.SALES));
        assign(sales.id(), "COMPANY", inside.value(), null);
        assign(sales.id(), "COMPANY", outside.value(), null);
        assign(sales.id(), "BOOKING", booking, null);

        assignments()
                .replaceCompanies(
                        sales.id(),
                        versionOf(sales.id()),
                        new AdministrationScope(Set.of(inside)),
                        AssignedCompanies.none(),
                        admin.id(),
                        NOW);

        assertThat(companyRowsOf(sales.id())).containsOnlyKeys(outside.value());
        List<UUID> bookings = jdbc().queryForList(
                        "SELECT scope_id FROM identity.user_assignment WHERE user_id = ? AND scope_kind = 'BOOKING'",
                        UUID.class,
                        sales.id().value());
        assertThat(bookings).containsExactly(booking);
    }

    @Test
    void 版が違えば置き換えず何も書かない() {
        // 行 9（V4）
        CompanyId operator = newCompany(CompanyKind.OPERATOR);
        CompanyId shipper = newCompany(CompanyKind.SHIPPER);
        User admin = newUser(operator, Set.of(Role.SYSTEM_ADMIN));
        User sales = newUser(operator, Set.of(Role.SALES));
        long version = versionOf(sales.id());

        boolean replaced = assignments()
                .replaceCompanies(
                        sales.id(),
                        version + 1,
                        new AdministrationScope(Set.of(shipper)),
                        new AssignedCompanies(Set.of(shipper)),
                        admin.id(),
                        NOW);

        assertThat(replaced).isFalse();
        assertThat(versionOf(sales.id())).isEqualTo(version);
        assertThat(companyRowsOf(sales.id())).isEmpty();
    }
}

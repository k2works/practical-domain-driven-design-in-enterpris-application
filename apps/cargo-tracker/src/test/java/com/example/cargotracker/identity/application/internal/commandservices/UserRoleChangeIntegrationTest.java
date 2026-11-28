package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 役割の変更のサービス（Bolt 34。US-16 AC1 の役割の分、UC-16 の 4b）。本物の PostgreSQL で、版の条件・役割の差分・監査記録を
 * 1 つのトランザクションで書くことと、同時の 2 つの変更で 1 つだけが通ることを確かめる。共有の表は利用者で絞る（T-113）。
 */
@IdentityIntegrationTest
class UserRoleChangeIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");

    @Autowired
    UserRoleChangeService service;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    MutableClock clock;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    private Company operator;
    private AuthenticatedActor admin;

    @BeforeEach
    void 時計を合わせA社と管理者を置く() {
        clock.setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(operator));
        UserId adminId = new UserId(UUID.randomUUID());
        admin = new AuthenticatedActor(adminId, operator.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
        // 管理範囲（Bolt 34b）。担当範囲の表は利用者への外部キーを持つので、管理者の利用者も作る
        transactionTemplate.executeWithoutResult(status -> {
            users.add(User.of(
                    adminId,
                    operator.id(),
                    EmailAddress.of("admin-" + adminId.value() + "@example.com"),
                    "管理 次郎",
                    "{noop}unused",
                    UserStatus.ACTIVE,
                    Set.of(Role.SYSTEM_ADMIN)));
            AdministrationFixtures.assignCompany(jdbc, adminId, operator.id());
        });
    }

    private UserId newSales() {
        UserId id = new UserId(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> users.add(User.of(
                id,
                operator.id(),
                EmailAddress.of("sales-" + id.value() + "@example.com"),
                "営業 一郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SALES))));
        return id;
    }

    private List<Map<String, Object>> rolesOf(UserId id) {
        return jdbc.queryForList(
                "SELECT role, granted_by FROM identity.user_role WHERE user_id = ? ORDER BY role", id.value());
    }

    private long versionOf(UserId id) {
        return jdbc.queryForObject("SELECT version FROM identity.app_user WHERE id = ?", Long.class, id.value());
    }

    private List<Map<String, Object>> auditsOf(UserId target) {
        return jdbc.queryForList(
                "SELECT actor_user_id, action, result, target_type, target_version, before_state, after_state"
                        + " FROM identity.audit_record WHERE target_id = ?",
                target.value().toString());
    }

    @Test
    void 役割を足し外して版を上げ監査を1行書く() {
        UserId sales = newSales();

        assertThat(service.change(admin, sales, 0L, Set.of(Role.ROUTE_DESIGNER, Role.TRACKING_MANAGER)))
                .isEqualTo(new RoleChangeOutcome.Changed(1L));

        assertThat(rolesOf(sales))
                .extracting(row -> row.get("role"), row -> row.get("granted_by"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "ROUTE_DESIGNER", admin.userId().value()),
                        org.assertj.core.groups.Tuple.tuple(
                                "TRACKING_MANAGER", admin.userId().value()));
        assertThat(versionOf(sales)).isEqualTo(1L);
        assertThat(users.findById(sales).orElseThrow().version()).isEqualTo(1L);
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("actor_user_id", admin.userId().value())
                        .containsEntry("action", "ROLES_CHANGED")
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("target_type", "USER")
                        .containsEntry("target_version", "1")
                        .containsEntry("before_state", "SALES")
                        .containsEntry("after_state", "ROUTE_DESIGNER,TRACKING_MANAGER"));
    }

    @Test
    void 残した役割の付与の記録は変えない() {
        UserId sales = newSales();

        service.change(admin, sales, 0L, Set.of(Role.SALES, Role.ROUTE_DESIGNER));

        assertThat(rolesOf(sales))
                .extracting(row -> row.get("role"), row -> row.get("granted_by"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "ROUTE_DESIGNER", admin.userId().value()),
                        org.assertj.core.groups.Tuple.tuple("SALES", null));
    }

    @Test
    void 古い版からの変更は何も書かない() {
        UserId sales = newSales();
        service.change(admin, sales, 0L, Set.of(Role.ROUTE_DESIGNER));

        assertThat(service.change(admin, sales, 0L, Set.of(Role.TRACKING_MANAGER)))
                .isEqualTo(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));

        assertThat(rolesOf(sales)).extracting(row -> row.get("role")).containsExactly("ROUTE_DESIGNER");
        assertThat(versionOf(sales)).isEqualTo(1L);
        assertThat(auditsOf(sales)).hasSize(1);
    }

    @Test
    void 管理範囲外の利用者の役割は変えず何も書かない() {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(outside));
        UserId stranger = new UserId(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> users.add(User.of(
                stranger,
                outside.id(),
                EmailAddress.of("stranger-" + stranger.value() + "@example.com"),
                "営業 三郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SALES))));

        assertThat(service.change(admin, stranger, 0L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.OutOfScope<>()));

        assertThat(rolesOf(stranger)).extracting(row -> row.get("role")).containsExactly("SALES");
        assertThat(versionOf(stranger)).isZero();
        assertThat(auditsOf(stranger)).isEmpty();
    }

    @Test
    void 同時の2つの変更は1つだけが通り後は古い版になる() {
        UserId sales = newSales();

        List<RoleChangeOutcome> results = OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate,
                () -> service.change(admin, sales, 0L, Set.of(Role.ROUTE_DESIGNER)),
                () -> service.change(admin, sales, 0L, Set.of(Role.TRACKING_MANAGER)));

        assertThat(results)
                .containsExactly(
                        new RoleChangeOutcome.Changed(1L),
                        new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));
        assertThat(rolesOf(sales)).extracting(row -> row.get("role")).containsExactly("ROUTE_DESIGNER");
        assertThat(versionOf(sales)).isEqualTo(1L);
        assertThat(auditsOf(sales)).hasSize(1);
    }
}

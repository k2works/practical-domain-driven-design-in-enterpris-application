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
import java.util.Arrays;
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
 * 担当範囲の変更のサービス（Bolt 38e。US-16 AC1 の担当の関係と変更履歴）。本物の PostgreSQL で、版の条件・担当企業の差・監査記録を
 * 1 つのトランザクションで書くことと、同時の 2 つの変更（担当範囲どうし、担当範囲と役割の変更）で 1 つだけが通ることを確かめる。共有の表は
 * 利用者で絞る（T-113）。
 */
@IdentityIntegrationTest
class UserAssignmentIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:30:00Z");

    @Autowired
    UserAssignmentService service;

    @Autowired
    UserRoleChangeService roleChanges;

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
    private Company shipperA;
    private Company shipperB;
    private Company outside;
    private AuthenticatedActor admin;

    private Company newCompany(String name, CompanyKind kind) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), name, kind, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(company));
        return company;
    }

    @BeforeEach
    void 時計を合わせA社と荷主と管理者を置く() {
        clock.setInstant(NOW);
        operator = newCompany("A 社", CompanyKind.OPERATOR);
        shipperA = newCompany("荷主 A", CompanyKind.SHIPPER);
        shipperB = newCompany("荷主 B", CompanyKind.SHIPPER);
        outside = newCompany("範囲外の荷主", CompanyKind.SHIPPER);
        UserId adminId = new UserId(UUID.randomUUID());
        admin = new AuthenticatedActor(adminId, operator.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
        // 管理範囲は明示の補助で入れる（#50 (a)。管理者を作っただけでは入らない）
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
            AdministrationFixtures.assignCompany(jdbc, adminId, shipperA.id());
            AdministrationFixtures.assignCompany(jdbc, adminId, shipperB.id());
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

    private List<UUID> companiesOf(UserId id) {
        return jdbc.queryForList(
                "SELECT scope_id FROM identity.user_assignment WHERE user_id = ? AND scope_kind = 'COMPANY'"
                        + " ORDER BY scope_id",
                UUID.class,
                id.value());
    }

    private long versionOf(UserId id) {
        return jdbc.queryForObject("SELECT version FROM identity.app_user WHERE id = ?", Long.class, id.value());
    }

    private List<Map<String, Object>> auditsOf(UserId target) {
        return jdbc.queryForList(
                "SELECT actor_user_id, action, result, target_type, target_version, before_state, after_state"
                        + " FROM identity.audit_record WHERE target_id = ? ORDER BY occurred_at, target_version",
                target.value().toString());
    }

    private static String sorted(CompanyId... ids) {
        return Arrays.stream(ids)
                .map(id -> id.value().toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    @Test
    void 担当企業を追加して版を上げ監査を1行書く() {
        // 行 3（AC-2、V5）
        UserId sales = newSales();

        assertThat(service.change(admin, sales, 0L, Set.of(shipperA.id(), shipperB.id())))
                .isEqualTo(new AssignmentChangeOutcome.Changed(1L));

        assertThat(companiesOf(sales))
                .containsExactlyInAnyOrder(shipperA.id().value(), shipperB.id().value());
        assertThat(versionOf(sales)).isEqualTo(1L);
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("actor_user_id", admin.userId().value())
                        .containsEntry("action", "ASSIGNMENTS_CHANGED")
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("target_type", "USER")
                        .containsEntry("target_version", "1")
                        .containsEntry("before_state", "")
                        .containsEntry("after_state", sorted(shipperA.id(), shipperB.id())));
    }

    @Test
    void 解除した企業を後で追加し直すと監査は2行になる() {
        // 行 4（T-216）
        UserId sales = newSales();
        service.change(admin, sales, 0L, Set.of(shipperA.id()));
        service.change(admin, sales, 1L, Set.of());

        assertThat(service.change(admin, sales, 2L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeOutcome.Changed(3L));

        assertThat(companiesOf(sales)).containsExactly(shipperA.id().value());
        assertThat(auditsOf(sales))
                .extracting(row -> row.get("before_state"), row -> row.get("after_state"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("", sorted(shipperA.id())),
                        org.assertj.core.groups.Tuple.tuple(sorted(shipperA.id()), ""),
                        org.assertj.core.groups.Tuple.tuple("", sorted(shipperA.id())));
    }

    @Test
    void 管理範囲の外の担当企業は残り監査には全体を書く() {
        // 行 6（V2）
        UserId sales = newSales();
        transactionTemplate.executeWithoutResult(
                status -> AdministrationFixtures.assignCompany(jdbc, sales, outside.id()));

        assertThat(service.change(admin, sales, 0L, Set.of(shipperA.id())))
                .isEqualTo(new AssignmentChangeOutcome.Changed(1L));

        assertThat(companiesOf(sales))
                .containsExactlyInAnyOrder(shipperA.id().value(), outside.id().value());
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("before_state", sorted(outside.id()))
                        .containsEntry("after_state", sorted(shipperA.id(), outside.id())));
    }

    @Test
    void 古い版からの変更は何も書かない() {
        UserId sales = newSales();
        service.change(admin, sales, 0L, Set.of(shipperA.id()));

        assertThat(service.change(admin, sales, 0L, Set.of(shipperB.id())))
                .isEqualTo(new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));

        assertThat(companiesOf(sales)).containsExactly(shipperA.id().value());
        assertThat(versionOf(sales)).isEqualTo(1L);
        assertThat(auditsOf(sales)).hasSize(1);
    }

    @Test
    void 担当範囲を変えた後は同じ版からの役割の変更が古い版になる() {
        // 行 9（V15）。役割の変更と同じ版を使う
        UserId sales = newSales();
        service.change(admin, sales, 0L, Set.of(shipperA.id()));

        assertThat(roleChanges.change(admin, sales, 0L, Set.of(Role.ROUTE_DESIGNER)))
                .isEqualTo(new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));
    }

    @Test
    void 同時の2つの担当範囲の変更は1つだけが通り後は古い版になる() {
        // 行 9（V4）
        UserId sales = newSales();

        List<AssignmentChangeOutcome> results = OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate,
                () -> service.change(admin, sales, 0L, Set.of(shipperA.id())),
                () -> service.change(admin, sales, 0L, Set.of(shipperB.id())));

        assertThat(results)
                .containsExactly(
                        new AssignmentChangeOutcome.Changed(1L),
                        new AssignmentChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));
        assertThat(companiesOf(sales)).containsExactly(shipperA.id().value());
        assertThat(versionOf(sales)).isEqualTo(1L);
        assertThat(auditsOf(sales)).hasSize(1);
    }

    @Test
    void 同時の担当範囲の変更と役割の変更は1つだけが通る() {
        UserId sales = newSales();

        List<Object> results = OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate,
                () -> service.change(admin, sales, 0L, Set.of(shipperA.id())),
                () -> roleChanges.change(admin, sales, 0L, Set.of(Role.ROUTE_DESIGNER)));

        assertThat(results)
                .containsExactly(
                        new AssignmentChangeOutcome.Changed(1L),
                        new RoleChangeOutcome.Refused(new UserAdministrationRefusal.Stale<>()));
        assertThat(versionOf(sales)).isEqualTo(1L);
    }
}

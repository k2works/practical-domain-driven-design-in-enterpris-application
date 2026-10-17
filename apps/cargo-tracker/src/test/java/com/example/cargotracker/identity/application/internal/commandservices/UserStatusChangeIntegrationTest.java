package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.OverlappingTransactions;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用状態の変更のサービス（Bolt 34d。US-16 AC5、UC-16 の 4b・5a）。本物の PostgreSQL で、版の条件・利用状態・監査記録を 1 つの
 * トランザクションで書くことと、役割の変更と版を共有すること、同時の 2 つの変更で 1 つだけが通ることを確かめる。共有の表は利用者で
 * 絞る（T-113）。
 */
@IdentityIntegrationTest
class UserStatusChangeIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-17T05:00:00Z");

    private static final Optional<SuspensionReason> USAGE_ENDED = Optional.of(SuspensionReason.USAGE_ENDED);

    @Autowired
    UserStatusChangeService service;

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
    private AuthenticatedActor admin;

    @BeforeEach
    void 時計を合わせA社と管理者を置く() {
        clock.setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(operator));
        UserId adminId = new UserId(UUID.randomUUID());
        admin = new AuthenticatedActor(adminId, operator.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
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

    private UserId newSales(Company company) {
        UserId id = new UserId(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> users.add(User.of(
                id,
                company.id(),
                EmailAddress.of("sales-" + id.value() + "@example.com"),
                "営業 一郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SALES))));
        return id;
    }

    private Map<String, Object> rowOf(UserId id) {
        return jdbc.queryForMap("SELECT status, version, updated_at FROM identity.app_user WHERE id = ?", id.value());
    }

    private List<Map<String, Object>> auditsOf(UserId target) {
        return jdbc.queryForList(
                "SELECT actor_user_id, action, result, reason, target_type, target_version, before_state, after_state"
                        + " FROM identity.audit_record WHERE target_id = ? ORDER BY occurred_at, action",
                target.value().toString());
    }

    @Test
    void 利用を停止して版と時刻を上げ監査を1行書く() {
        UserId sales = newSales(operator);
        // 利用者を作った時刻と分ける（更新の時刻を書かない誤りを見分けるため）
        Instant suspendedAt = NOW.plusSeconds(60);
        clock.setInstant(suspendedAt);

        assertThat(service.change(admin, sales, 0L, UserStatus.SUSPENDED, USAGE_ENDED))
                .isEqualTo(new StatusChangeResult.Changed(1L));

        assertThat(rowOf(sales)).containsEntry("status", "SUSPENDED").containsEntry("version", 1L);
        assertThat(((Timestamp) rowOf(sales).get("updated_at")).toInstant()).isEqualTo(suspendedAt);
        assertThat(users.findById(sales).orElseThrow().status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("actor_user_id", admin.userId().value())
                        .containsEntry("action", "USER_SUSPENDED")
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("reason", "USAGE_ENDED")
                        .containsEntry("target_type", "USER")
                        .containsEntry("target_version", "1")
                        .containsEntry("before_state", "ACTIVE")
                        .containsEntry("after_state", "SUSPENDED"));
    }

    @Test
    void 停止した利用者を再開して版を上げ監査を書く() {
        UserId sales = newSales(operator);
        service.change(admin, sales, 0L, UserStatus.SUSPENDED, USAGE_ENDED);
        clock.setInstant(NOW.plusSeconds(60));

        assertThat(service.change(admin, sales, 1L, UserStatus.ACTIVE, Optional.empty()))
                .isEqualTo(new StatusChangeResult.Changed(2L));

        assertThat(rowOf(sales)).containsEntry("status", "ACTIVE").containsEntry("version", 2L);
        assertThat(auditsOf(sales))
                .extracting(row -> row.get("action"), row -> row.get("reason"), row -> row.get("after_state"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("USER_SUSPENDED", "USAGE_ENDED", "SUSPENDED"),
                        org.assertj.core.groups.Tuple.tuple("USER_REACTIVATED", null, "ACTIVE"));
    }

    @Test
    void 古い版からの変更は何も書かない() {
        UserId sales = newSales(operator);
        service.change(admin, sales, 0L, UserStatus.SUSPENDED, USAGE_ENDED);

        assertThat(service.change(admin, sales, 0L, UserStatus.ACTIVE, Optional.empty()))
                .isEqualTo(new StatusChangeResult.Stale());

        assertThat(rowOf(sales)).containsEntry("status", "SUSPENDED").containsEntry("version", 1L);
        assertThat(auditsOf(sales)).hasSize(1);
    }

    @Test
    void 役割の変更の後の停止は古い版になる() {
        UserId sales = newSales(operator);
        roleChanges.change(admin, sales, 0L, Set.of(Role.ROUTE_DESIGNER));

        assertThat(service.change(admin, sales, 0L, UserStatus.SUSPENDED, USAGE_ENDED))
                .as("役割の変更と版を共有する")
                .isEqualTo(new StatusChangeResult.Stale());

        assertThat(rowOf(sales)).containsEntry("status", "ACTIVE").containsEntry("version", 1L);
    }

    @Test
    void 停止中の利用者の役割を変えられる() {
        UserId sales = newSales(operator);
        service.change(admin, sales, 0L, UserStatus.SUSPENDED, USAGE_ENDED);

        assertThat(roleChanges.change(admin, sales, 1L, Set.of(Role.ROUTE_DESIGNER)))
                .as("停止中の利用者の役割の変更は許す（Bolt 34d の決定 3）")
                .isEqualTo(new RoleChangeResult.Changed(2L));
        assertThat(rowOf(sales)).containsEntry("status", "SUSPENDED");
    }

    @Test
    void 管理範囲外の利用者の利用状態は変えず何も書かない() {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(outside));
        UserId stranger = newSales(outside);

        assertThat(service.change(admin, stranger, 0L, UserStatus.SUSPENDED, USAGE_ENDED))
                .isEqualTo(new StatusChangeResult.OutOfScope());

        assertThat(rowOf(stranger)).containsEntry("status", "ACTIVE").containsEntry("version", 0L);
        assertThat(auditsOf(stranger)).isEmpty();
    }

    @Test
    void 同時の2つの変更は1つだけが通り後は古い版になる() {
        UserId sales = newSales(operator);

        List<StatusChangeResult> results = OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate,
                () -> service.change(admin, sales, 0L, UserStatus.SUSPENDED, USAGE_ENDED),
                () -> service.change(admin, sales, 0L, UserStatus.SUSPENDED, Optional.of(SuspensionReason.EMERGENCY)));

        assertThat(results).containsExactly(new StatusChangeResult.Changed(1L), new StatusChangeResult.Stale());
        assertThat(rowOf(sales)).containsEntry("status", "SUSPENDED").containsEntry("version", 1L);
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("reason", "USAGE_ENDED"));
    }
}

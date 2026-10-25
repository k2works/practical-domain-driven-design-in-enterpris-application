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
 * 利用者の登録のサービス（Bolt 38b2。US-16 AC1・AC2）。本物の PostgreSQL で、利用者・役割（付与した操作者）・監査記録を 1 つのトランザクションで
 * 書くこと、範囲外の企業への試行だけを記録すること、同じメールアドレスの同時の 2 つで 1 つだけが通ることを確かめる。共有の表は利用者と企業で
 * 絞る（T-113）。
 */
@IdentityIntegrationTest
class UserRegistrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");

    @Autowired
    UserRegistrationService service;

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

    private static EmailAddress newEmail() {
        return EmailAddress.of("hanako-" + UUID.randomUUID() + "@example.com");
    }

    private List<Map<String, Object>> auditsOf(String targetId) {
        return jdbc.queryForList("SELECT * FROM identity.audit_record WHERE target_id = ?", targetId);
    }

    private int usersWith(EmailAddress email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.app_user WHERE email = ?", Integer.class, email.value());
    }

    @Test
    void 登録すると利用者と付与した操作者つきの役割と監査を書く() {
        // 行 1
        EmailAddress email = newEmail();

        UserRegistrationOutcome outcome = service.register(admin, operator.id(), email, "新人 花子", Set.of(Role.SALES));

        assertThat(outcome).isInstanceOf(UserRegistrationOutcome.Registered.class);
        UserId registered = ((UserRegistrationOutcome.Registered) outcome).userId();
        User user = users.findById(registered).orElseThrow();
        assertThat(user.companyId()).isEqualTo(operator.id());
        assertThat(user.passwordHash()).startsWith("{bcrypt}");
        assertThat(jdbc.queryForList(
                        "SELECT role, granted_by FROM identity.user_role WHERE user_id = ?", registered.value()))
                .containsExactly(
                        Map.of("role", "SALES", "granted_by", admin.userId().value()));
        List<Map<String, Object>> audits = auditsOf(registered.value().toString());
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst())
                .containsEntry("action", "USER_REGISTERED")
                .containsEntry("target_type", "USER")
                .containsEntry("target_version", "0")
                .containsEntry("after_state", "SALES");
        assertThat(audits.getFirst().get("before_state")).isNull();
        // 個人情報を残さない（行のすべての列で、メールアドレスと表示名のどちらも含まない）
        assertThat(audits.getFirst().values())
                .noneMatch(value -> String.valueOf(value).contains(email.value())
                        || String.valueOf(value).contains("新人 花子"));
    }

    private int auditsBy(UserId actor) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE actor_user_id = ?", Integer.class, actor.value());
    }

    @Test
    void 外側のトランザクションの中で範囲外の企業へ登録すると記録せずに例外にする() {
        // 範囲外の記録のガード（外側に合流すると、外側のロールバックで試行の記録が消える。IA-INV-14。開発レビュー）
        Company other = Company.of(new CompanyId(UUID.randomUUID()), "B 社", CompanyKind.SHIPPER, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(other));
        EmailAddress email = newEmail();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                        status -> service.register(admin, other.id(), email, "新人 花子", Set.of(Role.SHIPPER))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("外側のトランザクション");
        assertThat(auditsOf(other.id().value().toString())).isEmpty();
    }

    @Test
    void 範囲外の企業への登録は試行だけを記録し確かめるときは記録しない() {
        // 行 3b・行 11。対照の範囲内の登録は書く（T-106）
        Company other = Company.of(new CompanyId(UUID.randomUUID()), "B 社", CompanyKind.SHIPPER, true);
        transactionTemplate.executeWithoutResult(status -> companies.add(other));
        EmailAddress email = newEmail();

        assertThat(service.check(admin, other.id(), email, "新人 花子", Set.of(Role.SHIPPER)))
                .isEqualTo(new UserRegistrationCheck.Refused(new UserRegistrationRefusal.CompanyOutOfScope()));
        assertThat(auditsOf(other.id().value().toString())).isEmpty();

        assertThat(service.register(admin, other.id(), email, "新人 花子", Set.of(Role.SHIPPER)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.CompanyOutOfScope()));
        assertThat(usersWith(email)).isZero();
        List<Map<String, Object>> audits = auditsOf(other.id().value().toString());
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst())
                .containsEntry("action", "ADMIN_SCOPE_DENIED")
                .containsEntry("result", "FAILURE")
                .containsEntry("reason", "OUT_OF_SCOPE")
                .containsEntry("target_type", "COMPANY");

        EmailAddress control = newEmail();
        assertThat(service.register(admin, operator.id(), control, "対照 太郎", Set.of(Role.SALES)))
                .isInstanceOf(UserRegistrationOutcome.Registered.class);
        assertThat(usersWith(control)).isOne();
    }

    @Test
    void 受け付けられる登録を確かめても書かない() {
        // 行 11
        EmailAddress email = newEmail();

        int audits = auditsBy(admin.userId());

        assertThat(service.check(admin, operator.id(), email, "新人 花子", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationCheck.Acceptable());
        assertThat(usersWith(email)).isZero();
        assertThat(auditsBy(admin.userId())).isEqualTo(audits);
    }

    @Test
    void 既にいるメールアドレスは拒み何も書かない() {
        // 行 8
        EmailAddress email = newEmail();
        assertThat(service.register(admin, operator.id(), email, "先の 人", Set.of(Role.SALES)))
                .isInstanceOf(UserRegistrationOutcome.Registered.class);

        int audits = auditsBy(admin.userId());

        assertThat(service.register(admin, operator.id(), email, "後の 人", Set.of(Role.SALES)))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.DuplicateEmail()));
        assertThat(usersWith(email)).isOne();
        assertThat(auditsBy(admin.userId())).isEqualTo(audits);
    }

    @Test
    void 同じメールアドレスの同時の2つの登録は1つだけが通る() {
        // 行 9。先の登録は補助の都合で外側のトランザクションの中で呼ぶ（本番と違う。登録できた道は外側を rollback-only にしないので
        // 害はない）。後の登録は本番と同じく外側のトランザクションの外で呼ぶ
        EmailAddress email = newEmail();

        List<UserRegistrationOutcome> results = OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate,
                () -> service.register(admin, operator.id(), email, "先の 人", Set.of(Role.SALES)),
                () -> service.register(admin, operator.id(), email, "後の 人", Set.of(Role.ROUTE_DESIGNER)));

        assertThat(results.get(0)).isInstanceOf(UserRegistrationOutcome.Registered.class);
        assertThat(results.get(1))
                .isEqualTo(new UserRegistrationOutcome.Refused(new UserRegistrationRefusal.DuplicateEmail()));
        assertThat(usersWith(email)).isOne();
        UserId registered = ((UserRegistrationOutcome.Registered) results.get(0)).userId();
        assertThat(auditsOf(registered.value().toString())).hasSize(1);
    }
}

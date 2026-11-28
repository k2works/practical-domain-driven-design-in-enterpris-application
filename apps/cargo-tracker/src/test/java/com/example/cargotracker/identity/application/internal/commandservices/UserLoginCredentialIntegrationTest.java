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
import com.example.cargotracker.identity.domain.model.valueobjects.IdentityVerificationMethod;
import com.example.cargotracker.identity.domain.model.valueobjects.LoginCredentialRejection;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.sql.Timestamp;
import java.time.Duration;
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
 * ログインの資格の管理のサービス（Bolt 38c。US-18 AC3・AC9）。本物の PostgreSQL で、ロック中を条件にした解除、確認した登録の時刻を
 * 条件にした資格の削除と回復コードの削除と監査を 1 つのトランザクションで書くこと、同時の 2 つの解除で 1 つだけが書くことを確かめる
 * （行 3・4・6・8a・8b・10）。共有の表は利用者で絞る（T-113）。
 */
@IdentityIntegrationTest
class UserLoginCredentialIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:40:00Z");
    private static final Instant ENROLLED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final UtcInstant VERIFIED_AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));

    @Autowired
    UserLoginCredentialService service;

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

    /** ロックの記録を置く（期限は今からの差）。 */
    private void lock(UserId user, Duration untilFromNow) {
        jdbc.update(
                "INSERT INTO identity.login_failure (user_id, consecutive_failures, locked_until, updated_at)"
                        + " VALUES (?, 5, ?, ?)",
                user.value(),
                Timestamp.from(NOW.plus(untilFromNow)),
                Timestamp.from(NOW));
    }

    /** 認証アプリの登録と回復コード 2 つ（1 つは使用済み）を置く。 */
    private void enroll(UserId user, Instant enrolledAt) {
        jdbc.update(
                "INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at)"
                        + " VALUES (?, 'ciphertext', 0, ?)",
                user.value(),
                Timestamp.from(enrolledAt));
        jdbc.update(
                "INSERT INTO identity.recovery_code (id, user_id, code_hash, used_at, issued_at) VALUES (?, ?, 'h1', NULL, ?),"
                        + " (?, ?, 'h2', ?, ?)",
                UUID.randomUUID(),
                user.value(),
                Timestamp.from(enrolledAt),
                UUID.randomUUID(),
                user.value(),
                Timestamp.from(enrolledAt),
                Timestamp.from(enrolledAt));
    }

    private Map<String, Object> failureOf(UserId user) {
        return jdbc.queryForMap(
                "SELECT consecutive_failures, locked_until FROM identity.login_failure WHERE user_id = ?",
                user.value());
    }

    private int countOf(String table, UserId user) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, user.value());
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> auditsOf(UserId target) {
        return jdbc.queryForList(
                "SELECT actor_user_id, action, result, reason, target_type, target_version, before_state, after_state"
                        + " FROM identity.audit_record WHERE target_id = ? ORDER BY occurred_at, action",
                target.value().toString());
    }

    private TotpResetOutcome reset(UserId sales, Instant expectedEnrolledAt) {
        return service.resetTotp(
                admin,
                sales,
                new UtcInstant(expectedEnrolledAt),
                IdentityVerificationMethod.COMPANY_ADMINISTRATOR,
                VERIFIED_AT);
    }

    @Test
    void ロック中の利用者のロックを解除して0回にし監査を1行書き登録は変えない() {
        UserId sales = newSales();
        lock(sales, Duration.ofMinutes(5));
        enroll(sales, ENROLLED_AT);

        assertThat(service.unlock(admin, sales)).isEqualTo(new UnlockOutcome.Unlocked());

        assertThat(failureOf(sales)).containsEntry("consecutive_failures", 0).containsEntry("locked_until", null);
        assertThat(countOf("identity.totp_credential", sales)).isOne();
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("actor_user_id", admin.userId().value())
                        .containsEntry("action", "ACCOUNT_UNLOCKED")
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("reason", null)
                        .containsEntry("target_type", "USER")
                        .containsEntry("target_version", "0")
                        .containsEntry("before_state", "LOCKED")
                        .containsEntry("after_state", "UNLOCKED"));
    }

    @Test
    void 確定の前に期限が切れたロックは既に解除されているとして何も書かない() {
        UserId sales = newSales();
        lock(sales, Duration.ofMinutes(5));
        clock.setInstant(NOW.plus(Duration.ofMinutes(5)));

        assertThat(service.unlock(admin, sales))
                .isEqualTo(new UnlockOutcome.Refused(
                        new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.NOT_LOCKED)));

        assertThat(failureOf(sales)).containsEntry("consecutive_failures", 5);
        assertThat(auditsOf(sales)).isEmpty();
    }

    @Test
    void 確認した登録の資格と回復コードを消し監査を1行書きロックは変えない() {
        UserId sales = newSales();
        lock(sales, Duration.ofMinutes(5));
        enroll(sales, ENROLLED_AT);

        assertThat(reset(sales, ENROLLED_AT)).isEqualTo(new TotpResetOutcome.Reset());

        assertThat(countOf("identity.totp_credential", sales)).isZero();
        assertThat(countOf("identity.recovery_code", sales)).isZero();
        assertThat(failureOf(sales)).containsEntry("consecutive_failures", 5);
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("action", "TOTP_RESET_BY_ADMIN")
                        .containsEntry("result", "SUCCESS")
                        .containsEntry("reason", "IDENTITY_VERIFIED")
                        .containsEntry("target_version", "0")
                        .containsEntry("before_state", "ENROLLED")
                        .containsEntry("after_state", "method=COMPANY_ADMINISTRATOR;verifiedAt=2026-11-09T01:30:00Z"));
    }

    @Test
    void 先に解除された登録は既に解除されているとして何も書かない() {
        UserId sales = newSales();
        enroll(sales, ENROLLED_AT);
        reset(sales, ENROLLED_AT);

        assertThat(reset(sales, ENROLLED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(
                        new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.NOT_ENROLLED)));
        assertThat(auditsOf(sales)).hasSize(1);
    }

    @Test
    void 確認の後に登録し直した資格と回復コードは消さず何も書かない() {
        UserId sales = newSales();
        Instant renewed = NOW.minusSeconds(30);
        enroll(sales, renewed);

        assertThat(reset(sales, ENROLLED_AT))
                .isEqualTo(new TotpResetOutcome.Refused(
                        new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.ENROLLMENT_CHANGED)));

        assertThat(countOf("identity.totp_credential", sales)).isOne();
        assertThat(countOf("identity.recovery_code", sales)).isEqualTo(2);
        assertThat(auditsOf(sales)).isEmpty();
    }

    @Test
    void 同時の2つのロックの解除は1つだけが書き後は既に解除されている() {
        UserId sales = newSales();
        lock(sales, Duration.ofMinutes(5));

        List<UnlockOutcome> results = OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate, () -> service.unlock(admin, sales), () -> service.unlock(admin, sales));

        assertThat(results)
                .containsExactly(
                        new UnlockOutcome.Unlocked(),
                        new UnlockOutcome.Refused(
                                new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.NOT_LOCKED)));
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("action", "ACCOUNT_UNLOCKED"));
    }

    @Test
    void 同時の2つのTOTPの解除は1つだけが書き後は既に解除されている() {
        UserId sales = newSales();
        enroll(sales, ENROLLED_AT);

        List<TotpResetOutcome> results = OverlappingTransactions.runSecondOutsideTransaction(
                transactionTemplate, () -> reset(sales, ENROLLED_AT), () -> reset(sales, ENROLLED_AT));

        assertThat(results)
                .containsExactly(
                        new TotpResetOutcome.Reset(),
                        new TotpResetOutcome.Refused(
                                new UserAdministrationRefusal.Rejected<>(LoginCredentialRejection.NOT_ENROLLED)));
        assertThat(auditsOf(sales))
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("action", "TOTP_RESET_BY_ADMIN"));
    }
}

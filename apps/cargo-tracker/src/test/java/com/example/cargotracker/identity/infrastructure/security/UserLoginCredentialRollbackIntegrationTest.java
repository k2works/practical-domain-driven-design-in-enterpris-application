package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.application.internal.commandservices.UserLoginCredentialService;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.IdentityVerificationMethod;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ロックの解除と TOTP の解除で監査を書けなければ、ロック・資格・回復コードが元のままであることを確かめる（Bolt 38c。行 13。IA-INV-08 の
 * 同期の書き方。監査の書き込みを spy で失敗させる。単体テストの偽物は戻らないので、PostgreSQL で確かめる）。Bolt 34d の前例と同じく、spy
 * の基底を継ぐためこのパッケージに置く。
 */
class UserLoginCredentialRollbackIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final Instant ENROLLED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-09T01:40:00Z");

    @Autowired
    MutableClock clock;

    @Autowired
    UserLoginCredentialService service;

    @Autowired
    JdbcTemplate jdbc;

    private UserId sales;
    private AuthenticatedActor admin;

    @BeforeEach
    void ロックされ登録済みの営業担当者と管理者を置く() {
        Company operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        sales = new UserId(UUID.randomUUID());
        users.add(User.of(
                sales,
                operator.id(),
                EmailAddress.of("rollback-" + sales.value() + "@example.com"),
                "営業 一郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SALES)));
        UserId adminId = new UserId(UUID.randomUUID());
        users.add(User.of(
                adminId,
                operator.id(),
                EmailAddress.of("admin-" + adminId.value() + "@example.com"),
                "管理 次郎",
                "{noop}unused",
                UserStatus.ACTIVE,
                Set.of(Role.SYSTEM_ADMIN)));
        AdministrationFixtures.assignCompany(jdbc, adminId, operator.id());
        admin = new AuthenticatedActor(adminId, operator.id(), Set.of(Role.SYSTEM_ADMIN), "管理 次郎", "A 社");
        clock.setInstant(NOW);
        Instant now = NOW;
        jdbc.update(
                "INSERT INTO identity.login_failure (user_id, consecutive_failures, locked_until, updated_at)"
                        + " VALUES (?, 5, ?, ?)",
                sales.value(),
                Timestamp.from(now.plus(Duration.ofHours(1))),
                Timestamp.from(now));
        jdbc.update(
                "INSERT INTO identity.totp_credential (user_id, secret_encrypted, last_used_step, enrolled_at)"
                        + " VALUES (?, 'ciphertext', 0, ?)",
                sales.value(),
                Timestamp.from(ENROLLED_AT));
        jdbc.update(
                "INSERT INTO identity.recovery_code (id, user_id, code_hash, used_at, issued_at) VALUES (?, ?, 'h', NULL, ?)",
                UUID.randomUUID(),
                sales.value(),
                Timestamp.from(ENROLLED_AT));
    }

    private int countOf(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, sales.value());
        return count == null ? 0 : count;
    }

    @Test
    void ロックの解除で監査を書けなければロックは元のまま() {
        doThrow(new IllegalStateException("監査の表に届かない"))
                .when(auditRecords)
                .append(argThat(written -> written.action() == AuditAction.ACCOUNT_UNLOCKED));

        assertThatThrownBy(() -> service.unlock(admin, sales)).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                        "SELECT consecutive_failures FROM identity.login_failure WHERE user_id = ?",
                        Integer.class,
                        sales.value()))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                        "SELECT locked_until IS NOT NULL FROM identity.login_failure WHERE user_id = ?",
                        Boolean.class,
                        sales.value()))
                .as("ロックの期限も元のまま")
                .isTrue();
    }

    @Test
    void TOTPの解除で監査を書けなければ資格と回復コードは元のまま() {
        doThrow(new IllegalStateException("監査の表に届かない"))
                .when(auditRecords)
                .append(argThat(written -> written.action() == AuditAction.TOTP_RESET_BY_ADMIN));
        UtcInstant enrolledAt = new UtcInstant(ENROLLED_AT);
        UtcInstant verifiedAt = new UtcInstant(NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.resetTotp(
                        admin, sales, enrolledAt, IdentityVerificationMethod.PHONE_CALLBACK, verifiedAt))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countOf("identity.totp_credential")).isOne();
        assertThat(countOf("identity.recovery_code")).isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE target_id = ?",
                        Integer.class,
                        sales.value().toString()))
                .as("監査の行も残らない")
                .isZero();
    }
}

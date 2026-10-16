package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 管理範囲外の試行の記録のサービス（Bolt 34b。US-16 AC2、IA-INV-08・IA-INV-14）。本物の PostgreSQL で、試行が監査記録に 1 行、
 * 対象と理由とともに残ることを確かめる。共有の表は対象の利用者で絞る（T-113）。
 */
@IdentityIntegrationTest
class AdministrationScopeDenialIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-16T09:00:00Z");

    @Autowired
    AdministrationScopeDenialService service;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void 時計を合わせる() {
        clock.setInstant(NOW);
    }

    @Test
    void 範囲外の試行を対象と理由とともに失敗として1行書く() {
        AuthenticatedActor admin = new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.SYSTEM_ADMIN),
                "管理 次郎",
                "A 社");
        UserId target = new UserId(UUID.randomUUID());

        service.recordDenied(admin, target);

        assertThat(jdbc.queryForList(
                        "SELECT actor_user_id, actor_company_id, action, result, reason, target_type, target_version,"
                                + " before_state FROM identity.audit_record WHERE target_id = ?",
                        target.value().toString()))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("actor_user_id", admin.userId().value())
                        .containsEntry("actor_company_id", admin.companyId().value())
                        .containsEntry("action", "ADMIN_SCOPE_DENIED")
                        .containsEntry("result", "FAILURE")
                        .containsEntry("reason", "OUT_OF_SCOPE")
                        .containsEntry("target_type", "USER")
                        .containsEntry("target_version", null)
                        .containsEntry("before_state", null));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE target_id = ? AND occurred_at = ?",
                        Integer.class,
                        target.value().toString(),
                        OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC)))
                .as("時計の時刻で書く")
                .isEqualTo(1);
    }
}

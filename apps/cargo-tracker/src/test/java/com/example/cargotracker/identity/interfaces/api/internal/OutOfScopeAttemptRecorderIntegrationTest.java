package com.example.cargotracker.identity.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 担当範囲の外の試行の記録のポート（Bolt 38f の行 2c・8。ADR-019、IA-INV-08）。本物の PostgreSQL で、ポートの実装が監査記録に 1 行を
 * 全列で書くことと、外側のトランザクションの中では呼べず外側のロールバックの後に何も残らないことを確かめる。共有の表は対象で絞る（T-113）。
 */
@IdentityIntegrationTest
class OutOfScopeAttemptRecorderIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:30:00Z");

    @Autowired
    OutOfScopeAttemptRecorder recorder;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private final AuthenticatedActor sales = new AuthenticatedActor(
            new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(Role.SALES), "営業 一郎", "A 社");

    @BeforeEach
    void 時計を合わせる() {
        clock.setInstant(NOW);
    }

    @Test
    void ポートは担当範囲の外の試行を全列で1行書く() {
        String resourceId = "TR-" + UUID.randomUUID();

        recorder.recordOutOfScope(sales, ScopedResourceKind.TRANSPORT_REQUEST, resourceId);

        assertThat(jdbc.queryForList(
                        "SELECT actor_user_id, actor_company_id, action, result, reason, target_type, target_version,"
                                + " before_state, after_state FROM identity.audit_record WHERE target_id = ?",
                        resourceId))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("actor_user_id", sales.userId().value())
                        .containsEntry("actor_company_id", sales.companyId().value())
                        .containsEntry("action", "ASSIGNED_SCOPE_DENIED")
                        .containsEntry("result", "FAILURE")
                        .containsEntry("reason", "OUT_OF_SCOPE")
                        .containsEntry("target_type", "TRANSPORT_REQUEST")
                        .containsEntry("target_version", null)
                        .containsEntry("before_state", null)
                        .containsEntry("after_state", null));
    }

    @Test
    void 外側のトランザクションの中では呼べず外側のロールバックの後に何も残らない() {
        String resourceId = "TR-" + UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                        status -> recorder.recordOutOfScope(sales, ScopedResourceKind.TRANSPORT_REQUEST, resourceId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE target_id = ?", Integer.class, resourceId))
                .isZero();
    }
}

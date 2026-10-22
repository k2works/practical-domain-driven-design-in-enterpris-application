package com.example.cargotracker.routing.domain.model.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 経路版の再設計要の記録（US-07 AC4 の準備、R-INV-07。Bolt 37d）。記録を持てるのは再設計要と旧版だけで、再設計要は必ず持つ。
 */
class RouteVersionTest {

    static final UtcInstant EVALUATED_AT = at("2026-10-07T03:00:00Z");
    static final UtcInstant APPROVED_AT = at("2026-10-07T05:00:00Z");
    static final RedesignRecord RECORD =
            new RedesignRecord(at("2026-10-09T00:00:00Z"), List.of(RedesignReasonCode.DEADLINE_EXCEEDED));
    static final RouteConfirmation CONFIRMATION =
            new RouteConfirmation(1, new DecisionRationale("直行で期限まで 2 日ある。"), UUID.randomUUID(), APPROVED_AT);

    @Test
    void 再設計要の経路版は確定の記録と再設計要の記録を持つ() {
        RouteVersion version = version(RouteVersionStatus.REDESIGN_REQUIRED, CONFIRMATION, RECORD);

        assertThat(version.redesign()).contains(RECORD);
        assertThat(version.confirmation()).contains(CONFIRMATION);
    }

    @Test
    void 再設計要の経路版は再設計要の記録がなければ作れない() {
        assertThatThrownBy(() -> version(RouteVersionStatus.REDESIGN_REQUIRED, CONFIRMATION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} の経路版は再設計要の記録を持てない")
    @EnumSource(
            value = RouteVersionStatus.class,
            names = {"CANDIDATES_PRESENTED", "EXPERT_REVIEW", "CONFIRMED"})
    void 再設計要と旧版のほかは再設計要の記録を持てない(RouteVersionStatus status) {
        RouteConfirmation confirmation = status == RouteVersionStatus.CONFIRMED ? CONFIRMATION : null;

        assertThatThrownBy(() -> version(status, confirmation, RECORD)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 旧版は再設計要の記録を持っても持たなくてもよい() {
        assertThat(version(RouteVersionStatus.SUPERSEDED, CONFIRMATION, RECORD).redesign())
                .contains(RECORD);
        assertThat(version(RouteVersionStatus.SUPERSEDED, CONFIRMATION, null).redesign())
                .isEmpty();
    }

    @Test
    void 確定していない経路版と確定の経路版は再設計要の記録を持たない() {
        assertThat(version(RouteVersionStatus.CANDIDATES_PRESENTED, null, null).redesign())
                .isEmpty();
        assertThat(version(RouteVersionStatus.CONFIRMED, CONFIRMATION, null).redesign())
                .isEmpty();
    }

    static RouteVersion version(RouteVersionStatus status, RouteConfirmation confirmation, RedesignRecord redesign) {
        return new RouteVersion(1, status, List.of(candidate()), EVALUATED_AT, 1, confirmation, redesign);
    }

    static RouteCandidate candidate() {
        Leg leg = new Leg(
                "V-101",
                new Location("JPTYO"),
                new Location("NLRTM"),
                at("2026-10-12T00:00:00Z"),
                at("2026-10-30T09:00:00Z"),
                "V-101@1",
                at("2026-10-07T01:00:00Z"));
        return new RouteCandidate(
                1, List.of(leg), new ConstraintEvaluation(at("2026-10-30T09:00:00Z"), List.of(), null), EVALUATED_AT);
    }

    static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

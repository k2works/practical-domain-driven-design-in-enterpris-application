package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.events.RouteCandidatesNotFound;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidatesNotFoundReason;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 適合する候補がないときの専門判断への引き継ぎの判定（UC-06 3a、DE-32。Bolt 40-14）。 */
class RoutingCaseCandidatesNotFoundTest {

    private static final UserId DESIGNER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040e1401"));
    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-10-07T03:00:00Z"));

    private static RoutingCase calculate(
            List<com.example.cargotracker.routing.domain.model.aggregates.Voyage> voyages) {
        RoutingCase routingCase = RoutingCaseTest.open();
        routingCase.calculateCandidates(
                voyages,
                List.of(RoutingCaseConfirmationTest.SINGAPORE_8H),
                RoutingCaseConfirmationTest.JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        return routingCase;
    }

    @Test
    void 行1_経路に使える航海がなければ外部データの欠落として引き継ぐ() {
        RoutingCase routingCase = calculate(List.of());

        assertThat(routingCase.candidatesNotFoundReason()).contains(CandidatesNotFoundReason.EXTERNAL_DATA_MISSING);
        Optional<RouteCandidatesNotFound> event = routingCase.candidatesNotFound(DESIGNER, AT);
        assertThat(event).isPresent();
        assertThat(event.get().reason()).isEqualTo("EXTERNAL_DATA_MISSING");
        assertThat(event.get().routingCaseId()).isEqualTo(routingCase.id().value());
        assertThat(event.get().caseNumber()).isEqualTo(routingCase.number().text());
        assertThat(event.get().shipperCompanyId()).isEqualTo(routingCase.shipperCompanyId());
        assertThat(event.get().candidatesFound()).isZero();
        assertThat(event.get().operatorUserId()).isEqualTo(DESIGNER);
        assertThat(event.get().notFoundAt()).isEqualTo(AT);
    }

    @Test
    void 行2_候補はあるがすべて除外なら基本制約を満たす候補なしとして引き継ぐ() {
        // 希望到着期限（2026-11-02 前後）より遅く着く航海だけ
        RoutingCase routingCase = calculate(List.of(RoutingCaseTest.direct("V-LATE", "2026-11-03T12:00:00Z")));

        assertThat(routingCase.candidatesNotFoundReason()).contains(CandidatesNotFoundReason.NO_CONFORMING_CANDIDATE);
        assertThat(routingCase.candidatesNotFound(DESIGNER, AT)).hasValueSatisfying(event -> {
            assertThat(event.reason()).isEqualTo("NO_CONFORMING_CANDIDATE");
            assertThat(event.candidatesFound()).isEqualTo(1);
        });
    }

    @Test
    void 行3_適合する候補が1件でもあれば引き継がない() {
        RoutingCase routingCase = calculate(List.of(
                RoutingCaseTest.direct("V-LATE", "2026-11-03T12:00:00Z"),
                RoutingCaseTest.direct("V-EARLY", "2026-10-29T00:00:00Z")));

        assertThat(routingCase.candidatesNotFoundReason()).isEmpty();
        assertThat(routingCase.candidatesNotFound(DESIGNER, AT)).isEmpty();
    }

    @Test
    void 行4_まだ算出していない案件は引き継がない() {
        RoutingCase routingCase = RoutingCaseTest.open();

        assertThat(routingCase.candidatesNotFoundReason()).isEmpty();
        assertThat(routingCase.candidatesNotFound(DESIGNER, AT)).isEmpty();
    }
}

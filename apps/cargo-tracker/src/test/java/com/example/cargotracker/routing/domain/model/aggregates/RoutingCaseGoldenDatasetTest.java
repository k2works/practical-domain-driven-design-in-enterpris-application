package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden dataset v0 の集約の単位のテスト（US-06、Bolt 17 の開発レビュー D-65。Bolt 37f）。候補の並びと上限は集約にあるので、代表案件を
 * {@link RoutingCase#calculateCandidates} に直接かけ、候補一覧の全体（並び、判定、到着予定、接続余裕、除外の理由）を案件のデータの
 * 期待結果と比べる。受入シナリオ（route_candidates_golden_v0.feature）は区分ごとの要約を確かめる。値は仮の値（業務責任者の承認待ち）。
 */
class RoutingCaseGoldenDatasetTest {

    private final RouteCandidateFinder finder = new RouteCandidateFinder();
    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();
    private final InfoFreshnessPolicy freshness = new InfoFreshnessPolicy(Duration.ofHours(24));

    static List<String> cases() {
        return GoldenDatasetCase.V0_CASES;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void 代表案件の候補一覧が期待結果と一致する(String id) {
        GoldenDatasetCase golden = GoldenDatasetCase.load(id);
        RoutingCase routingCase = open(golden);

        routingCase.calculateCandidates(
                golden.voyages(), golden.rules(), golden.judgedAt(), finder, evaluator, freshness);

        List<RouteCandidate> candidates = routingCase.routeVersion().candidates();
        assertThat(candidates)
                .extracting(RoutingCaseGoldenDatasetTest::actualOf)
                .containsExactlyElementsOf(golden.expected());
        assertThat(candidates)
                .extracting(RouteCandidate::candidateNo)
                .containsExactlyElementsOf(numbers(candidates.size()));
    }

    private static GoldenDatasetCase.ExpectedCandidate actualOf(RouteCandidate candidate) {
        return new GoldenDatasetCase.ExpectedCandidate(
                candidate.legs().stream().map(Leg::voyageNumber).collect(Collectors.joining(",")),
                candidate.evaluation().conforming(),
                candidate.evaluation().estimatedArrivalAt(),
                candidate.evaluation().connectionSlack().orElse(null),
                candidate.evaluation().reasons().stream()
                        .map(ExclusionReason::code)
                        .toList());
    }

    private static List<Integer> numbers(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count).boxed().toList();
    }

    private static RoutingCase open(GoldenDatasetCase golden) {
        return RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                new RoutingCaseNumber(2026, 1),
                UUID.randomUUID(),
                "TR-2026-0001",
                1,
                UUID.randomUUID(),
                List.of(new Location("SGSIN")),
                golden.routeSpecification(),
                at("2026-10-06T02:00:00Z"),
                at("2026-10-08T09:00:00Z"),
                UUID.randomUUID());
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}

package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import java.time.Duration;
import java.util.List;

/** ほかのパッケージのテストで使う経路設計案件の見本（Bolt 20）。 */
public final class RoutingCaseFixture {

    private RoutingCaseFixture() {}

    /**
     * 直行の航海 V-EARLY の候補 1 を確定した案件（RC-2026-0001 経路版 1）と、確定で生成した DE-05。
     *
     * @param routingCase 確定した案件
     * @param event DE-05
     */
    public record Confirmed(RoutingCase routingCase, RouteConfirmed event) {}

    /** 直行の航海 V-EARLY の候補を算出し、まだ確定していない案件（RC-2026-0001 経路版 1。Bolt 25）。 */
    public static RoutingCase calculated() {
        RoutingCase routingCase = RoutingCaseTest.open();
        routingCase.calculateCandidates(
                List.of(RoutingCaseTest.direct("V-EARLY", "2026-10-29T00:00:00Z")),
                List.of(RoutingCaseConfirmationTest.SINGAPORE_8H),
                RoutingCaseConfirmationTest.JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        return routingCase;
    }

    /**
     * 案件の経路版を入れ替えて組み立て直す（保存されている状態からの再構成。再設計要・旧版の経路版を作るのに使う。Bolt 37d）。
     *
     * @param source 元の案件
     * @param routeVersions 経路版の一覧
     * @return 経路版を入れ替えた案件
     */
    public static RoutingCase withRouteVersions(RoutingCase source, List<RouteVersion> routeVersions) {
        return RoutingCase.reconstitute(
                source.id(),
                source.number(),
                source.transportRequestId(),
                source.transportRequestNumber(),
                source.transportRequestVersionNo(),
                source.quotationId(),
                source.routePolicyVia(),
                source.specification(),
                source.requestedAt(),
                source.quotationExpiresAt().orElse(null),
                source.requestedBy().orElse(null),
                routeVersions,
                source.aggregateVersion());
    }

    /** 確定した経路版を再設計要にした案件（確定の記録と候補はそのまま。Bolt 37d）。 */
    public static RoutingCase redesignRequired(RedesignRecord record) {
        RoutingCase confirmed = confirmed().routingCase();
        RouteVersion version = confirmed.routeVersion();
        return withRouteVersions(
                confirmed,
                List.of(new RouteVersion(
                        version.routeVersionNo(),
                        RouteVersionStatus.REDESIGN_REQUIRED,
                        version.candidates(),
                        version.evaluatedAt().orElseThrow(),
                        version.candidatesFound(),
                        version.confirmation().orElseThrow(),
                        record)));
    }

    public static Confirmed confirmed() {
        RoutingCase routingCase = RoutingCaseTest.open();
        ConstraintEvaluator evaluator = new ConstraintEvaluator();
        routingCase.calculateCandidates(
                List.of(RoutingCaseTest.direct("V-EARLY", "2026-10-29T00:00:00Z")),
                List.of(RoutingCaseConfirmationTest.SINGAPORE_8H),
                RoutingCaseConfirmationTest.JUDGED_AT,
                new RouteCandidateFinder(),
                evaluator,
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        RouteConfirmed event = routingCase.confirm(
                1,
                "直行で期限まで 4 日ある。",
                RoutingCaseConfirmationTest.ROUTE_DESIGNER,
                List.of(RoutingCaseTest.direct("V-EARLY", "2026-10-29T00:00:00Z")),
                List.of(RoutingCaseConfirmationTest.SINGAPORE_8H),
                RoutingCaseConfirmationTest.COMMIT_AT,
                evaluator,
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        return new Confirmed(routingCase, event);
    }
}

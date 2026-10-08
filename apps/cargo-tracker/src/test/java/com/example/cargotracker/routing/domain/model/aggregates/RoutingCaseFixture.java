package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
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

    public static Confirmed confirmed() {
        RoutingCase routingCase = RoutingCaseTest.open();
        ConstraintEvaluator evaluator = new ConstraintEvaluator();
        routingCase.calculateCandidates(
                List.of(RoutingCaseTest.direct("V-EARLY", "2026-10-29T00:00:00Z")),
                List.of(RoutingCaseConfirmationTest.SINGAPORE_8H),
                RoutingCaseConfirmationTest.JUDGED_AT,
                new RouteCandidateFinder(),
                evaluator);
        RouteConfirmed event = routingCase.confirm(
                1,
                "直行で期限まで 4 日ある。",
                RoutingCaseConfirmationTest.ROUTE_DESIGNER,
                List.of(RoutingCaseConfirmationTest.SINGAPORE_8H),
                RoutingCaseConfirmationTest.COMMIT_AT,
                evaluator);
        return new Confirmed(routingCase, event);
    }
}

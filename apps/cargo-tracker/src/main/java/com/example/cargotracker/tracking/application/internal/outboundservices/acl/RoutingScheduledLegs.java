package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import com.example.cargotracker.routing.interfaces.api.RouteVersionLegQuery;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import java.util.List;

/**
 * 腐敗防止層。経路設計の公開 API（確定した経路版の区間）の型を、追跡の予定区間に変え、この層の外に出さない（ADR-015、units.md の
 * U3 → U2。Bolt 25）。
 */
public class RoutingScheduledLegs {

    private final RouteVersionLegQuery routeVersionLegQuery;

    public RoutingScheduledLegs(RouteVersionLegQuery routeVersionLegQuery) {
        this.routeVersionLegQuery = routeVersionLegQuery;
    }

    /**
     * 確定した経路版の区間を、予定区間として区間の順に返す。
     *
     * @param routingCaseNumber 案件番号の表記
     * @param routeVersionNo 経路版番号
     * @return 予定区間の列。案件・経路版がない、または確定していない経路版なら空
     */
    public List<ScheduledLeg> confirmedLegsOf(String routingCaseNumber, int routeVersionNo) {
        return routeVersionLegQuery.confirmedLegsOf(routingCaseNumber, routeVersionNo).stream()
                .map(leg -> new ScheduledLeg(
                        leg.voyageNumber(), leg.load(), leg.discharge(), leg.departureAt(), leg.arrivalAt()))
                .toList();
    }
}

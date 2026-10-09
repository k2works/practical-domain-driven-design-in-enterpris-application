package com.example.cargotracker.routing.interfaces.api.internal;

import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.interfaces.api.RouteVersionLeg;
import com.example.cargotracker.routing.interfaces.api.RouteVersionLegQuery;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 経路設計の公開 API（確定した経路版の区間）のインバウンドアダプター（ADR-015。Bolt 25）。照会のアプリケーションサービスに委ね、区間を公開 API
 * の型に変える（航海の採用情報版と取得時刻は渡さない）。案件番号の形でない表記は、ない案件と同じく空を返す。
 */
@Component
public class RouteVersionLegQueryAdapter implements RouteVersionLegQuery {

    private final RoutingCaseQueryService queryService;

    public RouteVersionLegQueryAdapter(RoutingCaseQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public List<RouteVersionLeg> confirmedLegsOf(String routingCaseNumber, int routeVersionNo) {
        RoutingCaseNumber number;
        try {
            number = RoutingCaseNumber.parse(routingCaseNumber);
        } catch (IllegalArgumentException notACaseNumber) {
            return List.of();
        }
        return queryService.confirmedLegs(number, routeVersionNo).stream()
                .map(leg -> new RouteVersionLeg(
                        leg.voyageNumber(), leg.load(), leg.discharge(), leg.departureAt(), leg.arrivalAt()))
                .toList();
    }
}

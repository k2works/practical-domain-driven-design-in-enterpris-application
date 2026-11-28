package com.example.cargotracker.quotation.interfaces.api.internal;

import com.example.cargotracker.quotation.application.internal.commandservices.RouteAssignmentService;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignment;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentRequest;
import org.springframework.stereotype.Component;

/**
 * 見積りの公開 API（経路の割当て）のインバウンドアダプター（ADR-014。Bolt 20 の実装を 2026-10-09 に interfaces.api へ移した）。
 * 依頼を割り当てた経路に変えて入力ポートに委ね、結果を公開 API の受領の型に変える。不正な依頼は再配信しても変わらないため、
 * 例外にして listener を戻さず、理由で返す（Bolt 20 レビュー）。理由はドメインの値の名前ではなく公開 API の定数で返す。
 */
@Component
public class RouteAssignmentAdapter implements RouteAssignment {

    private final RouteAssignmentService service;

    public RouteAssignmentAdapter(RouteAssignmentService service) {
        this.service = service;
    }

    @Override
    public RouteAssignmentReceipt assign(RouteAssignmentRequest request) {
        AssignedRoute route;
        try {
            route = toAssignedRoute(request);
        } catch (IllegalArgumentException _) {
            return new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.INVALID_REQUEST);
        }
        return service.assign(request.quotationId(), route)
                .<RouteAssignmentReceipt>map(result -> switch (result) {
                    case ASSIGNED -> new RouteAssignmentReceipt.Assigned();
                    case ALREADY_ASSIGNED -> new RouteAssignmentReceipt.AlreadyAssigned();
                    case ANOTHER_ROUTE_VERSION_ASSIGNED ->
                        new RouteAssignmentReceipt.NotAssigned(
                                RouteAssignmentReceipt.NotAssigned.ANOTHER_ROUTE_VERSION_ASSIGNED);
                    case NOT_ROUTING_REQUESTED ->
                        new RouteAssignmentReceipt.NotAssigned(
                                RouteAssignmentReceipt.NotAssigned.NOT_ROUTING_REQUESTED);
                    case RETIRED -> new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.RETIRED);
                })
                .orElseGet(() ->
                        new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.QUOTATION_NOT_FOUND));
    }

    private static AssignedRoute toAssignedRoute(RouteAssignmentRequest request) {
        return new AssignedRoute(
                request.routingCaseNumber(),
                request.routeVersionNo(),
                request.confirmedAt(),
                request.legs().stream()
                        .map(leg -> new AssignedRouteLeg(
                                leg.voyageNumber(), leg.load(), leg.discharge(), leg.departureAt(), leg.arrivalAt()))
                        .toList());
    }
}

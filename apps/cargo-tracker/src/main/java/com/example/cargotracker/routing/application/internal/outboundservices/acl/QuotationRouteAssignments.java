package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import com.example.cargotracker.quotation.interfaces.api.RouteAssignment;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentLeg;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentRequest;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 見積りの公開 API（経路の割当て）を呼ぶ腐敗防止層（ADR-014。Bolt 20）。経路設計の区間を公開 API の形に変え、結果を
 * 経路設計の言葉（割り当てなかった理由）にして返す。見積りの型（公開 API の引数と戻り値）は、この部品の外に出さない。
 */
public class QuotationRouteAssignments {

    private final RouteAssignment routeAssignment;

    public QuotationRouteAssignments(RouteAssignment routeAssignment) {
        this.routeAssignment = routeAssignment;
    }

    /**
     * 依頼元の見積りに確定した経路版を割り当てる。
     *
     * @return 割り当てなかった理由（割り当てたか、すでに同じ経路版を割り当てていたら空）
     */
    public Optional<String> assign(
            UUID quotationId, RoutingCaseNumber number, int routeVersionNo, UtcInstant confirmedAt, List<Leg> legs) {
        RouteAssignmentReceipt receipt = routeAssignment.assign(new RouteAssignmentRequest(
                quotationId,
                number.text(),
                routeVersionNo,
                confirmedAt,
                legs.stream()
                        .map(leg -> new RouteAssignmentLeg(
                                leg.voyageNumber(), leg.load(), leg.discharge(), leg.departureAt(), leg.arrivalAt()))
                        .toList()));
        return switch (receipt) {
            case RouteAssignmentReceipt.Assigned _, RouteAssignmentReceipt.AlreadyAssigned _ -> Optional.empty();
            case RouteAssignmentReceipt.NotAssigned(String reason) -> Optional.of(reason);
        };
    }
}

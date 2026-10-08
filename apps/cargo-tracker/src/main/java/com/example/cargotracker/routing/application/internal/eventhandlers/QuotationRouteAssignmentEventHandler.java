package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteAssignments;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-05 経路を確定した を経路設計の中で受け、見積りの公開 API で依頼元の見積りに経路版を割り当てる（R-INV-11、ADR-014。Bolt 20）。
 * 見積りは経路設計のイベントを購読しない（依存が循環する。Bolt 19 レビュー D-71）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class QuotationRouteAssignmentEventHandler {

    private final RoutingCaseRepository repository;
    private final QuotationRouteAssignments routeAssignments;

    public QuotationRouteAssignmentEventHandler(
            RoutingCaseRepository repository, QuotationRouteAssignments routeAssignments) {
        this.repository = repository;
        this.routeAssignments = routeAssignments;
    }

    @ApplicationModuleListener
    public void on(RouteConfirmed event) {
        // Bolt 20 ステップ 3 の Green で作る
    }
}

package com.example.cargotracker.routing.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteConditions;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-16 荷主が詳細経路設計を依頼した を購読し、経路設計案件を作る（R-INV-10、US-06。Bolt 17）。骨組み（ステップ 3 の Red）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class RoutingCaseOpeningEventHandler {

    private final RoutingCaseRepository repository;
    private final RoutingCaseNumberIssuer numberIssuer;
    private final QuotationRouteConditions routeConditions;

    public RoutingCaseOpeningEventHandler(
            RoutingCaseRepository repository,
            RoutingCaseNumberIssuer numberIssuer,
            QuotationRouteConditions routeConditions) {
        this.repository = repository;
        this.numberIssuer = numberIssuer;
        this.routeConditions = routeConditions;
    }

    @ApplicationModuleListener
    public void on(RouteDesignRequested event) {
        // 骨組み
    }
}

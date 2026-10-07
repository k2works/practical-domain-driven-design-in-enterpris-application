package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import com.example.cargotracker.quotation.api.RouteConditionQuery;
import java.util.Optional;
import java.util.UUID;

/**
 * 見積りの公開 API（経路条件の照会）を呼び、経路設計の経路条件に変える腐敗防止層（Bolt 17）。骨組み（ステップ 3 の Red）。
 */
public class QuotationRouteConditions {

    private final RouteConditionQuery query;

    public QuotationRouteConditions(RouteConditionQuery query) {
        this.query = query;
    }

    /** 輸送要求版の経路条件。輸送要求がないか、その版が現在の版でなければ空。 */
    public Optional<RoutingCaseConditions> find(UUID transportRequestId, int transportRequestVersionNo) {
        return Optional.empty();
    }
}

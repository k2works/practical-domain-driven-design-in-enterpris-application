package com.example.cargotracker.quotation.interfaces.api.internal;

import com.example.cargotracker.quotation.application.internal.queryservices.RouteConditionQueryService;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.interfaces.api.RouteConditionQuery;
import com.example.cargotracker.quotation.interfaces.api.RouteConditionView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 見積りの公開 API（経路条件の照会）のインバウンドアダプター（Bolt 17 の実装を 2026-10-09 に interfaces.api へ移した）。
 * 照会の入力ポートに委ね、輸送要求の現在の版の輸送条件を経路条件に変える（見積りのドメインの型を公開 API の外に出さない）。
 */
@Component
public class RouteConditionQueryAdapter implements RouteConditionQuery {

    private final RouteConditionQueryService service;

    public RouteConditionQueryAdapter(RouteConditionQueryService service) {
        this.service = service;
    }

    @Override
    public Optional<RouteConditionView> find(UUID transportRequestId, int transportRequestVersionNo) {
        return service.find(transportRequestId, transportRequestVersionNo).map(request -> {
            ShipmentTerms terms = request.currentVersion().terms();
            return new RouteConditionView(
                    request.number().text(),
                    terms.origin(),
                    terms.destination(),
                    terms.arrivalDeadline(),
                    terms.cargo().category().name());
        });
    }
}

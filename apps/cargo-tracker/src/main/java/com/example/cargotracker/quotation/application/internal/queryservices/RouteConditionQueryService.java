package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.api.RouteConditionQuery;
import com.example.cargotracker.quotation.api.RouteConditionView;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 経路条件の照会の実装（見積りの公開 API。Bolt 17）。骨組み（ステップ 3 の Red）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class RouteConditionQueryService implements RouteConditionQuery {

    private final TransportRequestRepository repository;

    public RouteConditionQueryService(TransportRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RouteConditionView> find(UUID transportRequestId, int transportRequestVersionNo) {
        return Optional.empty();
    }
}

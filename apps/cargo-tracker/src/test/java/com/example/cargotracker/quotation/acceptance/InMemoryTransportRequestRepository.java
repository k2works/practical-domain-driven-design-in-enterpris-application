package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の輸送要求リポジトリ。
 */
public class InMemoryTransportRequestRepository implements TransportRequestRepository {

    private final Map<TransportRequestId, TransportRequest> store = new ConcurrentHashMap<>();

    @Override
    public void save(TransportRequest transportRequest) {
        store.put(transportRequest.id(), transportRequest);
    }

    @Override
    public Optional<TransportRequest> findById(TransportRequestId id) {
        return Optional.ofNullable(store.get(id));
    }
}

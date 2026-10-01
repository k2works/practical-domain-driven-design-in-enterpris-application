package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の輸送要求リポジトリ。
 * 本物（INSERT）と同じく、同じ ID の輸送要求を 2 回保存すると失敗する。
 */
public class InMemoryTransportRequestRepository implements TransportRequestRepository {

    private final Map<TransportRequestId, TransportRequest> store = new ConcurrentHashMap<>();

    @Override
    public void save(TransportRequest transportRequest) {
        if (store.putIfAbsent(transportRequest.id(), transportRequest) != null) {
            throw new IllegalStateException("輸送要求は既に保存されています: " + transportRequest.id());
        }
    }

    @Override
    public Optional<TransportRequest> findById(TransportRequestId id) {
        return Optional.ofNullable(store.get(id));
    }

    /** シナリオの開始時に記録を消す。 */
    public void clear() {
        store.clear();
    }
}

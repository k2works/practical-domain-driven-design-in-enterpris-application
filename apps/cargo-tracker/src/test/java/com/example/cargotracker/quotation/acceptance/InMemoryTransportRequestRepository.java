package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
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

    @Override
    public Optional<TransportRequest> findByNumber(TransportRequestNumber number) {
        return store.values().stream()
                .filter(request -> request.number().equals(number))
                .findFirst();
    }

    /** 保存した輸送要求の件数。提出を受け付けなかったときに何も保存していないことを確かめる。 */
    public int count() {
        return store.size();
    }

    /** シナリオの開始時に記録を消す。 */
    public void clear() {
        store.clear();
    }
}

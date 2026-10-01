package com.example.cargotracker.identity.acceptance;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の KPI 計測記録リポジトリ。
 */
public class InMemoryKpiObservationRepository implements KpiObservationRepository {

    private final Map<UUID, KpiObservation> store = new ConcurrentHashMap<>();

    @Override
    public void save(KpiObservation observation) {
        store.putIfAbsent(observation.transportRequestId(), observation);
    }

    @Override
    public Optional<KpiObservation> findByTransportRequestId(UUID transportRequestId) {
        return Optional.ofNullable(store.get(transportRequestId));
    }

    @Override
    public List<KpiObservation> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing((KpiObservation o) -> o.submittedAt().instant()).reversed())
                .toList();
    }

    /** シナリオの開始時に記録を消す。 */
    public void clear() {
        store.clear();
    }
}

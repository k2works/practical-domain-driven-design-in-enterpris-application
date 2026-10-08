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
 * MyBatis と同じく、読み出しは写しを返し、保存しなければ変わらない（Bolt 21 の開発レビュー D-80）。
 */
public class InMemoryKpiObservationRepository implements KpiObservationRepository {

    private final Map<UUID, KpiObservation> store = new ConcurrentHashMap<>();

    @Override
    public void save(KpiObservation observation) {
        store.putIfAbsent(observation.transportRequestId(), copyOf(observation));
    }

    @Override
    public void saveFirstPresentation(KpiObservation observation) {
        // MyBatis の条件付きの保存と同じく、まだないか早いときだけ書く（提出時刻より前は表の制約の代わりに集約が拒否する）
        observation
                .firstPresentedAt()
                .ifPresent(presentedAt -> store.computeIfPresent(observation.transportRequestId(), (id, stored) -> {
                    KpiObservation copy = copyOf(stored);
                    copy.recordPresentation(presentedAt);
                    return copy;
                }));
    }

    @Override
    public Optional<KpiObservation> findByTransportRequestId(UUID transportRequestId) {
        return Optional.ofNullable(store.get(transportRequestId)).map(InMemoryKpiObservationRepository::copyOf);
    }

    @Override
    public List<KpiObservation> findAll() {
        return store.values().stream()
                .map(InMemoryKpiObservationRepository::copyOf)
                .sorted(Comparator.comparing(
                                (KpiObservation o) -> o.submittedAt().instant())
                        .reversed())
                .toList();
    }

    /** シナリオの開始時に記録を消す。 */
    public void clear() {
        store.clear();
    }

    private static KpiObservation copyOf(KpiObservation observation) {
        return KpiObservation.reconstitute(
                observation.transportRequestId(),
                observation.transportRequestNumber(),
                observation.shipperCompanyId(),
                observation.submittedAt(),
                observation.firstPresentedAt().orElse(null));
    }
}

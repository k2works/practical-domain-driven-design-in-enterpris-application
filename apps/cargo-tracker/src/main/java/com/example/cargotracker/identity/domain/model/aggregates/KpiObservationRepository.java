package com.example.cargotracker.identity.domain.model.aggregates;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KPI 計測記録のリポジトリ（送信ポート）。実装は infrastructure に置く。
 */
public interface KpiObservationRepository {

    void save(KpiObservation observation);

    Optional<KpiObservation> findByTransportRequestId(UUID transportRequestId);

    List<KpiObservation> findAll();
}

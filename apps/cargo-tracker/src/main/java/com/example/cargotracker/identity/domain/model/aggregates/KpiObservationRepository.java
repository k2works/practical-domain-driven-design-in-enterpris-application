package com.example.cargotracker.identity.domain.model.aggregates;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KPI 計測記録のリポジトリ（送信ポート）。実装は infrastructure に置く。
 */
public interface KpiObservationRepository {

    /**
     * 記録を保存する。同じ輸送要求の記録が既にあれば何もしない（DE-01 の再配信を吸収する。ARCH-HO-01）。
     */
    void save(KpiObservation observation);

    Optional<KpiObservation> findByTransportRequestId(UUID transportRequestId);

    /**
     * すべての記録を提出時刻の新しい順に返す。
     */
    List<KpiObservation> findAll();
}

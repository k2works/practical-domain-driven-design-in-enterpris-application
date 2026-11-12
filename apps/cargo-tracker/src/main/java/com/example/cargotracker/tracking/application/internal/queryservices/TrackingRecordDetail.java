package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import java.time.Duration;
import java.util.Objects;

/**
 * 追跡の詳細（S-12）の照会の結果。追跡記録と、照会したときの鮮度の判定と、判定の許容範囲（警告の文の時間を許容範囲から作る。直書きしない）の組
 * （FRS-02。Bolt 39g4。【業務の判断】(as)）。判定は照会サービスが行い、画面の部品は結果を示すだけにする（Bolt 39f2 の前例）。
 *
 * @param trackingRecord 追跡記録
 * @param freshnessExceeded 照会したときに鮮度超過か
 * @param freshnessTolerance 鮮度の判定の許容範囲
 */
public record TrackingRecordDetail(
        TrackingRecord trackingRecord, boolean freshnessExceeded, Duration freshnessTolerance) {

    public TrackingRecordDetail {
        Objects.requireNonNull(trackingRecord, "trackingRecord");
        Objects.requireNonNull(freshnessTolerance, "freshnessTolerance");
    }
}

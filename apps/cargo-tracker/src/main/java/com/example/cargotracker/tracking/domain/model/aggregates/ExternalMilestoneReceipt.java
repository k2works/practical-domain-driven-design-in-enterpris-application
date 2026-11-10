package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import java.util.Objects;

/**
 * 主要実績の受け取りの結果。追跡記録が DE-13 の値を受け取った結果で、呼び手が保存するか拒否の記録に残すかを決める（Bolt 39g の決定 (5)・(7)）。
 */
public sealed interface ExternalMilestoneReceipt {

    /**
     * 下書きか保持のみの実績を足した。現在状態は変えない（T-INV-05・T-INV-08）。
     *
     * @param trackingRecord 実績を足した追跡記録
     * @param milestone 足した実績
     */
    record Received(TrackingRecord trackingRecord, Milestone milestone) implements ExternalMilestoneReceipt {

        public Received {
            Objects.requireNonNull(trackingRecord, "trackingRecord");
            Objects.requireNonNull(milestone, "milestone");
        }
    }

    /**
     * 同じ出典の実績があり、新しい実績を作らなかった（T-INV-02）。
     *
     * @param milestone 既存の実績
     */
    record AlreadyReceived(Milestone milestone) implements ExternalMilestoneReceipt {

        public AlreadyReceived {
            Objects.requireNonNull(milestone, "milestone");
        }
    }

    /** 取消済みの予約で、実績を作らなかった（T-INV-04）。 */
    record BookingCancelled() implements ExternalMilestoneReceipt {}
}

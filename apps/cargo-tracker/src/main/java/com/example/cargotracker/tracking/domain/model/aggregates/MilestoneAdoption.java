package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import java.util.Objects;

/**
 * 候補の採用の結果。追跡記録が下書きの採用を判定した結果で、呼び手が保存するか、画面が採用のボタンと理由を出し分ける（Bolt 39gb。前例は
 * {@link ExternalMilestoneReceipt}）。判定は純粋なので、画面は同じ操作を試して POST と同じ判定でボタンを出す。
 */
public sealed interface MilestoneAdoption {

    /**
     * 候補を採用した。現在状態と根拠の実績番号を導出し直した（T-INV-08）。
     *
     * @param trackingRecord 採用した追跡記録
     * @param milestone 採用した実績
     */
    record Adopted(TrackingRecord trackingRecord, Milestone milestone) implements MilestoneAdoption {

        public Adopted {
            Objects.requireNonNull(trackingRecord, "trackingRecord");
            Objects.requireNonNull(milestone, "milestone");
        }
    }

    /**
     * 候補と同じ値の実績が採用済みで、採用は要らない（何もしない。AC2）。
     *
     * @param adopted 採用済みの実績
     */
    record SameAsAdopted(Milestone adopted) implements MilestoneAdoption {

        public SameAsAdopted {
            Objects.requireNonNull(adopted, "adopted");
        }
    }

    /**
     * 採用を拒否した。
     *
     * @param reason 理由
     */
    record Rejected(AdoptionRejectionReason reason) implements MilestoneAdoption {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

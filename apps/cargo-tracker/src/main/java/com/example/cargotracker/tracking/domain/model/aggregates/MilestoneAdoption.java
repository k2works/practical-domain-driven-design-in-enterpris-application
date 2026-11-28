package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.events.DeliveryConfirmed;
import com.example.cargotracker.tracking.domain.events.PickupMilestoneAdopted;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 候補の採用の結果。追跡記録が下書きの採用を判定した結果で、呼び手が保存するか、画面が採用のボタンと理由を出し分ける（Bolt 39gb。前例は
 * {@link ExternalMilestoneReceipt}）。判定は純粋なので、画面は同じ操作を試して POST と同じ判定でボタンを出す。
 */
public sealed interface MilestoneAdoption {

    /**
     * 候補を採用した。時系列に反する候補は確認中に移した（実績の状態で分かる。Bolt 39gc）。現在状態と根拠の実績番号を導出し直した（T-INV-08）。
     * 呼び手が保存の後に発行するイベントは DE-18・DE-09・DE-10 のどれか 1 つまで（Bolt 39g2 の行 14）。
     *
     * @param trackingRecord 採用した追跡記録
     * @param milestone 採用した実績（採用か確認中）
     * @param placedUnderReview 追跡記録が確認中に入ったときの DE-18（すでに確認中なら空）
     * @param pickupAdopted 採用に移った集荷の DE-09（Bolt 39g2）
     * @param deliveryConfirmed 採用に移った引渡しの DE-10（Bolt 39g2）
     */
    record Adopted(
            TrackingRecord trackingRecord,
            Milestone milestone,
            Optional<TrackingPlacedUnderReview> placedUnderReview,
            Optional<PickupMilestoneAdopted> pickupAdopted,
            Optional<DeliveryConfirmed> deliveryConfirmed)
            implements MilestoneAdoption {

        public Adopted {
            Objects.requireNonNull(trackingRecord, "trackingRecord");
            Objects.requireNonNull(milestone, "milestone");
            Objects.requireNonNull(placedUnderReview, "placedUnderReview");
            Objects.requireNonNull(pickupAdopted, "pickupAdopted");
            Objects.requireNonNull(deliveryConfirmed, "deliveryConfirmed");
            if (Stream.of(placedUnderReview, pickupAdopted, deliveryConfirmed)
                            .filter(Optional::isPresent)
                            .count()
                    > 1) {
                throw new IllegalArgumentException("1 つの採用で出すイベントは 1 つまで: " + milestone.milestoneNo());
            }
        }
    }

    /**
     * 候補と同じ値の実績が判断済み（採用・確認中）で、採用は要らない（何もしない。AC2）。
     *
     * @param adopted 判断済みの実績
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
     * @param decided 値の違う判断済みの実績（理由が {@code CONFLICTS_WITH_ADOPTED} のときだけ。画面が状態で文を分ける。Bolt 39gc）
     */
    record Rejected(AdoptionRejectionReason reason, Optional<Milestone> decided) implements MilestoneAdoption {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(decided, "decided");
        }

        /** 判断済みの実績を持たない拒否。 */
        public Rejected(AdoptionRejectionReason reason) {
            this(reason, Optional.empty());
        }
    }
}

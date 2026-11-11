package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.events.DeliveryConfirmed;
import com.example.cargotracker.tracking.domain.events.PickupMilestoneAdopted;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 主要実績の登録の結果。実績を足して現在状態を導出し直した追跡記録と、登録した実績。同じ出典の実績が登録済みなら、
 * 追跡記録は元のままで、実績は既存の実績（T-INV-02）。登録した実績の状態は採用・確認中・保持のみのどれか（Bolt 39gc）。
 * 呼び手が保存の後に発行するイベントは DE-18・DE-09・DE-10 のどれか 1 つまで（採用と確認中は同じ実績で重ならない。Bolt 39g2 の行 14）。
 *
 * @param trackingRecord 追跡記録
 * @param milestone 登録した実績、または既存の実績
 * @param alreadyRegistered 同じ出典の実績が登録済みだったか
 * @param placedUnderReview 追跡記録が確認中に入ったときの DE-18（すでに確認中なら空。Bolt 39gc）
 * @param pickupAdopted 採用になった集荷の DE-09（Bolt 39g2）
 * @param deliveryConfirmed 採用になった引渡しの DE-10（Bolt 39g2）
 */
public record MilestoneRegistration(
        TrackingRecord trackingRecord,
        Milestone milestone,
        boolean alreadyRegistered,
        Optional<TrackingPlacedUnderReview> placedUnderReview,
        Optional<PickupMilestoneAdopted> pickupAdopted,
        Optional<DeliveryConfirmed> deliveryConfirmed) {

    public MilestoneRegistration {
        Objects.requireNonNull(trackingRecord, "trackingRecord");
        Objects.requireNonNull(milestone, "milestone");
        Objects.requireNonNull(placedUnderReview, "placedUnderReview");
        Objects.requireNonNull(pickupAdopted, "pickupAdopted");
        Objects.requireNonNull(deliveryConfirmed, "deliveryConfirmed");
        if (Stream.of(placedUnderReview, pickupAdopted, deliveryConfirmed)
                        .filter(Optional::isPresent)
                        .count()
                > 1) {
            throw new IllegalArgumentException("1 つの登録で出すイベントは 1 つまで: " + milestone.milestoneNo());
        }
    }

    /** イベントを出さない結果（同じ出典の二重送信・保持のみ）。 */
    static MilestoneRegistration withoutEvent(
            TrackingRecord trackingRecord, Milestone milestone, boolean alreadyRegistered) {
        return new MilestoneRegistration(
                trackingRecord, milestone, alreadyRegistered, Optional.empty(), Optional.empty(), Optional.empty());
    }
}

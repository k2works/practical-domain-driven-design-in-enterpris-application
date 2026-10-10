package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.DerivedStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * 現在状態の導出。主要実績の列から現在状態と根拠の実績番号を返す純粋な関数（T-INV-08。Bolt 26b）。
 *
 * <p>採用済みの実績のうち、発生時刻が最も新しいもの（同じ時刻なら実績番号の大きい後の登録）の種類に対応する状態にする。
 * 採用済みの実績がなければ、予定を採用した集荷予定のまま（追跡記録は予定を採用して始まる。Bolt 25）。目的地到着から引渡しの実績で
 * 引渡し済みへ直接進める（引渡し可能は R0.1 では作らない）。時系列に反する実績の確認中（AC3、T-INV-03）は W7 で足す。
 */
@DomainRule
public class CurrentStatusDeriver {

    private static final Map<MilestoneKind, TrackingStatus> STATUS_BY_KIND = new EnumMap<>(Map.of(
            MilestoneKind.PICKUP, TrackingStatus.PICKED_UP,
            MilestoneKind.RECEIPT_AT_ORIGIN, TrackingStatus.RECEIVED_AT_ORIGIN,
            MilestoneKind.DEPARTURE, TrackingStatus.IN_TRANSIT,
            MilestoneKind.TRANSSHIPMENT, TrackingStatus.TRANSSHIPPING,
            MilestoneKind.ARRIVAL, TrackingStatus.ARRIVED_AT_DESTINATION,
            MilestoneKind.DELIVERY, TrackingStatus.DELIVERED));

    /** 主要実績の列から現在状態を導出する。 */
    public DerivedStatus derive(List<Milestone> milestones) {
        return milestones.stream()
                .filter(milestone -> milestone.state() == MilestoneState.ADOPTED)
                .max(Comparator.comparing(
                                (Milestone milestone) -> milestone.occurredAt().instant())
                        .thenComparingInt(Milestone::milestoneNo))
                .map(latest -> new DerivedStatus(statusOf(latest.kind()), OptionalInt.of(latest.milestoneNo())))
                .orElseGet(() -> new DerivedStatus(TrackingStatus.PICKUP_SCHEDULED, OptionalInt.empty()));
    }

    private static TrackingStatus statusOf(MilestoneKind kind) {
        TrackingStatus status = STATUS_BY_KIND.get(kind);
        if (status == null) {
            throw new IllegalStateException("実績の種類に対応する追跡状態がありません: " + kind);
        }
        return status;
    }
}

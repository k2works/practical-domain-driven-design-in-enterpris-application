package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.DerivedStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.UnderReviewReason;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 現在状態の導出。主要実績の列から現在状態と根拠の実績番号を返す純粋な関数（T-INV-08。Bolt 26b）。
 *
 * <p>採用済みの実績のうち、発生時刻が最も新しいもの（同じ時刻なら実績番号の大きい後の登録）の種類に対応する状態にする。
 * 採用済みの実績がなければ、予定を採用した集荷予定のまま（追跡記録は予定を採用して始まる。Bolt 25）。目的地到着から引渡しの実績で
 * 引渡し済みへ直接進める（引渡し可能は R0.1 では作らない）。
 *
 * <p>確認中の実績が 1 件でもあれば、現在状態は確認中、根拠は最初に確認中になった実績（実績番号の最も小さい確認中）、理由は時系列に反する
 * （AC3、T-INV-03。Bolt 39gc。【業務の判断】(ah)）。確認中から出る道（補正の承認）は US-13。
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

    /** 主要実績の列から現在状態を導出する（確認中の実績があれば確認中）。 */
    public DerivedStatus derive(List<Milestone> milestones) {
        OptionalInt firstUnderReview = milestones.stream()
                .filter(milestone -> milestone.state() == MilestoneState.UNDER_REVIEW)
                .mapToInt(Milestone::milestoneNo)
                .min();
        if (firstUnderReview.isPresent()) {
            return new DerivedStatus(
                    TrackingStatus.UNDER_REVIEW, firstUnderReview, Optional.of(UnderReviewReason.OUT_OF_SEQUENCE));
        }
        return deriveFromAdopted(milestones);
    }

    /** 採用済みの実績だけから現在状態を導出する（確認中の実績を見ない。完了後の判定と S-12 の理由の文に使う。Bolt 39gc）。 */
    public DerivedStatus deriveFromAdopted(List<Milestone> milestones) {
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

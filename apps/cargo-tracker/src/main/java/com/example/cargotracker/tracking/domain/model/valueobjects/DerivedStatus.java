package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 導出した現在状態と、その根拠の実績番号（T-INV-08。Bolt 26b）。実績から導出していない（予定を採用したまま）なら根拠はない。
 * 確認中のときだけ確認中の理由を持つ（Bolt 39gc。表の CHECK {@code ck_tracking_record_under_review} と同じ規則）。
 *
 * @param status 現在状態
 * @param basisMilestoneNo 根拠の実績番号
 * @param underReviewReason 確認中の理由（現在状態が確認中のときだけ）
 */
public record DerivedStatus(
        TrackingStatus status, OptionalInt basisMilestoneNo, Optional<UnderReviewReason> underReviewReason) {

    public DerivedStatus {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(basisMilestoneNo, "basisMilestoneNo");
        Objects.requireNonNull(underReviewReason, "underReviewReason");
        if ((status == TrackingStatus.UNDER_REVIEW) != underReviewReason.isPresent()) {
            throw new IllegalArgumentException("確認中のときだけ確認中の理由を持ちます: 現在状態 " + status);
        }
    }

    /** 確認中でない現在状態。 */
    public DerivedStatus(TrackingStatus status, OptionalInt basisMilestoneNo) {
        this(status, basisMilestoneNo, Optional.empty());
    }
}

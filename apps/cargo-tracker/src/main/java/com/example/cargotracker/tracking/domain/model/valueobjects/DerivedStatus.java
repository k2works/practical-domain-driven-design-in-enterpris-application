package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * 導出した現在状態と、その根拠の実績番号（T-INV-08。Bolt 26b）。実績から導出していない（予定を採用したまま）なら根拠はない。
 *
 * @param status 現在状態
 * @param basisMilestoneNo 根拠の実績番号
 */
public record DerivedStatus(TrackingStatus status, OptionalInt basisMilestoneNo) {

    public DerivedStatus {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(basisMilestoneNo, "basisMilestoneNo");
    }
}

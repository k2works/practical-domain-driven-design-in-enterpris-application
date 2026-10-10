package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 主要実績の登録を拒否した理由（Bolt 26b）。取消済みの予約への登録の拒否（AC4、T-INV-04）は W7 で足す。
 */
public enum MilestoneRejectionReason {
    /** 発生時刻が登録時刻より後（未来の事実は登録できない）。 */
    OCCURRED_IN_FUTURE
}

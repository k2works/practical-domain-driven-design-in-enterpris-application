package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 追跡の予約状態。追跡記録が持つ予約の状態の写し（T-INV-04・T-INV-05 の判定に使う。Bolt 25）。追跡を開始したときは確定。
 */
public enum TrackedBookingStatus {
    /** 確定。 */
    CONFIRMED,
    /** 取消済み（DE-08。W11）。 */
    CANCELLED,
    /** 完了（引渡し済み）。 */
    COMPLETED
}

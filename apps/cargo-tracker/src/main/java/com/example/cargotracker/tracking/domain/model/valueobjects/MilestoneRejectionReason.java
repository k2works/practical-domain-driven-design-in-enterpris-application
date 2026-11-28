package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 主要実績の拒否の理由。追跡管理者の登録の拒否（Bolt 26b）と、DE-13 の受け取りの拒否の記録（Bolt 39g）で使う。手入力の取消済みの拒否（AC4、T-INV-04）は 39gc で足す。
 */
@ValueObject
public enum MilestoneRejectionReason {
    /** 発生時刻が登録時刻より後（未来の事実は登録できない。追跡管理者の登録だけ。T-INV-13）。 */
    OCCURRED_IN_FUTURE,
    /** 取消済みの予約（T-INV-04。DE-13 の受け取りの拒否の記録）。 */
    BOOKING_CANCELLED,
    /** 知らない追跡番号（DE-13 の受け取りの拒否の記録。【業務の判断】(ac)）。 */
    UNKNOWN_TRACKING_NUMBER
}

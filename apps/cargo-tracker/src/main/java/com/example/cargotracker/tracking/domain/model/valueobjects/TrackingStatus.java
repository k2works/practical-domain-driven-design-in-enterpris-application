package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 追跡状態。追跡記録の現在状態の値（要件定義の追跡状態モデル。状態名は業務責任者の確認前の候補）。現在状態は採用済みの実績だけから
 * 導出する（T-INV-08）。例外は予定の採用による「予約確定 → 集荷予定」だけで、Bolt 25 は集荷予定だけを使う。
 */
public enum TrackingStatus {
    /** 予約確定。 */
    BOOKED,
    /** 集荷予定（予定を採用した）。 */
    PICKUP_SCHEDULED,
    /** 集荷済み。 */
    PICKED_UP,
    /** 出発地搬入済み。 */
    RECEIVED_AT_ORIGIN,
    /** 輸送中。 */
    IN_TRANSIT,
    /** 積替え中。 */
    TRANSSHIPPING,
    /** 目的地到着。 */
    ARRIVED_AT_DESTINATION,
    /** 引渡し可能。 */
    READY_FOR_DELIVERY,
    /** 引渡し済み。 */
    DELIVERED,
    /** 確認中（順序逆転・矛盾・鮮度超過。T-INV-03）。 */
    UNDER_REVIEW
}

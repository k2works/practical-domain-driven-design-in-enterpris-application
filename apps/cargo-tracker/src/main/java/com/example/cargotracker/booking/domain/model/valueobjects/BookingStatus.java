package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 貨物予約の状態（ドメインモデルの状態遷移）。Bolt 23 は確定だけを使う。変更・取消し・輸送中・完了は US-05・US-12。
 */
public enum BookingStatus {
    CONFIRMED,
    AMENDMENT_PENDING,
    CANCELLATION_PENDING,
    AMENDING,
    CANCELLED,
    IN_TRANSIT,
    COMPLETED
}

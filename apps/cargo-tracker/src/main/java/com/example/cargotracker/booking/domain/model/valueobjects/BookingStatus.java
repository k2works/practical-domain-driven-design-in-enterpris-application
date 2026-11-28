package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 貨物予約の状態（ドメインモデルの状態遷移）。Bolt 23 は確定だけを使う。輸送中・完了は Bolt 39g2 から集荷・引渡しの通知（DE-09・DE-10）で
 * 書く。変更・取消しは US-05。
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

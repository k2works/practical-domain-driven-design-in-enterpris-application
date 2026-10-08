package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 輸送段階。変更・取消しの可否を決める（BR-04）。本予約の確定の時点は集荷前。集荷後・完了への遷移は US-12（DE-09・DE-10）。
 */
public enum TransportPhase {
    BEFORE_PICKUP,
    AFTER_PICKUP,
    COMPLETED
}

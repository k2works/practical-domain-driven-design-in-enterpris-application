package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 輸送段階。変更・取消しの可否を決める（BR-04）。本予約の確定の時点は集荷前。集荷の通知（DE-09）で集荷後に、引渡しの通知（DE-10）で完了に進み、
 * 戻らない（Bolt 39g2）。
 */
public enum TransportPhase {
    BEFORE_PICKUP,
    AFTER_PICKUP,
    COMPLETED
}

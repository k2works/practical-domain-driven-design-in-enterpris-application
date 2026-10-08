package com.example.cargotracker.booking.domain.model.sagas;

/**
 * 予約サガの状態（ADR-003・015）。後続が完了するまで、画面に成功と表示しない。
 */
public enum BookingSagaStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    NEEDS_HUMAN
}

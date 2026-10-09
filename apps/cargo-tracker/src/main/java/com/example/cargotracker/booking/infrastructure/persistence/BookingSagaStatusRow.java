package com.example.cargotracker.booking.infrastructure.persistence;

import java.util.UUID;

/**
 * 予約サガの状態の 1 行（S-10 予約一覧で予約 ID の集合からまとめて引く。Bolt 25b）。
 */
public record BookingSagaStatusRow(UUID bookingId, String status) {}

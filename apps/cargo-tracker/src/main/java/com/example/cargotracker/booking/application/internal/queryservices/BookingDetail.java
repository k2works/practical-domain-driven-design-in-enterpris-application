package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import java.util.Objects;

/**
 * 予約の詳細（S-24）の読み取りモデル（Bolt 23b の最小の表示）。
 *
 * @param booking 貨物予約
 * @param sagaStatus 予約サガの状態（追跡の開始が処理中か。処理中を完了と示さない。ADR-015）
 */
public record BookingDetail(Booking booking, BookingSagaStatus sagaStatus) {

    public BookingDetail {
        Objects.requireNonNull(booking, "booking");
        Objects.requireNonNull(sagaStatus, "sagaStatus");
    }
}

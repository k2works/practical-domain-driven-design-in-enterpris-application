package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import java.util.Objects;

/**
 * 本予約の確定の結果。確定した貨物予約と、発行する DE-07。
 *
 * @param booking 確定した貨物予約
 * @param event 本予約を確定した（DE-07）
 */
public record BookingConfirmation(Booking booking, BookingConfirmed event) {

    public BookingConfirmation {
        Objects.requireNonNull(booking, "booking");
        Objects.requireNonNull(event, "event");
    }
}

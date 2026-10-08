package com.example.cargotracker.booking.domain.model.sagas;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import java.util.Optional;

/**
 * 予約サガのリポジトリ（送信ポート）。
 */
public interface BookingSagaRepository {

    void save(BookingSaga saga);

    Optional<BookingSaga> findByBookingId(BookingId bookingId);
}

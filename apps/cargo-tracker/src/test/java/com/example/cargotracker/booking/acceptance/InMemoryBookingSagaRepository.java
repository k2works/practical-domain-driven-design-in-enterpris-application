package com.example.cargotracker.booking.acceptance;

import com.example.cargotracker.booking.domain.model.sagas.BookingSaga;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** メモリ上の予約サガのリポジトリ（業務ルール層の受入シナリオ）。予約サガは不変なので保存したインスタンスを返す（T-61）。 */
public class InMemoryBookingSagaRepository implements BookingSagaRepository {

    private final Map<BookingId, BookingSaga> byBookingId = new LinkedHashMap<>();

    @Override
    public synchronized void save(BookingSaga saga) {
        if (byBookingId.putIfAbsent(saga.bookingId(), saga) != null) {
            throw new IllegalStateException("予約ごとに予約サガは 1 つ: " + saga.bookingId());
        }
    }

    @Override
    public synchronized Optional<BookingSaga> findByBookingId(BookingId bookingId) {
        return Optional.ofNullable(byBookingId.get(bookingId));
    }

    public synchronized void clear() {
        byBookingId.clear();
    }
}

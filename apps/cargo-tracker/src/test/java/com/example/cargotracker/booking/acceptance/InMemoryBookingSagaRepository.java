package com.example.cargotracker.booking.acceptance;

import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.application.sagas.ConcurrentBookingSagaUpdateException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /** 期待版で更新し、版を 1 進めた写しを保存する（MyBatis の実装と同じ。T-61）。 */
    @Override
    public synchronized void update(BookingSaga saga) {
        BookingSaga current = byBookingId.get(saga.bookingId());
        if (current == null || current.version() != saga.version()) {
            throw new ConcurrentBookingSagaUpdateException(saga.bookingId(), saga.version());
        }
        byBookingId.put(
                saga.bookingId(),
                BookingSaga.reconstitute(
                        saga.id(),
                        saga.bookingId(),
                        saga.trackingNumber(),
                        saga.status(),
                        saga.currentStep(),
                        saga.startedAt(),
                        saga.version() + 1));
    }

    @Override
    public synchronized Map<BookingId, BookingSagaStatus> findStatusesByBookingIds(Set<BookingId> bookingIds) {
        Map<BookingId, BookingSagaStatus> statuses = new LinkedHashMap<>();
        bookingIds.forEach(id -> {
            BookingSaga saga = byBookingId.get(id);
            if (saga != null) {
                statuses.put(id, saga.status());
            }
        });
        return Map.copyOf(statuses);
    }

    public synchronized void clear() {
        byBookingId.clear();
    }
}

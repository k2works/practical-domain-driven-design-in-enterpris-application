package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.application.sagas.BookingSagaStep;
import com.example.cargotracker.booking.application.sagas.ConcurrentBookingSagaUpdateException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 予約サガのリポジトリの MyBatis 実装（ADR-015。Bolt 23）。状態の遷移は期待版で更新する（完了は Bolt 25。失敗・有人確認要は W8）。
 * {@code updated_at} は最後に状態が変わった時刻で、完了した予約サガでは完了の時刻を兼ねる（データモデル）。
 */
@Repository
public class MyBatisBookingSagaRepository implements BookingSagaRepository {

    private final BookingMapper mapper;
    private final Clock clock;

    public MyBatisBookingSagaRepository(BookingMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void save(BookingSaga saga) {
        mapper.insertBookingSaga(new BookingSagaRow(
                saga.id(),
                saga.bookingId().value(),
                saga.trackingNumber().value(),
                saga.status().name(),
                saga.currentStep().name(),
                saga.startedAt().instant().atOffset(ZoneOffset.UTC),
                OffsetDateTime.now(clock),
                saga.version()));
    }

    @Override
    public void update(BookingSaga saga) {
        if (mapper.updateBookingSaga(
                        saga.id(),
                        saga.status().name(),
                        saga.currentStep().name(),
                        OffsetDateTime.now(clock),
                        saga.version())
                == 0) {
            throw new ConcurrentBookingSagaUpdateException(saga.bookingId(), saga.version());
        }
    }

    @Override
    public Map<BookingId, BookingSagaStatus> findStatusesByBookingIds(Set<BookingId> bookingIds) {
        if (bookingIds.isEmpty()) {
            // IN () は SQL の誤りになるので照会しない
            return Map.of();
        }
        return mapper
                .findSagaStatusesByBookingIds(
                        bookingIds.stream().map(BookingId::value).toList())
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        row -> new BookingId(row.bookingId()), row -> BookingSagaStatus.valueOf(row.status())));
    }

    @Override
    public Optional<BookingSaga> findByBookingId(BookingId bookingId) {
        return mapper.findSagaByBookingId(bookingId.value())
                .map(row -> BookingSaga.reconstitute(
                        row.id(),
                        new BookingId(row.bookingId()),
                        new TrackingNumber(row.trackingNumber()),
                        BookingSagaStatus.valueOf(row.status()),
                        BookingSagaStep.valueOf(row.currentStep()),
                        new UtcInstant(row.startedAt().toInstant()),
                        row.version()));
    }
}

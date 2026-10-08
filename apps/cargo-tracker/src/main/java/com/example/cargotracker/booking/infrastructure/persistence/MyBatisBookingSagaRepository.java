package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.domain.model.sagas.BookingSaga;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaStep;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 予約サガのリポジトリの MyBatis 実装（ADR-015。Bolt 23）。状態の遷移（完了・失敗・有人確認要）の更新は Bolt 25・W8 で足す。
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

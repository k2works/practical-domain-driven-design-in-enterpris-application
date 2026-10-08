package com.example.cargotracker.booking.domain.model.sagas;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 予約サガ（ADR-015。Bolt 23）。本予約の確定と同じトランザクションで処理中として始める。後続の追跡の開始は Bolt 25。 */
class BookingSagaTest {

    @Test
    void 処理中で始まり追跡の開始を待つ() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        UtcInstant startedAt = new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"));

        BookingSaga saga = BookingSaga.start(bookingId, BookingFixture.TRACKING_NUMBER, startedAt);

        assertThat(saga.bookingId()).isEqualTo(bookingId);
        assertThat(saga.trackingNumber()).isEqualTo(BookingFixture.TRACKING_NUMBER);
        assertThat(saga.status()).isEqualTo(BookingSagaStatus.IN_PROGRESS);
        assertThat(saga.currentStep()).isEqualTo(BookingSagaStep.START_TRACKING);
        assertThat(saga.startedAt()).isEqualTo(startedAt);
        assertThat(saga.isCompleted()).isFalse();
    }
}

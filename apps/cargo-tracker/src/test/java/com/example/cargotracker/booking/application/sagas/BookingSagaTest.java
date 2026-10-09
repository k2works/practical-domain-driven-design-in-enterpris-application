package com.example.cargotracker.booking.application.sagas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** 追跡の開始の結果「開始した」で完了にする（ADR-015。Bolt 25）。版はリポジトリが期待版で更新するときに進める。 */
    @Test
    void 追跡を開始したら完了にする() {
        BookingSaga saga = BookingSaga.start(
                new BookingId(UUID.randomUUID()),
                BookingFixture.TRACKING_NUMBER,
                new UtcInstant(Instant.parse("2026-10-08T08:59:00Z")));

        BookingSaga completed = saga.complete();

        assertThat(completed.status()).isEqualTo(BookingSagaStatus.COMPLETED);
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.currentStep()).isEqualTo(BookingSagaStep.START_TRACKING);
        assertThat(completed.id()).isEqualTo(saga.id());
        assertThat(completed.bookingId()).isEqualTo(saga.bookingId());
        assertThat(completed.startedAt()).isEqualTo(saga.startedAt());
        assertThat(completed.version()).isEqualTo(saga.version());
        assertThat(saga.status()).isEqualTo(BookingSagaStatus.IN_PROGRESS);
    }

    @Test
    void 処理中でない予約サガは完了にしない() {
        BookingSaga completed = BookingSaga.start(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.TRACKING_NUMBER,
                        new UtcInstant(Instant.parse("2026-10-08T08:59:00Z")))
                .complete();

        assertThatThrownBy(completed::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
    }
}

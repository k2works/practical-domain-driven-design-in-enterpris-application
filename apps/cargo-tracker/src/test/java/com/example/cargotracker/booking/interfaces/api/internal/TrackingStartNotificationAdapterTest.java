package com.example.cargotracker.booking.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.internal.commandservices.BookingSagaCommandService;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationRequest;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 予約の公開 API（追跡の開始の結果）のインバウンドアダプター（ADR-015。Bolt 25）。予約サガの入力ポートに委ね、結果を公開 API の受領の型に
 * 変える（予約の型を公開 API の外に出さない）。
 */
class TrackingStartNotificationAdapterTest {

    private static final UtcInstant STARTED_AT = new UtcInstant(Instant.parse("2026-10-08T09:00:00Z"));

    private final InMemoryBookingSagaRepository sagaRepository = new InMemoryBookingSagaRepository();
    private final TrackingStartNotificationAdapter adapter =
            new TrackingStartNotificationAdapter(new BookingSagaCommandService(sagaRepository));

    @Test
    void 処理中の予約サガを完了にして完了を返す() {
        BookingId bookingId = startedSaga();

        TrackingStartNotificationReceipt receipt = adapter.notifyStarted(request(bookingId));

        assertThat(receipt).isEqualTo(new TrackingStartNotificationReceipt.Completed());
        assertThat(sagaRepository.findByBookingId(bookingId).orElseThrow().status())
                .isEqualTo(BookingSagaStatus.COMPLETED);
    }

    @Test
    void 完了済みなら完了済みを返す() {
        BookingId bookingId = startedSaga();
        adapter.notifyStarted(request(bookingId));

        assertThat(adapter.notifyStarted(request(bookingId)))
                .isEqualTo(new TrackingStartNotificationReceipt.AlreadyCompleted());
    }

    @Test
    void 予約サガがなければ理由を付けて完了にしなかったを返す() {
        assertThat(adapter.notifyStarted(request(new BookingId(UUID.randomUUID()))))
                .isEqualTo(new TrackingStartNotificationReceipt.NotCompleted(
                        TrackingStartNotificationReceipt.NotCompleted.SAGA_NOT_FOUND));
    }

    private BookingId startedSaga() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        sagaRepository.save(BookingSaga.start(
                bookingId, BookingFixture.TRACKING_NUMBER, new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"))));
        return bookingId;
    }

    private static TrackingStartNotificationRequest request(BookingId bookingId) {
        return new TrackingStartNotificationRequest(
                bookingId.value(), BookingFixture.TRACKING_NUMBER.value(), STARTED_AT);
    }
}

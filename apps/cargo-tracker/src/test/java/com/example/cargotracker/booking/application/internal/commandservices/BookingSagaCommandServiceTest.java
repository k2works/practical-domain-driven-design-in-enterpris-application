package com.example.cargotracker.booking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 追跡の開始の結果で予約サガを完了にする入力ポート（予約の公開 API の実装が委ねる。ADR-015。Bolt 25）。予約 ID で冪等で、処理中の
 * 予約サガだけを完了にする。予約サガのない予約は、警告のログを残して「見つからない」を返す（再配信で直らない欠け。T-58・T-62）。
 */
class BookingSagaCommandServiceTest {

    private static final UtcInstant STARTED_AT = new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"));
    private static final UtcInstant TRACKING_STARTED_AT = new UtcInstant(Instant.parse("2026-10-08T09:00:00Z"));

    private final InMemoryBookingSagaRepository sagaRepository = new InMemoryBookingSagaRepository();
    private final BookingSagaCommandService service = new BookingSagaCommandService(sagaRepository);

    private final Logger logger = (Logger) LoggerFactory.getLogger(BookingSagaCommandService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void 処理中の予約サガを完了にする() {
        BookingId bookingId = startedSaga();

        TrackingStartOutcome outcome = service.completeTrackingStart(bookingId, TRACKING_STARTED_AT);

        assertThat(outcome).isEqualTo(TrackingStartOutcome.COMPLETED);
        BookingSaga saga = sagaRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(saga.status()).isEqualTo(BookingSagaStatus.COMPLETED);
        assertThat(saga.version()).isEqualTo(1);
    }

    @Test
    void 完了済みの予約サガへの同じ結果の再通知は何もしない() {
        BookingId bookingId = startedSaga();
        service.completeTrackingStart(bookingId, TRACKING_STARTED_AT);

        TrackingStartOutcome outcome = service.completeTrackingStart(bookingId, TRACKING_STARTED_AT);

        assertThat(outcome).isEqualTo(TrackingStartOutcome.ALREADY_COMPLETED);
        assertThat(sagaRepository.findByBookingId(bookingId).orElseThrow().version())
                .isEqualTo(1);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 予約サガのない予約は警告のログを残して見つからないを返す() {
        BookingId unknown = new BookingId(UUID.randomUUID());

        TrackingStartOutcome outcome = service.completeTrackingStart(unknown, TRACKING_STARTED_AT);

        assertThat(outcome).isEqualTo(TrackingStartOutcome.SAGA_NOT_FOUND);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(unknown.value().toString());
        });
    }

    private BookingId startedSaga() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        sagaRepository.save(BookingSaga.start(bookingId, BookingFixture.TRACKING_NUMBER, STARTED_AT));
        return bookingId;
    }
}

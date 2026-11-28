package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationRequest;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTrackingStarts;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * DE-22 を受けて予約の公開 API で予約サガを完了にする listener（ADR-014・015。Bolt 25）。追跡記録の保存とは別のトランザクションで動く。
 * 予約が完了にしなかった（予約サガがない）ときは、警告のログを残して終え、例外にしない（追跡の開始は戻さない。結果整合。T-58）。
 */
class BookingTrackingStartNotificationEventHandlerTest {

    private static final TrackingStarted EVENT = new TrackingStarted(
            "CTABCDEFGH2345", UUID.randomUUID(), new UtcInstant(Instant.parse("2026-10-08T09:00:00Z")), 0);

    private final List<TrackingStartNotificationRequest> requests = new ArrayList<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(BookingTrackingStartNotificationEventHandler.class);
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
    void 予約IDと追跡番号と開始時刻を渡して予約サガを完了にする() {
        handler(new TrackingStartNotificationReceipt.Completed()).on(EVENT);

        assertThat(requests)
                .containsExactly(new TrackingStartNotificationRequest(
                        EVENT.bookingId(), EVENT.trackingNumber(), EVENT.startedAt()));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 完了済みなら何も残さない() {
        handler(new TrackingStartNotificationReceipt.AlreadyCompleted()).on(EVENT);

        assertThat(requests).hasSize(1);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 完了にしなかったら警告のログを残し例外にしない() {
        TrackingStartNotificationReceipt notCompleted = new TrackingStartNotificationReceipt.NotCompleted(
                TrackingStartNotificationReceipt.NotCompleted.SAGA_NOT_FOUND);

        assertThatCode(() -> handler(notCompleted).on(EVENT)).doesNotThrowAnyException();

        assertThat(logs.list).singleElement().satisfies(logged -> {
            assertThat(logged.getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFormattedMessage())
                    .contains(EVENT.bookingId().toString())
                    .contains("CTABCDEFGH2345")
                    .contains("SAGA_NOT_FOUND");
        });
    }

    private BookingTrackingStartNotificationEventHandler handler(TrackingStartNotificationReceipt receipt) {
        return new BookingTrackingStartNotificationEventHandler(new BookingTrackingStarts(request -> {
            requests.add(request);
            return receipt;
        }));
    }
}

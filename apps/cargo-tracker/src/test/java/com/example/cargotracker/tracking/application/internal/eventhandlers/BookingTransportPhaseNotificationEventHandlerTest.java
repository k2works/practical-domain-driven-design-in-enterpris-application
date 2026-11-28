package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotification;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationRequest;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTransportPhases;
import com.example.cargotracker.tracking.domain.events.DeliveryConfirmed;
import com.example.cargotracker.tracking.domain.events.PickupMilestoneAdopted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * DE-09・DE-10 を受けて予約の公開 API で集荷・引渡しを伝える listener と腐敗防止層（Bolt 39g2 の行 17・18。ADR-014）。記録しなかったときは警告の
 * ログで終え（追跡は戻さない。結果整合）、公開 API の例外はそのまま上げる（listener のトランザクションごと戻り、発行の記録が残る）。
 */
class BookingTransportPhaseNotificationEventHandlerTest {

    private static final UUID BOOKING_ID = UUID.fromString("00000000-0000-0000-0000-00000039a221");
    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-00000039a222");
    private static final UtcInstant OCCURRED_AT = new UtcInstant(Instant.parse("2026-11-01T02:30:00Z"));
    private static final PickupMilestoneAdopted PICKUP =
            new PickupMilestoneAdopted("CTABCDEFGH2345", BOOKING_ID, 1, OCCURRED_AT, MANAGER, 1);
    private static final DeliveryConfirmed DELIVERY =
            new DeliveryConfirmed("CTABCDEFGH2345", BOOKING_ID, 2, OCCURRED_AT, MANAGER, 2);

    private final List<String> calls = new ArrayList<>();
    private final List<TransportPhaseNotificationRequest> requests = new ArrayList<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(BookingTransportPhaseNotificationEventHandler.class);
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

    private BookingTransportPhaseNotificationEventHandler handler(TransportPhaseNotificationReceipt receipt) {
        return new BookingTransportPhaseNotificationEventHandler(
                new BookingTransportPhases(new TransportPhaseNotification() {
                    @Override
                    public TransportPhaseNotificationReceipt notifyPickedUp(TransportPhaseNotificationRequest request) {
                        calls.add("PICKUP");
                        requests.add(request);
                        return receipt;
                    }

                    @Override
                    public TransportPhaseNotificationReceipt notifyDelivered(
                            TransportPhaseNotificationRequest request) {
                        calls.add("DELIVERY");
                        requests.add(request);
                        return receipt;
                    }
                }));
    }

    @Test
    void 行17_DE09は集荷の通知で予約IDと追跡番号と実績番号と発生時刻と操作者を渡す() {
        handler(new TransportPhaseNotificationReceipt.Recorded()).on(PICKUP);

        assertThat(calls).containsExactly("PICKUP");
        assertThat(requests)
                .containsExactly(
                        new TransportPhaseNotificationRequest(BOOKING_ID, "CTABCDEFGH2345", 1, OCCURRED_AT, MANAGER));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 行17_DE10は引渡しの通知() {
        handler(new TransportPhaseNotificationReceipt.AlreadyRecorded()).on(DELIVERY);

        assertThat(calls).containsExactly("DELIVERY");
        assertThat(requests)
                .containsExactly(
                        new TransportPhaseNotificationRequest(BOOKING_ID, "CTABCDEFGH2345", 2, OCCURRED_AT, MANAGER));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 行17と18_記録しなかったら理由を警告のログに残し例外にしない() {
        TransportPhaseNotificationReceipt notRecorded = new TransportPhaseNotificationReceipt.NotRecorded(
                TransportPhaseNotificationReceipt.NotRecorded.BOOKING_CANCELLED);

        assertThatCode(() -> handler(notRecorded).on(PICKUP)).doesNotThrowAnyException();
        assertThatCode(() -> handler(notRecorded).on(DELIVERY)).doesNotThrowAnyException();

        assertThat(logs.list).hasSize(2).allSatisfy(logged -> {
            assertThat(logged.getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFormattedMessage())
                    .contains(BOOKING_ID.toString())
                    .contains("CTABCDEFGH2345")
                    .contains("BOOKING_CANCELLED");
        });
    }

    @Test
    void 行17_公開APIの例外はそのまま上げる() {
        BookingTransportPhaseNotificationEventHandler failing = new BookingTransportPhaseNotificationEventHandler(
                new BookingTransportPhases(new TransportPhaseNotification() {
                    @Override
                    public TransportPhaseNotificationReceipt notifyPickedUp(TransportPhaseNotificationRequest request) {
                        throw new IllegalStateException("ロックの待ちの上限");
                    }

                    @Override
                    public TransportPhaseNotificationReceipt notifyDelivered(
                            TransportPhaseNotificationRequest request) {
                        throw new IllegalStateException("ロックの待ちの上限");
                    }
                }));

        assertThatThrownBy(() -> failing.on(PICKUP)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failing.on(DELIVERY)).isInstanceOf(IllegalStateException.class);
    }
}

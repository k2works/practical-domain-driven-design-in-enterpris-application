package com.example.cargotracker.booking.application.internal.commandservices;

import static com.example.cargotracker.booking.domain.model.BookingFixture.SALES;
import static com.example.cargotracker.booking.domain.model.BookingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.booking.domain.model.BookingFixture.allConditions;
import static com.example.cargotracker.booking.domain.model.BookingFixture.processedCommand;
import static com.example.cargotracker.booking.domain.model.BookingFixture.terms;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhase;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 集荷・引渡しの通知で予約の状態と輸送段階を進める入力ポート（Bolt 39g2 の行 4。ADR-014）。進めたときだけ保存し、記録済み・記録しないは書かない
 * （版を上げない）。予約がなければ警告のログを残して「見つからない」を返す。
 */
class TransportPhaseCommandServiceTest {

    private static final BookingId ID = new BookingId(UUID.fromString("00000000-0000-0000-0000-00000039a201"));
    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-00000039a202");
    private static final UtcInstant COMMITTED_AT = new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"));

    private final InMemoryBookingRepository repository = new InMemoryBookingRepository();
    private final TransportPhaseCommandService service = new TransportPhaseCommandService(repository);

    private final Logger logger = (Logger) LoggerFactory.getLogger(TransportPhaseCommandService.class);
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

    private void confirmed() {
        Booking booking = Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT)
                .booking();
        repository.save(booking, SALES, processedCommand(booking));
    }

    private Booking saved() {
        return repository.findByTrackingNumber(TRACKING_NUMBER).orElseThrow();
    }

    @Test
    void 行4_集荷を記録すると保存して版を1上げ更新者は通知の操作者() {
        confirmed();

        TransportPhaseOutcome outcome = service.recordPickup(ID, MANAGER, TRACKING_NUMBER.value(), 1);

        assertThat(outcome).isEqualTo(TransportPhaseOutcome.RECORDED);
        assertThat(saved().status()).isEqualTo(BookingStatus.IN_TRANSIT);
        assertThat(saved().transportPhase()).isEqualTo(TransportPhase.AFTER_PICKUP);
        assertThat(saved().aggregateVersion()).isEqualTo(1);
        assertThat(repository.updatedBy(ID)).isEqualTo(MANAGER);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 行4_引渡しを記録すると完了にして保存する() {
        confirmed();

        assertThat(service.recordDelivery(ID, MANAGER, TRACKING_NUMBER.value(), 2))
                .isEqualTo(TransportPhaseOutcome.RECORDED);
        assertThat(saved().status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(saved().transportPhase()).isEqualTo(TransportPhase.COMPLETED);
        assertThat(saved().aggregateVersion()).isEqualTo(1);
    }

    @Test
    void 行4_記録済みは書かず版を上げない() {
        confirmed();
        service.recordDelivery(ID, MANAGER, TRACKING_NUMBER.value(), 2);

        assertThat(service.recordPickup(ID, UUID.randomUUID(), TRACKING_NUMBER.value(), 1))
                .isEqualTo(TransportPhaseOutcome.ALREADY_RECORDED);
        assertThat(service.recordDelivery(ID, UUID.randomUUID(), TRACKING_NUMBER.value(), 2))
                .isEqualTo(TransportPhaseOutcome.ALREADY_RECORDED);
        assertThat(saved().aggregateVersion()).isEqualTo(1);
        assertThat(repository.updatedBy(ID)).isEqualTo(MANAGER);
    }

    @Test
    void 行4_取消済みは記録せず書かない() {
        Booking booking = Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT)
                .booking();
        Booking cancelled = Booking.reconstitute(
                booking.id(),
                booking.trackingNumber(),
                BookingStatus.CANCELLED,
                TransportPhase.BEFORE_PICKUP,
                booking.versions(),
                0);
        repository.save(cancelled, SALES, processedCommand(cancelled));

        assertThat(service.recordPickup(ID, MANAGER, TRACKING_NUMBER.value(), 1))
                .isEqualTo(TransportPhaseOutcome.BOOKING_CANCELLED);
        assertThat(saved().aggregateVersion()).isZero();
        assertThat(saved().status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void 行4_申請中は記録せず書かない() {
        Booking booking = Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT)
                .booking();
        Booking pending = Booking.reconstitute(
                booking.id(),
                booking.trackingNumber(),
                BookingStatus.AMENDMENT_PENDING,
                TransportPhase.BEFORE_PICKUP,
                booking.versions(),
                0);
        repository.save(pending, SALES, processedCommand(pending));

        assertThat(service.recordDelivery(ID, MANAGER, TRACKING_NUMBER.value(), 2))
                .isEqualTo(TransportPhaseOutcome.BOOKING_UNDER_REQUEST);
        assertThat(saved().aggregateVersion()).isZero();
    }

    @Test
    void 行4_予約がなければ見つからないを返し警告のログを残す() {
        assertThat(service.recordPickup(ID, MANAGER, TRACKING_NUMBER.value(), 1))
                .isEqualTo(TransportPhaseOutcome.BOOKING_NOT_FOUND);

        assertThat(logs.list).singleElement().satisfies(logged -> {
            assertThat(logged.getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFormattedMessage())
                    .contains(ID.value().toString())
                    .contains(TRACKING_NUMBER.value());
        });
    }
}

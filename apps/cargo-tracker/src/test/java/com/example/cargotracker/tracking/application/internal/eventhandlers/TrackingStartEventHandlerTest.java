package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.routing.interfaces.api.RouteVersionLeg;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.RoutingScheduledLegs;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * DE-07 を受けて追跡を開始する listener（ADR-015、T-INV-11・T-INV-12。Bolt 25）。経路設計の公開 API から確定した経路版の区間を引いて
 * 予定として採用し、追跡記録を作って DE-22 を発行する。同じ予約の再配信は何もしない。経路版が見つからない・区間が不正・企業 ID がない
 * （Bolt 23・24 の形の DE-07）は、再配信で直らない欠けなので、警告のログを残して開始しない（例外にしない。T-58・T-62）。
 */
class TrackingStartEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002511"));
    private static final CompanyId CONSIGNEE = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002512"));

    private final InMemoryTrackingRecordRepository repository = new InMemoryTrackingRecordRepository();
    private final List<Object> published = new ArrayList<>();
    private List<RouteVersionLeg> legs = List.of(
            leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
            leg("V200", "KRPUS", "USLAX", "2026-11-04T00:00:00Z", "2026-11-15T00:00:00Z"));
    private final List<String> queried = new ArrayList<>();

    private final TrackingStartEventHandler handler = new TrackingStartEventHandler(
            repository,
            new RoutingScheduledLegs((caseNumber, versionNo) -> {
                queried.add(caseNumber + "#" + versionNo);
                return legs;
            }),
            published::add,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final Logger logger = (Logger) LoggerFactory.getLogger(TrackingStartEventHandler.class);
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
    void 確定した経路版の区間を予定として採用して追跡を始める() {
        BookingConfirmed event = event(SHIPPER, CONSIGNEE);

        handler.on(event);

        assertThat(queried).containsExactly("RC-2026-0001#1");
        TrackingRecord record = repository.findByBookingId(event.bookingId()).orElseThrow();
        assertThat(record.trackingNumber().value()).isEqualTo("CTABCDEFGH2345");
        assertThat(record.shipperCompanyId()).isEqualTo(SHIPPER);
        assertThat(record.consigneeCompanyId()).isEqualTo(CONSIGNEE);
        assertThat(record.currentStatus()).isEqualTo(TrackingStatus.PICKUP_SCHEDULED);
        assertThat(record.schedule().routingCaseNumber()).isEqualTo("RC-2026-0001");
        assertThat(record.schedule().routeVersionNo()).isEqualTo(1);
        assertThat(record.schedule().legs())
                .extracting(scheduledLeg -> scheduledLeg.voyageNumber())
                .containsExactly("V100", "V200");
        assertThat(record.startedAt().instant()).isEqualTo(NOW);
        assertThat(published)
                .containsExactly(new TrackingStarted("CTABCDEFGH2345", event.bookingId(), new UtcInstant(NOW), 0));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 同じ予約のDE07の再配信は追跡記録を作らずDE22も発行しない() {
        BookingConfirmed event = event(SHIPPER, CONSIGNEE);
        handler.on(event);
        published.clear();

        handler.on(event);

        assertThat(repository.all()).hasSize(1);
        assertThat(published).isEmpty();
    }

    @Test
    void 確定した経路版の区間がなければ警告のログを残して開始しない() {
        legs = List.of();
        BookingConfirmed event = event(SHIPPER, CONSIGNEE);

        handler.on(event);

        assertThat(repository.all()).isEmpty();
        assertThat(published).isEmpty();
        assertWarned(event.bookingId().toString(), "RC-2026-0001");
    }

    @Test
    void 区間が不正なら警告のログを残して開始しない() {
        legs = List.of(
                leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                leg("V200", "CNSHA", "USLAX", "2026-11-04T00:00:00Z", "2026-11-15T00:00:00Z"));
        BookingConfirmed event = event(SHIPPER, CONSIGNEE);

        handler.on(event);

        assertThat(repository.all()).isEmpty();
        assertThat(published).isEmpty();
        assertWarned(event.bookingId().toString(), "つながりません");
    }

    @Test
    void 企業IDのないDE07は警告のログを残して開始しない() {
        BookingConfirmed event = event(null, null);

        handler.on(event);

        assertThat(repository.all()).isEmpty();
        assertThat(published).isEmpty();
        assertThat(queried).isEmpty();
        assertWarned(event.bookingId().toString(), "企業 ID");
    }

    private void assertWarned(String... fragments) {
        assertThat(logs.list).singleElement().satisfies(logged -> {
            assertThat(logged.getLevel()).isEqualTo(Level.WARN);
            assertThat(logged.getFormattedMessage()).contains(fragments);
        });
    }

    private static BookingConfirmed event(CompanyId shipper, CompanyId consignee) {
        return new BookingConfirmed(
                UUID.randomUUID(),
                1,
                "CTABCDEFGH2345",
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "TR-2026-0001",
                "RC-2026-0001",
                1,
                new UtcInstant(Instant.parse("2026-10-08T08:59:00Z")),
                0,
                shipper,
                consignee);
    }

    private static RouteVersionLeg leg(String voyage, String load, String discharge, String departure, String arrival) {
        return new RouteVersionLeg(
                voyage,
                new Location(load),
                new Location(discharge),
                new UtcInstant(Instant.parse(departure)),
                new UtcInstant(Instant.parse(arrival)));
    }
}

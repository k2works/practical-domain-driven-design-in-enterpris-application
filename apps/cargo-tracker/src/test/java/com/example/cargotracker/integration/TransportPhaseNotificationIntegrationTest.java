package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.booking.application.internal.commandservices.TransportPhaseCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.TransportPhaseOutcome;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhase;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneRegistrationOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 追跡の集荷・引渡しから予約の状態と輸送段階までの配信を PostgreSQL 18 で確かめる（Bolt 39g2 の行 7・19・21。T-184。ADR-014）。手入力で実績を
 * 登録し、DE-09・DE-10 → 追跡の listener → 予約の公開 API を経て予約が進み、発行の記録が完了になるまで待つ。クラスに {@code @Transactional} を
 * 付けない（listener はコミットの後に別のトランザクションで動く）。追跡番号はテストごとに乱数で作り、時刻は Clock から相対に作る（実の時計と固定の
 * 日付を比べない）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransportPhaseNotificationIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String LISTENER = "BookingTransportPhaseNotificationEventHandler";
    private static final UserId MANAGER = new UserId(UUID.fromString("00000000-0000-0000-0000-00000039a2f1"));
    private static final Random RANDOM = new Random();

    @Autowired
    BookingRepository bookings;

    @Autowired
    TrackingRecordRepository trackingRecords;

    @Autowired
    TrackingRecordCommandService trackingCommandService;

    @Autowired
    TransportPhaseCommandService transportPhaseCommandService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    /** 確定した予約と、同じ予約 ID・追跡番号の追跡記録。 */
    private record Tracked(Booking booking, TrackingRecord trackingRecord) {}

    private Tracked tracked() {
        com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber number =
                com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber.generate(RANDOM);
        BookingTerms base = BookingFixture.terms();
        BookingTerms terms = new BookingTerms(
                base.transportRequestId(),
                base.transportRequestVersionNo(),
                "TR-2083-" + (100000 + RANDOM.nextInt(900000)),
                UUID.randomUUID(),
                base.quotationNo(),
                base.shipperCompanyId(),
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
        Booking booking = Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        terms,
                        number,
                        BookingFixture.SALES,
                        ago(Duration.ofDays(1)))
                .booking();
        TrackingRecord trackingRecord = TrackingRecord.start(
                        new TrackingNumber(number.value()),
                        booking.id().value(),
                        base.shipperCompanyId(),
                        base.consigneeCompanyId(),
                        TrackingFixture.schedule(),
                        ago(Duration.ofDays(1)))
                .trackingRecord();
        transactionTemplate.executeWithoutResult(status -> {
            bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
            trackingRecords.save(trackingRecord);
        });
        return new Tracked(booking, trackingRecord);
    }

    private UtcInstant now() {
        return new UtcInstant(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    private UtcInstant ago(Duration duration) {
        return new UtcInstant(now().instant().minus(duration));
    }

    private MilestoneRegistrationOutcome register(Tracked tracked, MilestoneKind kind, String reference, Duration ago) {
        TrackingRecord current = trackingRecords
                .findByTrackingNumber(tracked.trackingRecord().trackingNumber())
                .orElseThrow();
        AuthenticatedActor manager =
                AuthenticatedActorFixture.trackingManagerHandling(MANAGER, current.shipperCompanyId());
        return trackingCommandService.registerMilestone(
                new RegisterMilestoneCommand(
                        current.trackingNumber(),
                        current.aggregateVersion(),
                        kind,
                        new Location("JPTYO"),
                        new UtcInstant(now().instant().minus(ago)),
                        new Source(SourceKind.FIELD_RECORD, reference, now()),
                        MANAGER),
                manager);
    }

    private Booking booking(Tracked tracked) {
        return bookings.findByTrackingNumber(tracked.booking().trackingNumber()).orElseThrow();
    }

    private Integer completedPublications(Tracked tracked) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM platform.event_publication WHERE listener_id LIKE ? AND serialized_event LIKE ?"
                        + " AND completion_date IS NOT NULL",
                Integer.class,
                "%" + LISTENER + "%",
                "%" + tracked.booking().trackingNumber().value() + "%");
    }

    @Test
    void 行19と21_集荷と引渡しを登録すると予約が輸送中から完了に進み以後の手入力は保持のみ() {
        Tracked tracked = tracked();

        register(tracked, MilestoneKind.PICKUP, "F-1", Duration.ofHours(3));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(booking(tracked).status()).isEqualTo(BookingStatus.IN_TRANSIT);
            assertThat(booking(tracked).transportPhase()).isEqualTo(TransportPhase.AFTER_PICKUP);
            assertThat(completedPublications(tracked)).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT updated_by FROM booking.booking WHERE id = ?",
                        UUID.class,
                        tracked.booking().id().value()))
                .isEqualTo(MANAGER.value());

        register(tracked, MilestoneKind.DELIVERY, "F-2", Duration.ofHours(1));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(booking(tracked).status()).isEqualTo(BookingStatus.COMPLETED);
            assertThat(booking(tracked).transportPhase()).isEqualTo(TransportPhase.COMPLETED);
            assertThat(completedPublications(tracked)).isEqualTo(2);
        });
        assertThat(booking(tracked).aggregateVersion()).isEqualTo(2);

        // 行 21（【前の決定の変更】(17)）。追跡記録の予約状態の写しは確定のまま。完了後の判定は採用済みの実績から導いた状態で効く
        TrackingRecord afterDelivery = trackingRecords
                .findByTrackingNumber(tracked.trackingRecord().trackingNumber())
                .orElseThrow();
        assertThat(afterDelivery.bookingStatus()).isEqualTo(TrackedBookingStatus.CONFIRMED);
        assertThat(register(tracked, MilestoneKind.DEPARTURE, "F-3", Duration.ofMinutes(30)))
                .isInstanceOfSatisfying(
                        MilestoneRegistrationOutcome.Registered.class,
                        registered -> assertThat(registered.state()).isEqualTo(MilestoneState.RETAINED_ONLY));
    }

    @Test
    void 行7_集荷と引渡しの通知を重ねると後が予約の行を待ち両方とも例外なく終わり予約は完了() {
        Tracked tracked = tracked();
        BookingId id = tracked.booking().id();
        String number = tracked.booking().trackingNumber().value();

        List<TransportPhaseOutcome> outcomes = OverlappingTransactions.run(
                transactionTemplate,
                () -> transportPhaseCommandService.recordPickup(id, MANAGER.value(), number, 1),
                () -> transportPhaseCommandService.recordDelivery(id, MANAGER.value(), number, 2));

        assertThat(outcomes).containsExactly(TransportPhaseOutcome.RECORDED, TransportPhaseOutcome.RECORDED);
        assertThat(booking(tracked).status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking(tracked).aggregateVersion()).isEqualTo(2);
    }

    @Test
    void 行7_引渡しの通知が先なら後から届いた集荷は記録済みで版は1つだけ上がる() {
        Tracked tracked = tracked();
        BookingId id = tracked.booking().id();
        String number = tracked.booking().trackingNumber().value();

        List<TransportPhaseOutcome> outcomes = OverlappingTransactions.run(
                transactionTemplate,
                () -> transportPhaseCommandService.recordDelivery(id, MANAGER.value(), number, 2),
                () -> transportPhaseCommandService.recordPickup(id, MANAGER.value(), number, 1));

        assertThat(outcomes).containsExactly(TransportPhaseOutcome.RECORDED, TransportPhaseOutcome.ALREADY_RECORDED);
        assertThat(booking(tracked).status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking(tracked).aggregateVersion()).isEqualTo(1);
    }

    @Test
    void 行19_予約がない追跡の集荷は予約を作らず発行の記録は完了する() {
        TrackingRecord orphan = TrackingRecord.start(
                        new TrackingNumber(
                                com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber.generate(
                                                RANDOM)
                                        .value()),
                        UUID.randomUUID(),
                        BookingFixture.terms().shipperCompanyId(),
                        BookingFixture.terms().consigneeCompanyId(),
                        TrackingFixture.schedule(),
                        ago(Duration.ofDays(1)))
                .trackingRecord();
        transactionTemplate.executeWithoutResult(status -> trackingRecords.save(orphan));
        AuthenticatedActor manager =
                AuthenticatedActorFixture.trackingManagerHandling(MANAGER, orphan.shipperCompanyId());

        trackingCommandService.registerMilestone(
                new RegisterMilestoneCommand(
                        orphan.trackingNumber(),
                        0,
                        MilestoneKind.PICKUP,
                        new Location("JPTYO"),
                        new UtcInstant(now().instant().minus(Duration.ofHours(1))),
                        new Source(SourceKind.FIELD_RECORD, "F-1", now()),
                        MANAGER),
                manager);

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(jdbc.queryForObject(
                                "SELECT count(*) FROM platform.event_publication WHERE listener_id LIKE ?"
                                        + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                                Integer.class,
                                "%" + LISTENER + "%",
                                "%" + orphan.trackingNumber().value() + "%"))
                        .isEqualTo(1));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM booking.booking WHERE id = ?", Integer.class, orphan.bookingId()))
                .isZero();
    }
}

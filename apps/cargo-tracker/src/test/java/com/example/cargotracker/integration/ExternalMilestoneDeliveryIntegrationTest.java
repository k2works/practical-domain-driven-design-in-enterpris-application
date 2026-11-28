package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 外部データの取込（DE-13）から追跡の下書き・保持のみ・拒否の記録までの配信を PostgreSQL 18 で確かめる（US-12 AC4・AC5、US-14 AC1。Bolt 39g の行 13・24・29〜36・32b〜32d・41。T-184）。
 * ファイルを取り込むか、DE-13 を発行して listener まで流し、発行記録の完了まで待つ。発行記録は受け手で絞る（bolt_39b3b_report.md）。追跡番号はテストごとに乱数で作る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExternalMilestoneDeliveryIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalMilestoneDeliveryIntegrationTest.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String LISTENER = "ExternalMilestoneEventHandler";
    private static final String AUDIT_LISTENER = "ExternalRecordAuditEventHandler";
    private static final String HEADER =
            "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Random RANDOM = new Random();
    private static final Location TOKYO = new Location("JPTYO");

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");
    private UUID sourceId;

    @Autowired
    IngestionService ingestionService;

    @Autowired
    ReceivedRecordDecisionService decisionService;

    @Autowired
    TrackingRecordRepository trackingRecords;

    @Autowired
    IncompleteEventPublications incompleteEventPublications;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @BeforeEach
    void insertApprovedSource() {
        sourceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'MILESTONE', 'APPROVED', 0)",
                sourceId,
                "運送会社-" + UUID.randomUUID());
    }

    @AfterEach
    void suspendSource() {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
    }

    @Nested
    class Bolt39g_配信 {

        @Test
        void 行29_取込のDE13で追跡に下書きが1件でき発行記録が受け手ごとに完了する() {
            TrackingNumber number = startedNumber();

            UUID received = ingestAdopted(row(2, "MS-1", "1", number, occurred()));

            awaitListener(received, 1);
            await().atMost(TIMEOUT)
                    .untilAsserted(() ->
                            assertThat(completed(AUDIT_LISTENER, received)).isEqualTo(1));
            List<Map<String, Object>> rows = milestones(number);
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst())
                    .containsEntry("milestone_no", 1)
                    .containsEntry("state", "DRAFT")
                    .containsEntry("source_kind", "EXTERNAL_RECORD")
                    .containsEntry("source_ref", received.toString())
                    .containsEntry("data_source_id", sourceId)
                    .containsEntry("external_id", "MS-1")
                    .containsEntry("registered_by", steward.userId().value());
            assertThat(rows.getFirst().get("published_at")).isNotNull();
            assertThat(currentStatus(number)).isEqualTo("PICKUP_SCHEDULED");
        }

        @Test
        void 行30_隔離を採用すると同じ主要実績の2件目の下書きができる() {
            TrackingNumber number = startedNumber();
            Instant acquiredAt = acquiredAt();
            Ingested ingested = ingest(
                    row(2, "MS-1", "1", number, occurred(), acquiredAt),
                    row(3, "MS-1", "1", number, occurred().plus(Duration.ofHours(1)), acquiredAt));
            assertThat(ingested.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED, Classification.QUARANTINED);
            awaitListener(ingested.rows().getFirst().receivedRecordId(), 1);
            UUID quarantined = ingested.rows().get(1).receivedRecordId();

            decisionService.adopt(steward, new AdoptReceivedRecordCommand(quarantined, null));

            awaitListener(quarantined, 1);
            assertThat(milestones(number))
                    .extracting(row -> row.get("source_ref"))
                    .containsExactly(
                            ingested.rows().getFirst().receivedRecordId().toString(), quarantined.toString());
        }

        @Test
        void 行31_同じ原本の再送はDE13が出ず実績は増えない() {
            TrackingNumber number = startedNumber();
            Instant acquiredAt = acquiredAt();
            IngestedRow row = row(2, "MS-1", "1", number, occurred(), acquiredAt);
            Ingested first = ingest(row);
            awaitListener(first.rows().getFirst().receivedRecordId(), 1);

            Ingested second = ingest(row);

            assertThat(second.rows()).extracting(RowResult::classification).containsExactly(Classification.DUPLICATE);
            assertThat(milestones(number)).hasSize(1);
        }

        @Test
        void 行32_同じイベントの再配信は実績を増やさず発行記録が完了する() {
            TrackingNumber number = startedNumber();
            UUID received = ingestAdopted(row(2, "MS-1", "1", number, occurred()));
            awaitListener(received, 1);

            jdbc.update(
                    "UPDATE platform.event_publication SET completion_date = NULL"
                            + " WHERE listener_id LIKE ? AND serialized_event LIKE ?",
                    "%" + LISTENER + "%",
                    "%" + received + "%");
            incompleteEventPublications.resubmitIncompletePublications(
                    publication -> publication.getEvent() instanceof MilestoneAdoptedValueAccepted accepted
                            && accepted.source().reference().equals(received.toString()));

            awaitListener(received, 1);
            assertThat(milestones(number)).hasSize(1);
        }

        @Test
        void 行32b_下書きの後に取消済みになって同じDE13が届いても実績は変わらず拒否の記録を作らない() {
            TrackingNumber number = startedNumber();
            MilestoneAdoptedValueAccepted event = event(number, "MS-1");
            publish(event);
            awaitPublished(event, 1);
            setBookingStatus(number, "CANCELLED");

            publish(event);

            awaitPublished(event, 2);
            assertThat(milestones(number))
                    .singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("state", "DRAFT"));
            assertThat(rejected(event)).isZero();
        }

        @Test
        void 行32c_下書きの後に完了になって同じDE13が届いても保持のみを足さず下書きのまま() {
            TrackingNumber number = startedNumber();
            MilestoneAdoptedValueAccepted event = event(number, "MS-1");
            publish(event);
            awaitPublished(event, 1);
            setBookingStatus(number, "COMPLETED");

            publish(event);

            awaitPublished(event, 2);
            assertThat(milestones(number))
                    .singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("state", "DRAFT"));
        }

        @Test
        void 行32d_拒否の記録の後に追跡記録ができて同じDE13が届いても実績にせず拒否の記録も増えない() {
            TrackingNumber number = randomNumber();
            MilestoneAdoptedValueAccepted event = event(number, "MS-1");
            publish(event);
            awaitPublished(event, 1);
            assertThat(rejected(event)).isEqualTo(1);
            start(number);

            publish(event);

            awaitPublished(event, 2);
            assertThat(milestones(number)).isEmpty();
            assertThat(rejected(event)).isEqualTo(1);
        }

        @Test
        void 行33_知らない追跡番号は拒否の記録に1件残り発行記録が完了する() {
            TrackingNumber number = randomNumber();

            UUID received = ingestAdopted(row(2, "MS-1", "1", number, occurred()));

            awaitListener(received, 1);
            assertThat(rejectedByRef(received.toString()))
                    .containsEntry("reason", "UNKNOWN_TRACKING_NUMBER")
                    .containsEntry("tracking_number", number.value())
                    .containsEntry("data_source_id", sourceId)
                    .containsEntry("external_id", "MS-1");
        }

        @Test
        void 行34_取消済みは実績0件で拒否の記録に1件残り発行記録が完了する() {
            TrackingNumber number = startedNumber();
            setBookingStatus(number, "CANCELLED");

            UUID received = ingestAdopted(row(2, "MS-1", "1", number, occurred()));

            awaitListener(received, 1);
            assertThat(milestones(number)).isEmpty();
            assertThat(rejectedByRef(received.toString())).containsEntry("reason", "BOOKING_CANCELLED");
            assertThat(version(number)).isZero();
        }

        @Test
        void 行35_完了後は保持のみの実績ができて現在状態は引渡し済みのまま() {
            TrackingNumber number = startedNumber();
            TrackingRecord loaded = trackingRecords.findByTrackingNumber(number).orElseThrow();
            UtcInstant now = new UtcInstant(clock.instant());
            TrackingRecord delivered = loaded.registerMilestone(
                            MilestoneKind.DELIVERY,
                            TOKYO,
                            now,
                            new Source(SourceKind.FIELD_RECORD, "F-1", now),
                            steward.userId(),
                            now)
                    .trackingRecord();
            transactionTemplate.executeWithoutResult(status -> trackingRecords.update(delivered));

            UUID received = ingestAdopted(row(2, "MS-1", "1", number, occurred()));

            awaitListener(received, 1);
            assertThat(milestones(number))
                    .extracting(row -> row.get("state"))
                    .containsExactly("ADOPTED", "RETAINED_ONLY");
            assertThat(currentStatus(number)).isEqualTo("DELIVERED");
        }

        @Test
        void 行36_届いた後に追跡記録ができても拒否の記録のままで新しい外部版が届けば下書きになる() {
            TrackingNumber number = randomNumber();
            UUID first = ingestAdopted(row(2, "MS-1", "1", number, occurred()));
            awaitListener(first, 1);
            start(number);
            assertThat(milestones(number)).isEmpty();

            UUID second = ingestAdopted(row(2, "MS-1", "2", number, occurred()));

            awaitListener(second, 1);
            assertThat(rejectedByRef(first.toString())).containsEntry("reason", "UNKNOWN_TRACKING_NUMBER");
            assertThat(milestones(number))
                    .singleElement()
                    .satisfies(row -> assertThat(row)
                            .containsEntry("state", "DRAFT")
                            .containsEntry("source_ref", second.toString()));
        }

        @Test
        void 行13_外部版をまたいで追跡番号が変わると元の下書きは変わらず新しい番号に下書きができる() {
            // Bolt 39h の行 11（【前の決定の変更】(26)）。取込は追跡番号の違う後の外部版を差異にして DE-13 を出さないので、追跡の側の守り
            // （D-115）は DE-13 を直接出して確かめる
            TrackingNumber original = startedNumber();
            TrackingNumber corrected = startedNumber();
            MilestoneAdoptedValueAccepted first = event(original, "MS-1", "1");
            publish(first);
            awaitPublished(first, 1);

            MilestoneAdoptedValueAccepted second = event(corrected, "MS-1", "2");
            publish(second);

            awaitPublished(second, 1);
            assertThat(milestones(original))
                    .singleElement()
                    .satisfies(row -> assertThat(row)
                            .containsEntry("state", "DRAFT")
                            .containsEntry("source_ref", first.source().reference()));
            assertThat(milestones(corrected))
                    .singleElement()
                    .satisfies(row -> assertThat(row)
                            .containsEntry("source_ref", second.source().reference()));
        }

        @Test
        void 行24_情報源を停止した後に届いたDE13も下書きで受ける() {
            TrackingNumber number = startedNumber();
            jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
            MilestoneAdoptedValueAccepted event = event(number, "MS-1");

            publish(event);

            awaitPublished(event, 1);
            assertThat(milestones(number))
                    .singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("state", "DRAFT"));
        }

        @Test
        void 行41_1つのファイルの200行が同じ追跡番号に集中しても下書き200件で実績番号が連続し発行記録がすべて完了する() {
            TrackingNumber number = startedNumber();
            Instant acquiredAt = acquiredAt();
            IngestedRow[] rows = IntStream.rangeClosed(1, 200)
                    .mapToObj(i -> row(i + 1, "MS-" + i, "1", number, occurred(), acquiredAt))
                    .toArray(IngestedRow[]::new);
            long started = System.nanoTime();

            Ingested ingested = ingest(rows);

            assertThat(ingested.rows()).extracting(RowResult::classification).containsOnly(Classification.ADOPTED);
            await().atMost(Duration.ofSeconds(60))
                    .pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> {
                        assertThat(milestones(number)).hasSize(200);
                        assertThat(completedForTracking(number)).isEqualTo(200);
                    });
            long seconds = Duration.ofNanos(System.nanoTime() - started).toMillis();
            LOG.info("Bolt 39g 行 41: 同じ追跡番号の 200 行の配信にかかった時間 {} ms", seconds);
            assertThat(milestones(number))
                    .extracting(row -> row.get("milestone_no"))
                    .containsExactlyElementsOf(
                            IntStream.rangeClosed(1, 200).boxed().toList());
            assertThat(milestones(number)).allSatisfy(row -> assertThat(row).containsEntry("state", "DRAFT"));
        }
    }

    private Instant acquiredAt() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private Instant occurred() {
        return Instant.parse("2026-11-19T14:00:00Z");
    }

    private TrackingNumber randomNumber() {
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new TrackingNumber(value.toString());
    }

    private void start(TrackingNumber number) {
        TrackingRecord trackingRecord = TrackingRecord.start(
                        number,
                        UUID.randomUUID(),
                        new CompanyId(UUID.randomUUID()),
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord();
        transactionTemplate.executeWithoutResult(status -> trackingRecords.save(trackingRecord));
    }

    private TrackingNumber startedNumber() {
        TrackingNumber number = randomNumber();
        start(number);
        return number;
    }

    private void setBookingStatus(TrackingNumber number, String status) {
        jdbc.update(
                "UPDATE tracking.tracking_record SET booking_status = ? WHERE tracking_number = ?",
                status,
                number.value());
    }

    private IngestedRow row(int line, String externalId, String version, TrackingNumber number, Instant occurredAt) {
        return row(line, externalId, version, number, occurredAt, acquiredAt());
    }

    private IngestedRow row(
            int line,
            String externalId,
            String version,
            TrackingNumber number,
            Instant occurredAt,
            Instant acquiredAt) {
        String payload =
                externalId + "," + version + "," + acquiredAt + "," + number.value() + ",DEPARTURE,JPTYO," + occurredAt;
        MilestoneValues values = new MilestoneValues(
                new TrackingNumberValue(number.value()),
                MilestoneKindValue.DEPARTURE,
                TOKYO,
                new UtcInstant(occurredAt));
        return new IngestedRow(
                line, line, externalId, version, acquiredAt, values, payload.getBytes(StandardCharsets.UTF_8));
    }

    private Ingested ingest(IngestedRow... rows) {
        StringBuilder content = new StringBuilder(HEADER);
        for (IngestedRow row : rows) {
            content.append('\n').append(new String(row.payload(), StandardCharsets.UTF_8));
        }
        IngestFileCommand command = new IngestFileCommand(
                sourceId,
                content.toString().getBytes(StandardCharsets.UTF_8),
                rows.length,
                new ArrayList<>(List.of(rows)),
                List.of(),
                null);
        IngestionOutcome outcome = ingestionService.ingest(steward, command);
        assertThat(outcome).isInstanceOf(Ingested.class);
        return (Ingested) outcome;
    }

    private UUID ingestAdopted(IngestedRow row) {
        Ingested ingested = ingest(row);
        assertThat(ingested.rows()).extracting(RowResult::classification).containsExactly(Classification.ADOPTED);
        return ingested.rows().getFirst().receivedRecordId();
    }

    private MilestoneAdoptedValueAccepted event(TrackingNumber number, String externalId) {
        return event(number, externalId, "1");
    }

    private MilestoneAdoptedValueAccepted event(TrackingNumber number, String externalId, String infoVersion) {
        UtcInstant at = new UtcInstant(acquiredAt());
        return new MilestoneAdoptedValueAccepted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourceId,
                new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), at),
                number.value(),
                "DEPARTURE",
                TOKYO,
                new UtcInstant(occurred()),
                externalId,
                infoVersion,
                steward.userId(),
                steward.companyId(),
                at);
    }

    private void publish(MilestoneAdoptedValueAccepted event) {
        transactionTemplate.executeWithoutResult(status -> events.publishEvent(event));
    }

    private void awaitListener(UUID receivedRecordId, int count) {
        await().atMost(TIMEOUT)
                .untilAsserted(
                        () -> assertThat(completed(LISTENER, receivedRecordId)).isEqualTo(count));
    }

    private void awaitPublished(MilestoneAdoptedValueAccepted event, int count) {
        awaitListener(UUID.fromString(event.source().reference()), count);
    }

    private int completed(String listener, UUID receivedRecordId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE listener_id LIKE ? AND serialized_event LIKE ?"
                        + " AND event_type LIKE '%MilestoneAdoptedValueAccepted' AND completion_date IS NOT NULL",
                Integer.class, "%" + listener + "%", "%" + receivedRecordId + "%");
        return count == null ? 0 : count;
    }

    private int completedForTracking(TrackingNumber number) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE listener_id LIKE ? AND serialized_event LIKE ?"
                        + " AND completion_date IS NOT NULL",
                Integer.class,
                "%" + LISTENER + "%",
                "%" + number.value() + "%");
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> milestones(TrackingNumber number) {
        return jdbc.queryForList(
                "SELECT milestone_no, state, source_kind, source_ref, data_source_id, external_id, published_at,"
                        + " registered_by FROM tracking.milestone WHERE tracking_number = ? ORDER BY milestone_no",
                number.value());
    }

    private int rejected(MilestoneAdoptedValueAccepted event) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tracking.rejected_milestone WHERE source_ref = ?",
                Integer.class,
                event.source().reference());
        return count == null ? 0 : count;
    }

    private Map<String, Object> rejectedByRef(String reference) {
        return jdbc.queryForMap(
                "SELECT reason, tracking_number, data_source_id, external_id FROM tracking.rejected_milestone"
                        + " WHERE source_ref = ?",
                reference);
    }

    private String currentStatus(TrackingNumber number) {
        return jdbc.queryForObject(
                "SELECT current_status FROM tracking.tracking_record WHERE tracking_number = ?",
                String.class,
                number.value());
    }

    private Long version(TrackingNumber number) {
        return jdbc.queryForObject(
                "SELECT version FROM tracking.tracking_record WHERE tracking_number = ?", Long.class, number.value());
    }
}

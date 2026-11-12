package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler;
import com.example.cargotracker.routing.application.internal.eventhandlers.VoyageUpdateEventHandler;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.eventhandlers.VoyageArrivalEventHandler;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DE-12 を受けて追跡記録の最新の到着見込みを動かす配信を PostgreSQL 18 で確かめる（Bolt 39g4b の行 14。前例 {@code ExternalMilestoneDeliveryIntegrationTest}・
 * {@code VoyageUpdateDeliveryIntegrationTest}）。航海のファイルの取込で採用した DE-12 が、経路設計・監査・追跡の 3 つの購読に配られ、すべての発行記録が完了する。
 * 航海番号と追跡番号はテストごとに一意にし、港は JPTYO → USLAX にする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VoyageArrivalDeliveryIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Instant DEPARTURE = Instant.parse("2097-12-01T00:00:00Z");
    private static final Instant ARRIVAL = Instant.parse("2097-12-20T00:00:00Z");
    private static final Instant DELAYED_ARRIVAL = Instant.parse("2097-12-21T08:00:00Z");

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");
    private final String voyageNumber = "D-" + UUID.randomUUID().toString().substring(0, 12);

    @Autowired
    IngestionService ingestionService;

    @Autowired
    TrackingRecordRepository trackingRecords;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Test
    void 行14_取込のコミットの後に3つの購読に配られ発行記録が完了し追跡記録の見込みが動く() {
        UUID sourceId = insertApprovedSource();
        TrackingNumber trackingNumber = startedTracking();
        try {
            Ingested result = ingested(ingestionService.ingest(steward, voyageFile(sourceId, acquiredAt())));
            UUID receivedRecordId = result.rows().getFirst().receivedRecordId();

            for (Class<?> listener : List.of(
                    VoyageUpdateEventHandler.class,
                    ExternalRecordAuditEventHandler.class,
                    VoyageArrivalEventHandler.class)) {
                await().atMost(TIMEOUT)
                        .untilAsserted(() -> assertThat(completedPublications(receivedRecordId, listener))
                                .as(listener.getSimpleName())
                                .isEqualTo(1));
            }

            TrackingRecord found =
                    trackingRecords.findByTrackingNumber(trackingNumber).orElseThrow();
            assertThat(found.latestEta()).isEqualTo(new UtcInstant(DELAYED_ARRIVAL));
            assertThat(found.originalEta()).isEqualTo(new UtcInstant(ARRIVAL));
            assertThat(found.voyageArrival())
                    .hasValueSatisfying(arrival ->
                            assertThat(arrival.order().sourceReference()).isEqualTo(receivedRecordId.toString()));
            assertThat(found.aggregateVersion()).isEqualTo(1L);
        } finally {
            deleteTracking(trackingNumber);
            jdbc.update("DELETE FROM routing.port_call WHERE voyage_number = ?", voyageNumber);
            jdbc.update("DELETE FROM routing.voyage WHERE voyage_number = ?", voyageNumber);
            jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
        }
    }

    private TrackingNumber startedTracking() {
        Random random = new Random();
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        TrackingNumber trackingNumber = new TrackingNumber(value.toString());
        TrackingRecord trackingRecord = TrackingRecord.start(
                        trackingNumber,
                        UUID.randomUUID(),
                        new CompanyId(UUID.randomUUID()),
                        new CompanyId(UUID.randomUUID()),
                        new Schedule(
                                "RC-2026-0001",
                                1,
                                List.of(new ScheduledLeg(
                                        voyageNumber,
                                        new Location("JPTYO"),
                                        new Location("USLAX"),
                                        new UtcInstant(DEPARTURE),
                                        new UtcInstant(ARRIVAL)))),
                        new UtcInstant(clock.instant()))
                .trackingRecord();
        transactionTemplate.executeWithoutResult(status -> trackingRecords.save(trackingRecord));
        return trackingNumber;
    }

    private int completedPublications(UUID receivedRecordId, Class<?> listener) {
        Integer completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ? AND serialized_event LIKE ?"
                        + " AND listener_id LIKE ? AND completion_date IS NOT NULL",
                Integer.class,
                "%" + receivedRecordId + "%",
                "%" + voyageNumber + "%",
                "%" + listener.getSimpleName() + "%");
        return completed == null ? 0 : completed;
    }

    private IngestFileCommand voyageFile(UUID sourceId, Instant acquiredAt) {
        VoyageValues values = new VoyageValues(
                voyageNumber,
                List.of(
                        new PortCallValue(new Location("JPTYO"), null, new UtcInstant(DEPARTURE)),
                        new PortCallValue(new Location("USLAX"), new UtcInstant(DELAYED_ARRIVAL), null)));
        String line = voyageNumber + ",1," + acquiredAt + ",JPTYO;;" + DEPARTURE + "|USLAX;" + DELAYED_ARRIVAL + ";";
        IngestedRow row =
                new IngestedRow(2, 2, voyageNumber, "1", acquiredAt, values, line.getBytes(StandardCharsets.UTF_8));
        String content = "external_id,external_version,acquired_at,port_calls\n" + line;
        return new IngestFileCommand(
                sourceId, content.getBytes(StandardCharsets.UTF_8), 1, List.of(row), List.of(), null);
    }

    private Instant acquiredAt() {
        return clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
    }

    private static Ingested ingested(IngestionOutcome outcome) {
        assertThat(outcome).isInstanceOf(Ingested.class);
        return (Ingested) outcome;
    }

    private UUID insertApprovedSource() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'VOYAGE', 'APPROVED', 0)",
                id,
                "船会社-" + UUID.randomUUID());
        return id;
    }

    private void deleteTracking(TrackingNumber trackingNumber) {
        jdbc.update("DELETE FROM tracking.scheduled_leg WHERE tracking_number = ?", trackingNumber.value());
        jdbc.update("DELETE FROM tracking.tracking_record WHERE tracking_number = ?", trackingNumber.value());
    }
}

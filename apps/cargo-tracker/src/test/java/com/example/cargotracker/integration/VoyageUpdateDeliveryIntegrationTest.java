package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.routing.application.internal.eventhandlers.VoyageUpdateEventHandler;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DE-12 を受けて経路設計の航海を書く配信を PostgreSQL 18 で確かめる（Bolt 39e の行 21・22。T-184）。取込と採用の判断のコミットの後に listener が非同期に受け、
 * `routing.voyage` と `routing.port_call` に書き、発行記録が完了する。航海番号はテストごとに一意にし、港は JPTYO → USLAX にする（共有の DB の候補につながらない）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VoyageUpdateDeliveryIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String LISTENER = VoyageUpdateEventHandler.class.getSimpleName();
    private static final Instant DEPARTURE = Instant.parse("2097-12-01T00:00:00Z");
    private static final Instant ARRIVAL = Instant.parse("2097-12-20T00:00:00Z");
    private static final Instant CHANGED_ARRIVAL = Instant.parse("2097-12-21T00:00:00Z");

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
    ReceivedRecordDecisionService decisionService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Nested
    class Bolt39e_配信 {

        @Test
        void 行21_取込のコミットの後に航海と寄港が書かれ発行記録が完了する() {
            UUID sourceId = insertApprovedSource();
            try {
                Instant acquiredAt = acquiredAt();
                Ingested result = ingested(
                        ingestionService.ingest(steward, voyageFile(sourceId, voyageRow(2, acquiredAt, ARRIVAL))));
                UUID receivedRecordId = result.rows().getFirst().receivedRecordId();

                awaitCompleted(receivedRecordId);

                Map<String, Object> voyage = voyageRow();
                assertThat(voyage)
                        .containsEntry("adopted_info_version", "1")
                        .containsEntry("source_kind", "EXTERNAL_RECORD")
                        .containsEntry("source_ref", receivedRecordId.toString())
                        .containsEntry("version", 0L);
                assertThat(instant(voyage.get("acquired_at"))).isEqualTo(acquiredAt);
                assertThat(instant(voyage.get("published_at"))).isEqualTo(ingestedAt(result.ingestionId()));
                assertThat(portCalls()).containsExactly("JPTYO:" + DEPARTURE, "USLAX:" + ARRIVAL);
            } finally {
                deleteVoyage();
                removeSource(sourceId);
            }
        }

        @Test
        void 行22_隔離を採用すると同じ情報版で公開した時刻が新しくなり航海が書き換わる() {
            UUID sourceId = insertApprovedSource();
            try {
                Instant acquiredAt = acquiredAt();
                Ingested result = ingested(ingestionService.ingest(
                        steward,
                        voyageFile(
                                sourceId,
                                voyageRow(2, acquiredAt, ARRIVAL),
                                voyageRow(3, acquiredAt, CHANGED_ARRIVAL))));
                assertThat(result.rows())
                        .extracting(RowResult::classification)
                        .containsExactly(Classification.ADOPTED, Classification.QUARANTINED);
                UUID adopted = result.rows().get(0).receivedRecordId();
                UUID quarantined = result.rows().get(1).receivedRecordId();
                awaitCompleted(adopted);
                Instant firstPublishedAt = instant(voyageRow().get("published_at"));

                DecisionOutcome outcome =
                        decisionService.adopt(steward, new AdoptReceivedRecordCommand(quarantined, null));

                assertThat(outcome).isEqualTo(new DecisionOutcome.AdoptedQuarantine(quarantined));
                awaitCompleted(quarantined);
                Map<String, Object> voyage = voyageRow();
                assertThat(voyage)
                        .containsEntry("adopted_info_version", "1")
                        .containsEntry("source_ref", quarantined.toString())
                        .containsEntry("version", 1L);
                assertThat(instant(voyage.get("published_at"))).isAfter(firstPublishedAt);
                assertThat(portCalls()).containsExactly("JPTYO:" + DEPARTURE, "USLAX:" + CHANGED_ARRIVAL);
            } finally {
                deleteVoyage();
                removeSource(sourceId);
            }
        }
    }

    private void awaitCompleted(UUID receivedRecordId) {
        await().atMost(TIMEOUT)
                .untilAsserted(() ->
                        assertThat(completedPublications(receivedRecordId)).isEqualTo(1));
    }

    private int completedPublications(UUID receivedRecordId) {
        Integer completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ? AND serialized_event LIKE ?"
                        + " AND listener_id LIKE ? AND completion_date IS NOT NULL",
                Integer.class,
                "%" + receivedRecordId + "%",
                "%" + voyageNumber + "%",
                "%" + LISTENER + "%");
        return completed == null ? 0 : completed;
    }

    private Map<String, Object> voyageRow() {
        return jdbc.queryForMap(
                "SELECT adopted_info_version, source_kind, source_ref, acquired_at, published_at, version"
                        + " FROM routing.voyage WHERE voyage_number = ?",
                voyageNumber);
    }

    private List<String> portCalls() {
        return jdbc.query(
                "SELECT port_unlocode, arrival_at, departure_at FROM routing.port_call WHERE voyage_number = ? ORDER BY call_no",
                (rows, rowNumber) -> {
                    OffsetDateTime time = rows.getObject("departure_at", OffsetDateTime.class);
                    if (time == null) {
                        time = rows.getObject("arrival_at", OffsetDateTime.class);
                    }
                    return rows.getString("port_unlocode") + ":" + time.toInstant();
                },
                voyageNumber);
    }

    private static Instant instant(Object timestamp) {
        return timestamp instanceof OffsetDateTime offset
                ? offset.toInstant()
                : ((java.sql.Timestamp) timestamp).toInstant();
    }

    private Instant ingestedAt(UUID ingestionId) {
        return instant(jdbc.queryForObject(
                "SELECT ingested_at FROM external_data.ingestion WHERE id = ?", Object.class, ingestionId));
    }

    private IngestedRow voyageRow(int lineNumber, Instant acquiredAt, Instant arrival) {
        VoyageValues values = new VoyageValues(
                voyageNumber,
                List.of(
                        new PortCallValue(new Location("JPTYO"), null, new UtcInstant(DEPARTURE)),
                        new PortCallValue(new Location("USLAX"), new UtcInstant(arrival), null)));
        String line = voyageNumber + ",1," + acquiredAt + ",JPTYO;;" + DEPARTURE + "|USLAX;" + arrival + ";";
        return new IngestedRow(
                lineNumber, lineNumber, voyageNumber, "1", acquiredAt, values, line.getBytes(StandardCharsets.UTF_8));
    }

    private static IngestFileCommand voyageFile(UUID sourceId, IngestedRow... rows) {
        StringBuilder content = new StringBuilder("external_id,external_version,acquired_at,port_calls");
        for (IngestedRow row : rows) {
            content.append('\n').append(new String(row.payload(), StandardCharsets.UTF_8));
        }
        return new IngestFileCommand(
                sourceId,
                content.toString().getBytes(StandardCharsets.UTF_8),
                rows.length,
                List.of(rows),
                List.of(),
                null);
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

    private void removeSource(UUID sourceId) {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
    }

    private void deleteVoyage() {
        jdbc.update("DELETE FROM routing.port_call WHERE voyage_number = ?", voyageNumber);
        jdbc.update("DELETE FROM routing.voyage WHERE voyage_number = ?", voyageNumber);
    }
}

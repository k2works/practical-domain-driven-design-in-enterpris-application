package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * H2 のイベント発行記録の大きさ（Bolt 39b3b の行 10。「人に諮ること」3 の決定、ADR-007・ADR-013）。ローカル起動・デモ環境と同じ dev
 * プロファイルの H2 で、寄港 60 の航海を取り込むと DE-12 がイベント発行記録に入り、取込がコミットされ、監査の listener が監査記録を書く。
 * Spring Modulith の H2 の定義（{@code serialized_event VARCHAR(4000)}）のままでは DE-12 が入らず、取込全体が巻き戻る。
 *
 * <p>配信を確かめるので取込のサービス自身のトランザクションをコミットする。確かめる行は自分の受信記録 ID と UUID を含む提供元の名前で絞り、
 * 情報源は最後に停止中にして承認済みのまま残さない（取込が参照するので消せない）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class H2EventPublicationCapacityTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** 寄港の時刻の始まり（寄港ごとに 1 日ずらす）。 */
    private static final Instant FIRST_CALL = Instant.parse("2026-12-01T00:00:00Z");

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    @Autowired
    IngestionService ingestionService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Test
    void 行10_H2で寄港60の航海を取り込むとDE12が発行記録に入り取込がコミットされ監査記録ができる() {
        UUID sourceId = insertApprovedSource("船会社-" + UUID.randomUUID());
        try {
            IngestionOutcome outcome = ingestionService.ingest(steward, voyageFile(sourceId));

            assertThat(outcome).isInstanceOf(Ingested.class);
            Ingested ingested = (Ingested) outcome;
            assertThat(ingested.rows())
                    .singleElement()
                    .satisfies(row -> assertThat(row.classification()).isEqualTo(Classification.ADOPTED));
            UUID receivedRecordId = ingested.rows().getFirst().receivedRecordId();
            assertThat(count("SELECT COUNT(*) FROM external_data.ingestion WHERE id = ?", ingested.ingestionId()))
                    .isEqualTo(1);
            await().atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(count(
                                    "SELECT COUNT(*) FROM identity.audit_record"
                                            + " WHERE action = 'EXTERNAL_RECORD_ADOPTED' AND target_type = 'RECEIVED_RECORD'"
                                            + " AND target_id = ?",
                                    receivedRecordId.toString()))
                            .isEqualTo(1));
            await().atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(count(
                                    "SELECT COUNT(*) FROM platform.event_publication WHERE event_type = ?"
                                            + " AND serialized_event LIKE ? AND listener_id LIKE ?"
                                            + " AND completion_date IS NOT NULL",
                                    VoyageAdoptedValueUpdated.class.getName(),
                                    "%" + receivedRecordId + "%",
                                    "%" + ExternalRecordAuditEventHandler.class.getSimpleName() + "%"))
                            .isEqualTo(1));
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行10_H2のイベント発行記録は広げた列に受け手と直列化したイベントの索引を持つ() {
        assertThat(jdbc.queryForObject(
                        "SELECT character_maximum_length FROM information_schema.columns"
                                + " WHERE table_schema = 'platform' AND table_name = 'event_publication'"
                                + " AND column_name = 'serialized_event'",
                        Long.class))
                .isEqualTo(1_000_000L);
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.index_columns WHERE table_schema = 'platform'"
                                + " AND table_name = 'event_publication'"
                                + " AND index_name = 'event_publication_by_listener_id_and_serialized_event_idx'"
                                + " ORDER BY ordinal_position",
                        String.class))
                .containsExactly("listener_id", "serialized_event");
    }

    /** 寄港 60（上限）の航海のファイル（同じ港で 1 日ずつずらした到着と出発。取得時刻は取込時刻より前。T-230）。 */
    private IngestFileCommand voyageFile(UUID sourceId) {
        Instant acquiredAt = clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
        List<PortCallValue> calls = new ArrayList<>();
        for (int i = 0; i < VoyageValues.MAX_PORT_CALLS; i++) {
            Instant arrival = FIRST_CALL.plus(Duration.ofDays(i));
            calls.add(new PortCallValue(
                    new Location(i % 2 == 0 ? "JPTYO" : "USLAX"),
                    new UtcInstant(arrival),
                    new UtcInstant(arrival.plus(Duration.ofHours(12)))));
        }
        String portCalls = calls.stream()
                .map(call -> call.port().unLocode() + ";" + call.arrivalAt().instant() + ";"
                        + call.departureAt().instant())
                .collect(Collectors.joining("|"));
        String line = "V2611E,1," + acquiredAt + "," + portCalls;
        IngestedRow row = new IngestedRow(
                2,
                2,
                "V2611E",
                "1",
                acquiredAt,
                new VoyageValues("V2611E", calls),
                line.getBytes(StandardCharsets.UTF_8));
        return new IngestFileCommand(
                sourceId,
                ("external_id,external_version,acquired_at,port_calls\n" + line).getBytes(StandardCharsets.UTF_8),
                1,
                List.of(row),
                List.of(),
                null);
    }

    private UUID insertApprovedSource(String provider) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'VOYAGE', 'APPROVED', 0)",
                id,
                provider);
        return id;
    }

    /** コミットした情報源を承認済みのまま残さない。取込が残っていれば消せないので、先に停止中にする。 */
    private void removeSource(UUID sourceId) {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
        jdbc.update(
                "DELETE FROM external_data.data_source WHERE id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM external_data.ingestion WHERE data_source_id = ?)",
                sourceId,
                sourceId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}

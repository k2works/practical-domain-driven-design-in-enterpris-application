package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
import com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 取込の経路が握る接続とコミットの量を、1,000 件の主要実績のファイルで PostgreSQL 18 で確かめる（Bolt 39b4 の行 3・4・7。ADR-004 の
 * 改訂の「接続」と「量」。1 万件の時間は {@code ExternalDataIngestionPerformanceTest}）。取込を呼んだスレッドが同時に握る接続は、外側の
 * 取込のトランザクションと原本の保存の独立したトランザクションの 2 本で、コミットは「行の数 ＋ 2」（行ごとの原本、ファイル全体の原本、
 * 外側）。数えるのは {@link ConnectionUsageRecorder} で、監査の listener の非同期のスレッドは数えない。
 *
 * <p>コミットを数えるので取込のサービス自身のトランザクションをコミットする。取込・原本・受信記録・採用値・発行記録は共有の DB に残るので、
 * 確かめる行は自分の取込 ID と、UUID を含む提供元の名前で絞り、全体で数えない（test_strategy の数えるテストの規約）。テストの終わりに
 * 自分の取込の監査の発行記録がすべて完了になるまで待ち、後のテストに配信を残さない（T-184）。情報源は最後に停止中にする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExternalDataIngestionConnectionTest {

    static final String MILESTONE_HEADER =
            "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";

    private static final int ROWS = 1_000;

    /** 1,000 件の監査の配信を待つ上限（設計の決定「共有の DB の他の行」。超えたら落とす）。 */
    private static final Duration DELIVERY_TIMEOUT = Duration.ofMinutes(2);

    /** 追跡番号の文字（{@code CT} の後の 12 桁。0・O・1・I・L を除く）。 */
    private static final String TRACKING_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final String AUDIT_LISTENER = ExternalRecordAuditEventHandler.class.getSimpleName();

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    @Autowired
    IngestionService ingestionService;

    @Autowired
    ConnectionUsageRecorder connectionUsage;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Test
    void 行3_取込を呼んだスレッドが同時に握る接続は外側と原本の保存の2本を超えない() {
        UUID sourceId = insertApprovedSource(newProvider(), "MILESTONE");
        try {
            Ingested result = recordedIngest(milestoneFile(sourceId, ROWS, acquiredAt()));

            assertThat(result.receivedCount()).isEqualTo(ROWS);
            assertThat(connectionUsage.peakConnections()).isEqualTo(2);
            awaitCompletedAuditPublications(result);
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行4_コミットは行の数に2を足した数で同じファイルの再取込もすべて重複で同じ数になる() {
        UUID sourceId = insertApprovedSource(newProvider(), "MILESTONE");
        try {
            byte[] csv = milestoneCsv(ROWS, acquiredAt());

            Ingested first = recordedIngest(assemble(sourceId, csv));
            int firstCommits = connectionUsage.commits();
            Ingested second = recordedIngest(assemble(sourceId, csv));
            int secondCommits = connectionUsage.commits();

            assertThat(first.countOf(Classification.ADOPTED)).isEqualTo(ROWS);
            assertThat(second.countOf(Classification.DUPLICATE)).isEqualTo(ROWS);
            assertThat(firstCommits).isEqualTo(ROWS + 2);
            assertThat(secondCommits).isEqualTo(ROWS + 2);
            awaitCompletedAuditPublications(first);
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行7_前に別の提供元の取込をコミットしておいても数える件数は自分の取込の分だけになる() {
        UUID otherSourceId = insertApprovedSource(newProvider(), "MILESTONE");
        String provider = newProvider();
        UUID sourceId = insertApprovedSource(provider, "MILESTONE");
        try {
            Ingested other = ingested(ingestionService.ingest(steward, milestoneFile(otherSourceId, 3, acquiredAt())));

            Ingested result = ingested(ingestionService.ingest(steward, milestoneFile(sourceId, ROWS, acquiredAt())));

            UUID ingestionId = result.ingestionId();
            assertThat(count("SELECT COUNT(*) FROM external_data.received_record WHERE ingestion_id = ?", ingestionId))
                    .isEqualTo(ROWS);
            assertThat(count("SELECT COUNT(*) FROM external_data.external_record WHERE provider = ?", provider))
                    .isEqualTo(ROWS);
            assertThat(count(
                            "SELECT COUNT(*) FROM external_data.adopted_value a JOIN external_data.received_record r"
                                    + " ON r.id = a.received_record_id WHERE r.ingestion_id = ?",
                            ingestionId))
                    .isEqualTo(ROWS);
            // 行ごとの原本と、ファイル全体の原本
            assertThat(count(
                            "SELECT COUNT(*) FROM external_data.stored_payload WHERE object_key IN"
                                    + " (SELECT payload_object_key FROM external_data.received_record WHERE ingestion_id = ?"
                                    + " UNION SELECT file_object_key FROM external_data.ingestion WHERE id = ?)",
                            ingestionId,
                            ingestionId))
                    .isEqualTo(ROWS + 1);
            assertThat(auditPublications(ingestionId)).isEqualTo(ROWS);
            awaitCompletedAuditPublications(other);
            awaitCompletedAuditPublications(result);
        } finally {
            removeSource(sourceId);
            removeSource(otherSourceId);
        }
    }

    /** 取込を呼んだスレッドの接続とコミットを記録しながら取り込む。 */
    private Ingested recordedIngest(IngestFileCommand command) {
        connectionUsage.start();
        try {
            return ingested(ingestionService.ingest(steward, command));
        } finally {
            connectionUsage.stop();
        }
    }

    /**
     * 自分の取込の監査の発行記録がすべて完了になるまで待つ（後のテストに配信を残さない。T-184）。採用の行と隔離の行が 1 件ずつ監査の
     * イベントを出し、重複は出さない。待つ条件は自分の取込 ID（イベントが持つ）と監査の受け手で絞る。
     */
    void awaitCompletedAuditPublications(Ingested result) {
        int expected = result.countOf(Classification.ADOPTED) + result.countOf(Classification.QUARANTINED);
        await().atMost(DELIVERY_TIMEOUT)
                .pollInterval(Duration.ofMillis(500))
                .until(() -> completedAuditPublications(result.ingestionId()) == expected);
    }

    private int auditPublications(UUID ingestionId) {
        return count(
                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ? AND listener_id LIKE ?",
                "%" + ingestionId + "%",
                "%" + AUDIT_LISTENER + "%");
    }

    private int completedAuditPublications(UUID ingestionId) {
        return count(
                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ? AND listener_id LIKE ?"
                        + " AND completion_date IS NOT NULL",
                "%" + ingestionId + "%",
                "%" + AUDIT_LISTENER + "%");
    }

    private IngestFileCommand milestoneFile(UUID sourceId, int rows, Instant acquiredAt) {
        return assemble(sourceId, milestoneCsv(rows, acquiredAt));
    }

    private static IngestFileCommand assemble(UUID sourceId, byte[] csv) {
        return IngestFileCommandAssembler.assemble(sourceId, CsvFileKind.MILESTONE, csv);
    }

    /** 取得時刻（取込時刻より前。秒まで。T-230）。 */
    private Instant acquiredAt() {
        return clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
    }

    /** 外部 ID と追跡番号が行ごとに違う、出発・東京の主要実績のファイル（すべて採用になる。T-230）。 */
    static byte[] milestoneCsv(int rows, Instant acquiredAt) {
        return csv(
                MILESTONE_HEADER,
                rows,
                row -> "MS-%05d,1,%s,%s,DEPARTURE,JPTYO,%s"
                        .formatted(row, acquiredAt, trackingNumber(row), acquiredAt));
    }

    /** ヘッダーと、行の番号（0 から）から作る物理データ行の並びのファイル。 */
    static byte[] csv(String header, int rows, IntFunction<String> line) {
        StringBuilder content = new StringBuilder(header);
        for (int row = 0; row < rows; row++) {
            content.append('\n').append(line.apply(row));
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 行の番号ごとに違う追跡番号（{@code CT} と 12 桁）。 */
    static String trackingNumber(int row) {
        StringBuilder digits = new StringBuilder();
        int rest = row;
        for (int i = 0; i < 12; i++) {
            digits.append(TRACKING_CHARACTERS.charAt(rest % TRACKING_CHARACTERS.length()));
            rest /= TRACKING_CHARACTERS.length();
        }
        return "CT" + digits.reverse();
    }

    static String newProvider() {
        return "運送会社-" + UUID.randomUUID();
    }

    UUID insertApprovedSource(String provider, String recordType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, ?, 'APPROVED', 0)",
                id,
                provider,
                recordType);
        return id;
    }

    /** コミットした情報源を承認済みのまま残さない（取込が残るので消せない。停止中にする）。 */
    void removeSource(UUID sourceId) {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
    }

    static Ingested ingested(IngestionOutcome outcome) {
        assertThat(outcome).isInstanceOf(Ingested.class);
        return (Ingested) outcome;
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}

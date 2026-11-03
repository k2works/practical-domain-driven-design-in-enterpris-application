package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 取込のサービスを PostgreSQL 18 で確かめる（Bolt 39b の行 2・4・9・11。US-14 AC3・AC4、BR-12、ED-INV-02。Bolt 39b2 の行 5・15・21 は
 * CSV のバイト列から組み立て（{@code interfaces.file}）を通して取り込む。UC-15 2a）。
 *
 * <p>共有の DB に承認済みの情報源を残さない。行 2・4・11 はテストのトランザクションの中で取込のサービスを呼び（サービスの
 * {@code @Transactional} は外側に加わる）、最後に巻き戻す。行 9 は取込のサービス自身のトランザクションが巻き戻ることを確かめるので、
 * 情報源をコミットし、最後に停止中にしてから消す。原本の保存は独立したトランザクションでコミットされて残る（ADR-004 の改訂）ので、
 * 確かめる行はテストごとの UUID を含む提供元の名前と、保存した原本のオブジェクトキーで絞る。取得時刻は取込時刻より前、主要実績の行は
 * payload に追跡番号を持つ（T-230）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExternalDataIngestionIntegrationTest {

    private static final String MILESTONE_HEADER = "external_id,external_version,acquired_at,tracking_number";

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    @Autowired
    IngestionService ingestionService;

    @MockitoSpyBean
    ExternalPayloadStorage payloadStorage;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    /** 取得時刻（取込時刻より前。T-230）。 */
    private Instant acquiredAt;

    @Test
    void 行2_同じファイルを別の取込で取り込み直すと受信記録は重複で外部原本の数は変わらない() {
        inRolledBackTransaction(() -> {
            String provider = newProvider();
            UUID sourceId = insertApprovedSource(provider);
            IngestFileCommand file = file(
                    sourceId, milestone(2, "MS-0001", "CTABCDEFGH2345"), milestone(3, "MS-0002", "CTABCDEFGH2346"));

            Ingested first = ingested(ingestionService.ingest(steward, file));
            Ingested second = ingested(ingestionService.ingest(steward, file));

            assertThat(externalRecordCount(provider)).isEqualTo(2);
            assertThat(second.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.DUPLICATE, Classification.DUPLICATE);
            assertThat(second.rows())
                    .extracting(RowResult::externalRecordId)
                    .containsExactlyElementsOf(first.rows().stream()
                            .map(RowResult::externalRecordId)
                            .toList());
            assertThat(storedClassifications(second))
                    .containsExactly(Classification.DUPLICATE.name(), Classification.DUPLICATE.name());
        });
    }

    @Test
    void 行4_同じファイルの中の同じキーは同じトランザクションで先の行の外部原本が見え後の行は重複か隔離になる() {
        inRolledBackTransaction(() -> {
            String provider = newProvider();
            UUID sourceId = insertApprovedSource(provider);
            IngestFileCommand file = file(
                    sourceId,
                    milestone(2, "MS-0001", "CTABCDEFGH2345"),
                    milestone(3, "MS-0001", "CTABCDEFGH2345"),
                    milestone(4, "MS-0001", "CTABCDEFGH2346"));

            Ingested result = ingested(ingestionService.ingest(steward, file));

            assertThat(result.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED, Classification.DUPLICATE, Classification.QUARANTINED);
            assertThat(result.rows())
                    .extracting(RowResult::externalRecordId)
                    .containsOnly(onlyExternalRecordId(provider));
            assertThat(storedClassifications(result))
                    .containsExactly(
                            Classification.ADOPTED.name(),
                            Classification.DUPLICATE.name(),
                            Classification.QUARANTINED.name());
        });
    }

    @Test
    void 行11_受け取った件数はコマンドの件数のまま書かれ分類別の件数を受信記録から数えた合計と一致する() {
        inRolledBackTransaction(() -> {
            String provider = newProvider();
            UUID sourceId = insertApprovedSource(provider);
            IngestFileCommand file = file(
                    sourceId,
                    milestone(2, "MS-0001", "CTABCDEFGH2345"),
                    milestone(3, "MS-0001", "CTABCDEFGH2345"),
                    milestone(4, "MS-0001", "CTABCDEFGH2346"),
                    milestone(5, "MS-0002", "CTABCDEFGH2347"));

            Ingested result = ingested(ingestionService.ingest(steward, file));

            Integer storedCount = jdbc.queryForObject(
                    "SELECT received_count FROM external_data.ingestion WHERE id = ?",
                    Integer.class,
                    result.ingestionId());
            assertThat(storedCount).isEqualTo(4);
            assertThat(result.receivedCount()).isEqualTo(4);
            assertThat(result.countOf(Classification.ADOPTED)).isEqualTo(2);
            assertThat(result.countOf(Classification.DUPLICATE)).isEqualTo(1);
            assertThat(result.countOf(Classification.QUARANTINED)).isEqualTo(1);
            assertThat(receivedRecordCount(result.ingestionId())).isEqualTo(storedCount);
        });
    }

    @Test
    void 行9_行の途中で失敗すると取込と外部原本と受信記録は残らず保存した原本は残る() {
        String provider = newProvider();
        UUID sourceId = insertApprovedSource(provider);
        try {
            IngestedRow failing = milestone(4, "MS-0003", "CTABCDEFGH2347");
            IngestFileCommand file = file(
                    sourceId,
                    milestone(2, "MS-0001", "CTABCDEFGH2345"),
                    milestone(3, "MS-0002", "CTABCDEFGH2346"),
                    failing);
            doThrow(new IllegalStateException("原本の保存に失敗した（テスト）"))
                    .when(payloadStorage)
                    .store(any(), argThat(payload -> Arrays.equals(payload, failing.payload())));

            assertThatThrownBy(() -> ingestionService.ingest(steward, file)).isInstanceOf(IllegalStateException.class);

            assertThat(count("SELECT COUNT(*) FROM external_data.ingestion WHERE data_source_id = ?", sourceId))
                    .isZero();
            assertThat(externalRecordCount(provider)).isZero();
            List<String> storedKeys = storedObjectKeys();
            // ファイル全体と前の 2 行の原本（失敗した行は保存を呼んだが書いていない）
            assertThat(storedKeys).hasSize(3);
            for (String key : storedKeys) {
                assertThat(count("SELECT COUNT(*) FROM external_data.stored_payload WHERE object_key = ?", key))
                        .as("保存した原本 %s", key)
                        .isEqualTo(1);
                assertThat(count(
                                "SELECT COUNT(*) FROM external_data.received_record WHERE payload_object_key = ?", key))
                        .as("原本 %s の受信記録", key)
                        .isZero();
            }
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行5_引用符の閉じ忘れは前の行を分類し取り込めなかった行の始まりと数を取込に書き範囲の原文を指すキーのない隔離を残す() {
        inRolledBackTransaction(() -> {
            String provider = newProvider();
            UUID sourceId = insertApprovedSource(provider);
            String unreadable =
                    "MS-0002,1,\"" + acquired() + ",CTABCDEFGH2346\n" + "MS-0003,1," + acquired() + ",CTABCDEFGH2347";
            byte[] csv = (MILESTONE_HEADER + "\n" + "MS-0001,1," + acquired() + ",CTABCDEFGH2345\n" + unreadable)
                    .getBytes(StandardCharsets.UTF_8);

            Ingested result = ingested(ingestionService.ingest(
                    steward, IngestFileCommandAssembler.assemble(sourceId, CsvFileKind.MILESTONE, csv)));

            assertThat(result.rows()).extracting(RowResult::classification).containsExactly(Classification.ADOPTED);
            assertThat(result.requiresReview()).isTrue();
            assertThat(jdbc.queryForList(
                            "SELECT unparsed_from_line, unparsed_line_count FROM external_data.ingestion WHERE id = ?",
                            result.ingestionId()))
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row).containsEntry("unparsed_from_line", 3);
                        assertThat(row).containsEntry("unparsed_line_count", 2);
                    });
            Map<String, Object> quarantined = jdbc.queryForMap(
                    "SELECT external_record_id, acquired_at, classification, classification_note, payload_object_key"
                            + " FROM external_data.received_record WHERE id = ? AND ingestion_id = ?",
                    result.unreadable().receivedRecordId(),
                    result.ingestionId());
            assertThat(quarantined)
                    .containsEntry("external_record_id", null)
                    .containsEntry("acquired_at", null)
                    .containsEntry("classification", Classification.QUARANTINED.name())
                    .containsEntry(
                            "classification_note", "3 行目からファイルの終わりまで: 引用符が閉じていません。2 行を取り込めませんでした。ファイルを確かめてください。");
            assertThat(storedPayload((String) quarantined.get("payload_object_key")))
                    .isEqualTo(unreadable.getBytes(StandardCharsets.UTF_8));
            // 閉じ忘れの範囲の行（MS-0002・MS-0003）は外部原本を作らない
            assertThat(externalRecordCount(provider)).isEqualTo(1);
        });
    }

    @Test
    void 行15_受け取った件数は引用符の中の改行と閉じ忘れを含めた物理行の数で空行を数えず行の結果と取り込めなかった行の数の合計と突き合う() {
        inRolledBackTransaction(() -> {
            String provider = newProvider();
            UUID sourceId = insertApprovedSource(provider);
            String header = MILESTONE_HEADER + ",remarks";
            String csv = String.join(
                    "\n",
                    header,
                    "MS-0001,1," + acquired() + ",CTABCDEFGH2345,",
                    "MS-0002,1," + acquired() + ",CTABCDEFGH2346,\"港で荷役待ち",
                    "翌日に積み込み\"",
                    "",
                    "MS-0003,1," + acquired() + ",CTABCDEFGH2347,余分な列,",
                    "MS-0004,1,\"" + acquired() + ",CTABCDEFGH2348,",
                    "MS-0005,1," + acquired() + ",CTABCDEFGH2349,");

            Ingested result = ingested(ingestionService.ingest(
                    steward,
                    IngestFileCommandAssembler.assemble(
                            sourceId, CsvFileKind.MILESTONE, csv.getBytes(StandardCharsets.UTF_8))));

            // 物理データ行は 2・3・4・6・7・8 行目（5 行目の空行は数えない）
            Map<String, Object> stored = jdbc.queryForMap(
                    "SELECT received_count, unparsed_from_line, unparsed_line_count FROM external_data.ingestion"
                            + " WHERE id = ?",
                    result.ingestionId());
            assertThat(stored)
                    .containsEntry("received_count", 6)
                    .containsEntry("unparsed_from_line", 7)
                    .containsEntry("unparsed_line_count", 2);
            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::lastLineNumber, RowResult::classification)
                    .containsExactly(
                            tuple(2, 2, Classification.ADOPTED),
                            tuple(3, 4, Classification.ADOPTED),
                            tuple(6, 6, Classification.QUARANTINED));
            assertThat(result.reconciles()).isTrue();
            int rowLines = result.rows().stream()
                    .mapToInt(row -> row.lastLineNumber() - row.lineNumber() + 1)
                    .sum();
            assertThat(rowLines + (Integer) stored.get("unparsed_line_count")).isEqualTo(stored.get("received_count"));
            // 分類別の件数は受信記録の数（行の結果 3 件と取り込めなかった部分 1 件）で、物理行の数と単位が違う
            assertThat(receivedRecordCount(result.ingestionId())).isEqualTo(4);
        });
    }

    @Test
    void 行21_外側が巻き戻ると行の誤りの原文とファイルの原本は残り受信記録からも取込からも参照されない() {
        String rejectedLine = "MS-0002,1," + acquired() + ",CTABCDEFGH2346,余分な列";
        byte[][] payloads = new byte[1][];
        inRolledBackTransaction(() -> {
            UUID sourceId = insertApprovedSource(newProvider());
            byte[] csv = String.join(
                            "\n", MILESTONE_HEADER, "MS-0001,1," + acquired() + ",CTABCDEFGH2345", rejectedLine)
                    .getBytes(StandardCharsets.UTF_8);
            payloads[0] = csv;

            Ingested result = ingested(ingestionService.ingest(
                    steward, IngestFileCommandAssembler.assemble(sourceId, CsvFileKind.MILESTONE, csv)));

            assertThat(result.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED, Classification.QUARANTINED);
        });

        String rejectedKey = storedKeyOf(rejectedLine.getBytes(StandardCharsets.UTF_8));
        String fileKey = storedKeyOf(payloads[0]);
        for (String key : List.of(rejectedKey, fileKey)) {
            assertThat(count("SELECT COUNT(*) FROM external_data.stored_payload WHERE object_key = ?", key))
                    .as("保存した原本 %s", key)
                    .isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM external_data.received_record WHERE payload_object_key = ?", key))
                    .as("原本 %s を指す受信記録", key)
                    .isZero();
            assertThat(count("SELECT COUNT(*) FROM external_data.ingestion WHERE file_object_key = ?", key))
                    .as("原本 %s を指す取込", key)
                    .isZero();
        }
    }

    /** 取得時刻の文字列（取込時刻より前。T-230）。 */
    private String acquired() {
        if (acquiredAt == null) {
            acquiredAt = clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
        }
        return acquiredAt.toString();
    }

    /** spy が受けた保存の呼び出しのうち、そのバイト列を保存したオブジェクトキー。 */
    private String storedKeyOf(byte[] payload) {
        ArgumentCaptor<PayloadObjectKey> keys = ArgumentCaptor.forClass(PayloadObjectKey.class);
        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(payloadStorage, Mockito.atLeastOnce()).store(keys.capture(), stored.capture());
        List<byte[]> all = stored.getAllValues();
        for (int i = 0; i < all.size(); i++) {
            if (Arrays.equals(all.get(i), payload)) {
                return keys.getAllValues().get(i).value();
            }
        }
        throw new AssertionError("保存していない原本: " + new String(payload, StandardCharsets.UTF_8));
    }

    private byte[] storedPayload(String objectKey) {
        return jdbc.queryForObject(
                "SELECT content FROM external_data.stored_payload WHERE object_key = ?", byte[].class, objectKey);
    }

    private void inRolledBackTransaction(Runnable body) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            body.run();
            status.setRollbackOnly();
        });
    }

    /** 保存に成功した原本のオブジェクトキー（spy が受けた呼び出しのうち、失敗させた最後の 1 回を除く）。 */
    private List<String> storedObjectKeys() {
        ArgumentCaptor<PayloadObjectKey> keys = ArgumentCaptor.forClass(PayloadObjectKey.class);
        Mockito.verify(payloadStorage, Mockito.atLeastOnce()).store(keys.capture(), any());
        List<PayloadObjectKey> all = keys.getAllValues();
        return all.subList(0, all.size() - 1).stream()
                .map(PayloadObjectKey::value)
                .toList();
    }

    private static String newProvider() {
        return "運送会社-" + UUID.randomUUID();
    }

    private UUID insertApprovedSource(String provider) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'MILESTONE', 'APPROVED', 0)",
                id,
                provider);
        return id;
    }

    /** コミットした情報源を承認済みのまま残さない。取込が残っていれば（巻き戻らなかったとき）消せないので、先に停止中にする。 */
    private void removeSource(UUID sourceId) {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
        jdbc.update(
                "DELETE FROM external_data.data_source WHERE id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM external_data.ingestion WHERE data_source_id = ?)",
                sourceId,
                sourceId);
    }

    private IngestedRow milestone(int lineNumber, String externalId, String trackingNumber) {
        if (acquiredAt == null) {
            // テストの中で 1 回だけ決める（行ごとに時計を読むと、同じ内容のはずの行の payload が違ってしまう）
            acquiredAt = clock.instant().minus(Duration.ofMinutes(30));
        }
        String line = externalId + ",1," + acquiredAt + "," + trackingNumber;
        return new IngestedRow(
                lineNumber, lineNumber, externalId, "1", acquiredAt, line.getBytes(StandardCharsets.UTF_8));
    }

    private static IngestFileCommand file(UUID sourceId, IngestedRow... rows) {
        StringBuilder content = new StringBuilder(MILESTONE_HEADER);
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

    private static Ingested ingested(IngestionOutcome outcome) {
        assertThat(outcome).isInstanceOf(Ingested.class);
        return (Ingested) outcome;
    }

    private int externalRecordCount(String provider) {
        return count("SELECT COUNT(*) FROM external_data.external_record WHERE provider = ?", provider);
    }

    private UUID onlyExternalRecordId(String provider) {
        return jdbc.queryForObject(
                "SELECT id FROM external_data.external_record WHERE provider = ?", UUID.class, provider);
    }

    /** 書いた受信記録の分類を行の順に読む（取込に関連付いた受信記録に限る。同じ取込の分類の時刻は同じなので、結果の受信記録 ID で引く）。 */
    private List<String> storedClassifications(Ingested result) {
        return result.rows().stream()
                .map(row -> jdbc.queryForObject(
                        "SELECT classification FROM external_data.received_record WHERE id = ? AND ingestion_id = ?",
                        String.class,
                        row.receivedRecordId(),
                        result.ingestionId()))
                .toList();
    }

    private int receivedRecordCount(UUID ingestionId) {
        return count("SELECT COUNT(*) FROM external_data.received_record WHERE ingestion_id = ?", ingestionId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}

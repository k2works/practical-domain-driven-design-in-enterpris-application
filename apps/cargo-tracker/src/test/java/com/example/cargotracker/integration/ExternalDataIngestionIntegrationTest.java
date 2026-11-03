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
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateExternalRecordException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 取込のサービスを PostgreSQL 18 で確かめる（Bolt 39b の行 2・4・9・11。US-14 AC3・AC4、BR-12、ED-INV-02。Bolt 39b2 の行 5・15・21 は
 * CSV のバイト列から組み立て（{@code interfaces.file}）を通して取り込む。UC-15 2a。Bolt 39b2b の行 19 は値の誤りの行の隔離）。
 *
 * <p>共有の DB に承認済みの情報源を残さない。行 2・4・11 はテストのトランザクションの中で取込のサービスを呼び（サービスの
 * {@code @Transactional} は外側に加わる）、最後に巻き戻す。行 9 は取込のサービス自身のトランザクションが巻き戻ることを確かめるので、
 * 情報源をコミットし、最後に停止中にしてから消す。原本の保存は独立したトランザクションでコミットされて残る（ADR-004 の改訂）ので、
 * 確かめる行はテストごとの UUID を含む提供元の名前と、保存した原本のオブジェクトキーで絞る。取得時刻は取込時刻より前、主要実績の行は
 * payload に追跡番号を持つ（T-230）。
 *
 * <p>Bolt 39b3 の行 1・2・8・9・15 は採用値とイベント（DE-12〜DE-14）を確かめる。発行はテストの中のコミットの後にだけ受ける記録
 * （{@link CommittedEventRecorder}）とイベント発行記録（{@code platform.event_publication}）で確かめ、どちらも自分の情報源 ID で絞る。
 * 起きないことは採用値の追記の呼び出しの記録で確かめる（T-236）。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ExternalDataIngestionIntegrationTest.CommittedEventRecorder.class})
class ExternalDataIngestionIntegrationTest {

    private static final String MILESTONE_HEADER =
            "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";

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

    @MockitoSpyBean
    ExternalRecordRepository externalRecords;

    @MockitoSpyBean
    AdoptedValueRepository adoptedValues;

    @Autowired
    CommittedEventRecorder committedEvents;

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
            String unreadable = "MS-0002,1,\"" + acquired() + ",CTABCDEFGH2346" + tail() + "\n" + "MS-0003,1,"
                    + acquired() + ",CTABCDEFGH2347" + tail();
            byte[] csv = (MILESTONE_HEADER + "\n" + "MS-0001,1," + acquired() + ",CTABCDEFGH2345" + tail() + "\n"
                            + unreadable)
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
                    "MS-0001,1," + acquired() + ",CTABCDEFGH2345" + tail() + ",",
                    "MS-0002,1," + acquired() + ",CTABCDEFGH2346" + tail() + ",\"港で荷役待ち",
                    "翌日に積み込み\"",
                    "",
                    "MS-0003,1," + acquired() + ",CTABCDEFGH2347" + tail() + ",余分な列,",
                    "MS-0004,1,\"" + acquired() + ",CTABCDEFGH2348" + tail() + ",",
                    "MS-0005,1," + acquired() + ",CTABCDEFGH2349" + tail() + ",");

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
        String rejectedLine = "MS-0002,1," + acquired() + ",CTABCDEFGH2346" + tail() + ",余分な列";
        byte[][] payloads = new byte[1][];
        inRolledBackTransaction(() -> {
            UUID sourceId = insertApprovedSource(newProvider());
            byte[] csv = String.join(
                            "\n",
                            MILESTONE_HEADER,
                            "MS-0001,1," + acquired() + ",CTABCDEFGH2345" + tail(),
                            rejectedLine)
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

    @Test
    void 行19_値の誤りの行は原文を保存してからキーのない隔離になり同じキーの正しい行は採用される() {
        inRolledBackTransaction(() -> {
            String provider = newProvider();
            UUID sourceId = insertApprovedSource(provider);
            // 3 行目は追跡番号に紛らわしい文字 0 を含む（キーの列は正しい）。4 行目は同じキーの正しい行
            String malformedLine = "MS-0002,1," + acquired() + ",CTABCDEFGH0345" + tail();
            byte[] csv = String.join(
                            "\n",
                            MILESTONE_HEADER,
                            "MS-0001,1," + acquired() + ",CTABCDEFGH2345" + tail(),
                            malformedLine,
                            "MS-0002,1," + acquired() + ",CTABCDEFGH2346" + tail())
                    .getBytes(StandardCharsets.UTF_8);

            Ingested result = ingested(ingestionService.ingest(
                    steward, IngestFileCommandAssembler.assemble(sourceId, CsvFileKind.MILESTONE, csv)));

            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::classification)
                    .containsExactly(
                            tuple(2, Classification.ADOPTED),
                            tuple(3, Classification.QUARANTINED),
                            tuple(4, Classification.ADOPTED));
            RowResult malformed = result.rows().get(1);
            assertThat(malformed.externalRecordId()).isNull();
            Map<String, Object> quarantined = jdbc.queryForMap(
                    "SELECT external_record_id, acquired_at, classification, classification_note, payload_object_key"
                            + " FROM external_data.received_record WHERE id = ? AND ingestion_id = ?",
                    malformed.receivedRecordId(),
                    result.ingestionId());
            assertThat(quarantined)
                    .containsEntry("external_record_id", null)
                    .containsEntry("acquired_at", null)
                    .containsEntry("classification", Classification.QUARANTINED.name())
                    .containsEntry(
                            "classification_note",
                            "3 行目: 追跡番号の形が違います（tracking_number）。CT と英大文字・数字 12 桁（0・O・1・I・L を除く）で書いてください。");
            assertThat(storedPayload((String) quarantined.get("payload_object_key")))
                    .isEqualTo(malformedLine.getBytes(StandardCharsets.UTF_8));
            // 値の誤りの行は外部原本を作らない。同じキーの正しい行の外部原本だけがある
            assertThat(externalRecordCount(provider)).isEqualTo(2);
            assertThat(count(
                            "SELECT COUNT(*) FROM external_data.external_record WHERE provider = ? AND external_id = ?",
                            provider,
                            "MS-0002"))
                    .isEqualTo(1);
            assertThat(result.reconciles()).isTrue();
            assertThat(receivedRecordCount(result.ingestionId())).isEqualTo(3);
        });
    }

    @Test
    void 行1_採用の主要実績と航海の行は受信記録IDで引ける採用値を1つずつ取込の時刻で書く() {
        inRolledBackTransaction(() -> {
            UUID milestoneSource = insertApprovedSource(newProvider());
            UUID voyageSource = insertApprovedSource("船会社-" + UUID.randomUUID(), "VOYAGE");

            Ingested milestones = ingested(
                    ingestionService.ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "CTABCDEFGH2345"))));
            Ingested voyages = ingested(ingestionService.ingest(steward, voyageFile(voyageSource)));

            assertThat(adoptedValueOf(milestones.rows().getFirst()))
                    .containsEntry("target_type", "MILESTONE")
                    .containsEntry("target_key", "CTABCDEFGH2345")
                    .containsEntry("value_summary", "DEPARTURE;JPTYO;" + summaryTime(acquiredAt))
                    .containsEntry("info_version", "1")
                    .containsEntry("published_at", ingestedAt(milestones.ingestionId()));
            assertThat(adoptedValueOf(voyages.rows().getFirst()))
                    .containsEntry("target_type", "VOYAGE")
                    .containsEntry("target_key", "V2611E")
                    .containsEntry(
                            "value_summary", "JPTYO;;2026-11-20T00:00:00.000000Z|USLAX;2026-12-01T00:00:00.000000Z;")
                    .containsEntry("info_version", "3")
                    .containsEntry("published_at", ingestedAt(voyages.ingestionId()));
        });
    }

    @Test
    void 行2_同じファイルの再取込は重複の受信記録に採用値を作らず採用値の数が変わらない() {
        inRolledBackTransaction(() -> {
            UUID sourceId = insertApprovedSource(newProvider());
            IngestFileCommand file = file(
                    sourceId, milestone(2, "MS-0001", "CTABCDEFGH2345"), milestone(3, "MS-0002", "CTABCDEFGH2346"));

            Ingested first = ingested(ingestionService.ingest(steward, file));
            Ingested second = ingested(ingestionService.ingest(steward, file));

            assertThat(adoptedValueCount(first.ingestionId())).isEqualTo(2);
            assertThat(adoptedValueCount(second.ingestionId())).isZero();
            // 起きないことは追記の呼び出しの記録で確かめる（T-236）。2 回目の取込は追記を呼ばない
            ArgumentCaptor<AdoptedValue> added = ArgumentCaptor.forClass(AdoptedValue.class);
            Mockito.verify(adoptedValues, Mockito.times(2)).add(added.capture());
            assertThat(added.getAllValues())
                    .extracting(AdoptedValue::receivedRecordId)
                    .containsExactlyElementsOf(first.rows().stream()
                            .map(RowResult::receivedRecordId)
                            .toList());
        });
    }

    /**
     * コミットの後に届くことを確かめるため、取込のサービス自身のトランザクションをコミットする。採用値・取込・外部原本・受信記録とイベント
     * 発行記録は共有の DB に残る（採用値の表は追記専用で消せない）。情報源も取込が参照するので消えず、最後に停止中にして残す。確かめる
     * 行とイベントは自分の情報源 ID で絞る。
     */
    @Test
    void 行8_同じ取込の中の複数の採用は採用値とイベントを行ごとに物理行の順でコミットの後に届け発行記録に残す() {
        String provider = newProvider();
        UUID sourceId = insertApprovedSource(provider);
        try {
            String adopted = "MS-0001,1," + acquired() + ",CTABCDEFGH2345" + tail();
            byte[] csv = String.join(
                            "\n",
                            MILESTONE_HEADER,
                            adopted,
                            "MS-0002,1," + acquired() + ",CTABCDEFGH2346" + tail(),
                            adopted,
                            "MS-0003,1," + acquired() + ",CTABCDEFGH2347" + tail() + ",余分な列",
                            "MS-0002,1," + acquired() + ",CTABCDEFGH2348" + tail())
                    .getBytes(StandardCharsets.UTF_8);

            Ingested result = ingested(ingestionService.ingest(
                    steward, IngestFileCommandAssembler.assemble(sourceId, CsvFileKind.MILESTONE, csv)));

            assertThat(result.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(
                            Classification.ADOPTED,
                            Classification.ADOPTED,
                            Classification.DUPLICATE,
                            Classification.QUARANTINED,
                            Classification.QUARANTINED);
            List<Object> events = committedEvents.of(sourceId);
            assertThat(events)
                    .extracting(event -> event.getClass().getSimpleName())
                    .containsExactly(
                            "MilestoneAdoptedValueAccepted",
                            "MilestoneAdoptedValueAccepted",
                            "ExternalRecordQuarantined",
                            "ExternalRecordQuarantined");
            MilestoneAdoptedValueAccepted firstAdopted = (MilestoneAdoptedValueAccepted) events.get(0);
            MilestoneAdoptedValueAccepted secondAdopted = (MilestoneAdoptedValueAccepted) events.get(1);
            assertThat(List.of(firstAdopted, secondAdopted))
                    .extracting(
                            MilestoneAdoptedValueAccepted::trackingNumber,
                            event -> event.source().reference(),
                            MilestoneAdoptedValueAccepted::ingestionId)
                    .containsExactly(
                            tuple(
                                    "CTABCDEFGH2345",
                                    result.rows().get(0).receivedRecordId().toString(),
                                    result.ingestionId()),
                            tuple(
                                    "CTABCDEFGH2346",
                                    result.rows().get(1).receivedRecordId().toString(),
                                    result.ingestionId()));
            assertThat(List.of(firstAdopted.adoptedValueId(), secondAdopted.adoptedValueId()))
                    .containsExactly(
                            adoptedValueId(result.rows().get(0)),
                            adoptedValueId(result.rows().get(1)));
            assertThat(events.subList(2, 4))
                    .map(ExternalRecordQuarantined.class::cast)
                    .extracting(
                            ExternalRecordQuarantined::externalId,
                            ExternalRecordQuarantined::reason,
                            ExternalRecordQuarantined::firstLine)
                    .containsExactly(
                            tuple(null, "COLUMN_COUNT_MISMATCH", 5),
                            tuple("MS-0002", ExternalRecordQuarantined.CONTENT_MISMATCH, 6));
            assertThat(adoptedValueCount(result.ingestionId())).isEqualTo(2);
            // Spring Modulith のイベント発行記録に、自分の情報源のイベントが配信の完了まで残る
            assertThat(completedPublications(MilestoneAdoptedValueAccepted.class, sourceId))
                    .isEqualTo(2);
            assertThat(completedPublications(ExternalRecordQuarantined.class, sourceId))
                    .isEqualTo(2);
            assertThat(publications(VoyageAdoptedValueUpdated.class, sourceId)).isZero();
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行9_行の途中で原本の保存に失敗すると採用値もイベントの発行記録も残らずコミットの後の記録に届かない() {
        UUID sourceId = insertApprovedSource(newProvider());
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

            assertRolledBackAfterAdopting(sourceId, 2);
            // 原本だけ残る（ファイル全体の原本）
            assertThat(count(
                            "SELECT COUNT(*) FROM external_data.stored_payload WHERE object_key = ?",
                            storedKeyOf(file.filePayload())))
                    .isEqualTo(1);
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行9_外部原本の一意制約に違反すると取込全体が巻き戻り採用値もイベントの発行記録も残らない() {
        String provider = newProvider();
        UUID sourceId = insertApprovedSource(provider);
        try {
            IngestFileCommand file = file(
                    sourceId,
                    milestone(2, "MS-0001", "CTABCDEFGH2345"),
                    milestone(3, "MS-0002", "CTABCDEFGH2346"),
                    milestone(4, "MS-0002", "CTABCDEFGH2346"));
            // 同時の取込を模す: 4 行目の照会では 3 行目の外部原本が見えず、追記が一意制約に当たる
            Mockito.doCallRealMethod()
                    .doReturn(Optional.empty())
                    .when(externalRecords)
                    .findByKey(argThat(key ->
                            key.provider().equals(provider) && key.externalId().equals("MS-0002")));

            assertThatThrownBy(() -> ingestionService.ingest(steward, file))
                    .isInstanceOf(DuplicateExternalRecordException.class);

            assertRolledBackAfterAdopting(sourceId, 2);
            assertThat(externalRecordCount(provider)).isZero();
            assertThat(count(
                            "SELECT COUNT(*) FROM external_data.stored_payload WHERE object_key = ?",
                            storedKeyOf(file.filePayload())))
                    .isEqualTo(1);
        } finally {
            removeSource(sourceId);
        }
    }

    @Test
    void 行15_再取込と内容の違う原本の取込は最初の採用値を書き換えず追記もしない() {
        inRolledBackTransaction(() -> {
            UUID sourceId = insertApprovedSource(newProvider());
            IngestFileCommand file = file(sourceId, milestone(2, "MS-0001", "CTABCDEFGH2345"));
            Ingested first = ingested(ingestionService.ingest(steward, file));
            Map<String, Object> before = adoptedValueOf(first.rows().getFirst());

            Ingested duplicate = ingested(ingestionService.ingest(steward, file));
            Ingested quarantined = ingested(
                    ingestionService.ingest(steward, file(sourceId, milestone(2, "MS-0001", "CTABCDEFGH2346"))));

            assertThat(duplicate.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.DUPLICATE);
            assertThat(quarantined.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.QUARANTINED);
            assertThat(adoptedValueOf(first.rows().getFirst())).isEqualTo(before);
            Mockito.verify(adoptedValues, Mockito.times(1)).add(any());
        });
    }

    /**
     * 取込のトランザクションが巻き戻ったことを確かめる。採用値の追記と発行は呼ばれた（呼び出しの記録。T-236）が、その採用値も、それを指す
     * イベントの発行記録も残らず、コミットの後にだけ受ける記録にも届かない。
     */
    private void assertRolledBackAfterAdopting(UUID sourceId, int adoptedRows) {
        ArgumentCaptor<AdoptedValue> added = ArgumentCaptor.forClass(AdoptedValue.class);
        Mockito.verify(adoptedValues, Mockito.times(adoptedRows)).add(added.capture());
        for (AdoptedValue value : added.getAllValues()) {
            assertThat(count("SELECT COUNT(*) FROM external_data.adopted_value WHERE id = ?", value.id()))
                    .as("採用値 %s", value.id())
                    .isZero();
            assertThat(count(
                            "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ?",
                            "%" + value.id() + "%"))
                    .as("採用値 %s のイベントの発行記録", value.id())
                    .isZero();
        }
        assertThat(count("SELECT COUNT(*) FROM external_data.ingestion WHERE data_source_id = ?", sourceId))
                .isZero();
        assertThat(publications(MilestoneAdoptedValueAccepted.class, sourceId)).isZero();
        assertThat(committedEvents.of(sourceId)).isEmpty();
    }

    private IngestFileCommand voyageFile(UUID sourceId) {
        String line = "V2611E,3," + acquired() + ",JPTYO;;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;";
        VoyageValues values = new VoyageValues(
                "V2611E",
                List.of(
                        new PortCallValue(
                                new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-20T00:00:00Z"))),
                        new PortCallValue(
                                new Location("USLAX"), new UtcInstant(Instant.parse("2026-12-01T00:00:00Z")), null)));
        IngestedRow row =
                new IngestedRow(2, 2, "V2611E", "3", acquiredAt, values, line.getBytes(StandardCharsets.UTF_8));
        return new IngestFileCommand(
                sourceId,
                ("external_id,external_version,acquired_at,port_calls\n" + line).getBytes(StandardCharsets.UTF_8),
                1,
                List.of(row),
                List.of(),
                null);
    }

    private static String summaryTime(Instant instant) {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
                .withZone(ZoneOffset.UTC)
                .format(instant);
    }

    private Map<String, Object> adoptedValueOf(RowResult row) {
        return jdbc.queryForMap(
                "SELECT id, target_type, target_key, value_summary, info_version, published_at"
                        + " FROM external_data.adopted_value WHERE received_record_id = ?",
                row.receivedRecordId());
    }

    private UUID adoptedValueId(RowResult row) {
        return (UUID) adoptedValueOf(row).get("id");
    }

    private Object ingestedAt(UUID ingestionId) {
        return jdbc.queryForObject(
                "SELECT ingested_at FROM external_data.ingestion WHERE id = ?", Object.class, ingestionId);
    }

    private int adoptedValueCount(UUID ingestionId) {
        return count(
                "SELECT COUNT(*) FROM external_data.adopted_value a"
                        + " JOIN external_data.received_record r ON r.id = a.received_record_id WHERE r.ingestion_id = ?",
                ingestionId);
    }

    /** 自分の情報源のイベントの発行記録の数（情報源 ID で絞る。全体を数えない）。 */
    private int publications(Class<?> eventType, UUID sourceId) {
        return count(
                "SELECT COUNT(*) FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?",
                eventType.getName(),
                "%" + sourceId + "%");
    }

    private int completedPublications(Class<?> eventType, UUID sourceId) {
        return count(
                "SELECT COUNT(*) FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?"
                        + " AND completion_date IS NOT NULL",
                eventType.getName(),
                "%" + sourceId + "%");
    }

    /**
     * コミットの後にだけ受ける記録（Bolt 39b3 の行 8・9。listener は作らない。発行と巻き戻りを確かめるためのテストの中の受け手）。Spring
     * Modulith はこの受け手ごとにイベント発行記録を書き、受けた後に完了にする。
     */
    static class CommittedEventRecorder {

        private final List<Object> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        public void on(VoyageAdoptedValueUpdated event) {
            events.add(event);
        }

        @TransactionalEventListener
        public void on(MilestoneAdoptedValueAccepted event) {
            events.add(event);
        }

        @TransactionalEventListener
        public void on(ExternalRecordQuarantined event) {
            events.add(event);
        }

        /** 情報源のイベントを受けた順に返す（ほかのテストの取込を混ぜない）。 */
        List<Object> of(UUID dataSourceId) {
            return events.stream()
                    .filter(event -> dataSourceId.equals(dataSourceIdOf(event)))
                    .toList();
        }

        private static UUID dataSourceIdOf(Object event) {
            return switch (event) {
                case VoyageAdoptedValueUpdated voyage -> voyage.dataSourceId();
                case MilestoneAdoptedValueAccepted milestone -> milestone.dataSourceId();
                case ExternalRecordQuarantined quarantined -> quarantined.dataSourceId();
                default -> null;
            };
        }
    }

    /** 取得時刻の文字列（取込時刻より前。T-230）。 */
    private String acquired() {
        if (acquiredAt == null) {
            acquiredAt = clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
        }
        return acquiredAt.toString();
    }

    /** 主要実績の行の値の列（追跡番号の後。出発・東京、発生時刻は取得時刻と同じ。T-230。Bolt 39b2b）。 */
    private String tail() {
        return ",DEPARTURE,JPTYO," + acquired();
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
        return insertApprovedSource(provider, "MILESTONE");
    }

    private UUID insertApprovedSource(String provider, String recordType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, ?, 'APPROVED', 0)",
                id,
                provider,
                recordType);
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
        String line = externalId + ",1," + acquiredAt + "," + trackingNumber + ",DEPARTURE,JPTYO," + acquiredAt;
        MilestoneValues values = new MilestoneValues(
                new TrackingNumberValue(trackingNumber),
                MilestoneKindValue.DEPARTURE,
                new Location("JPTYO"),
                new UtcInstant(acquiredAt));
        return new IngestedRow(
                lineNumber, lineNumber, externalId, "1", acquiredAt, values, line.getBytes(StandardCharsets.UTF_8));
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

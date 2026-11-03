package com.example.cargotracker.externaldata.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.RejectedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Unreadable;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryDataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryIngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 取込のサービス（Bolt 39b の行 1〜5b・7〜8・9b・10〜12、形式の誤りは入れ子のクラスで Bolt 39b2 の行番号。US-14 AC1〜AC4、BR-12、BR-18、
 * ED-INV-01・ED-INV-02）。メモリの実装と
 * 固定の Clock で確かめる。取得時刻は取込時刻より前、主要実績の行は payload に追跡番号を持ち、航海の行は持たない（T-230）。
 */
class IngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00.123456Z");
    private static final Instant ACQUIRED = Instant.parse("2026-11-03T08:30:00Z");
    private static final String MILESTONE_HEADER = "external_id,external_version,acquired_at,tracking_number";

    private final List<String> calls = new ArrayList<>();
    private final InMemoryDataSourceRepository sources = new InMemoryDataSourceRepository();
    private final InMemoryIngestionRepository ingestions = new InMemoryIngestionRepository();
    private final InMemoryExternalRecordRepository externalRecords = new InMemoryExternalRecordRepository();
    private final InMemoryReceivedRecordRepository receivedRecords = new InMemoryReceivedRecordRepository();
    private final InMemoryExternalPayloadStorage storage = new InMemoryExternalPayloadStorage();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AuthenticatedActor steward = actor(Role.DATA_STEWARD);
    private final DataSource milestoneSource = approvedSource("運送会社A-" + UUID.randomUUID(), "MILESTONE");

    private IngestionService service() {
        return service(new RecordingStorage(storage));
    }

    private IngestionService service(ExternalPayloadStorage payloadStorage) {
        return new IngestionService(
                sources, ingestions, externalRecords, new RecordingReceivedRecords(), payloadStorage, clock);
    }

    private static AuthenticatedActor actor(Role role) {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(role), "操作者", "A 社");
    }

    private DataSource approvedSource(String provider, String recordType) {
        return source(provider, recordType, ApprovalStatus.APPROVED);
    }

    private DataSource source(String provider, String recordType, ApprovalStatus status) {
        DataSource source = new DataSource(UUID.randomUUID(), provider, recordType, status);
        sources.add(source);
        return source;
    }

    private static IngestedRow milestone(int lineNumber, String externalId, String version, String trackingNumber) {
        String line = externalId + "," + version + "," + ACQUIRED + "," + trackingNumber;
        return new IngestedRow(
                lineNumber, lineNumber, externalId, version, ACQUIRED, line.getBytes(StandardCharsets.UTF_8));
    }

    private static IngestFileCommand file(DataSource source, IngestedRow... rows) {
        StringBuilder content = new StringBuilder(MILESTONE_HEADER);
        for (IngestedRow row : rows) {
            content.append('\n').append(new String(row.payload(), StandardCharsets.UTF_8));
        }
        return new IngestFileCommand(
                source.id(),
                content.toString().getBytes(StandardCharsets.UTF_8),
                rows.length,
                List.of(rows),
                List.of(),
                null);
    }

    private Ingested ingested(IngestionOutcome outcome) {
        assertThat(outcome).isInstanceOf(Ingested.class);
        return (Ingested) outcome;
    }

    private IdempotencyKey keyOf(DataSource source, String externalId, String version) {
        return new IdempotencyKey(source.provider(), source.recordType(), externalId, version);
    }

    @Test
    void 行1_新しい冪等性キーの行は外部原本を作り受信記録を採用にして取込に関連付ける() {
        IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");

        Ingested result = ingested(service().ingest(steward, file(milestoneSource, row)));

        ExternalRecord adopted = externalRecords
                .findByKey(keyOf(milestoneSource, "MS-0001", "1"))
                .orElseThrow();
        ReceivedRecord received = receivedRecords.all().getFirst();
        assertThat(adopted.payloadHash()).isEqualTo(PayloadHash.of(row.payload()));
        assertThat(storage.read(adopted.payloadObjectKey())).isEqualTo(row.payload());
        assertThat(adopted.firstReceivedAt()).isEqualTo(NOW);
        assertThat(received.classification()).isEqualTo(Classification.ADOPTED);
        assertThat(received.classificationNote()).isNull();
        assertThat(received.externalRecordId()).isEqualTo(adopted.id());
        assertThat(received.ingestionId()).isEqualTo(result.ingestionId());
        assertThat(received.payloadObjectKey()).isEqualTo(adopted.payloadObjectKey());
        assertThat(result.rows())
                .containsExactly(new RowResult(2, 2, Classification.ADOPTED, adopted.id(), received.id()));
    }

    @Test
    void 行1_原本はファイル全体と行の順に保存してから受信記録を書く() {
        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));

        assertThat(calls).containsExactly("store:ingestions", "store:records", "received:ADOPTED");
    }

    @Test
    void 行1_同じ外部IDで外部版の違う行は新しい冪等性キーで外部原本を作り採用にする() {
        ingested(service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345"))));

        Ingested second = ingested(
                service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "2", "CTABCDEFGH2345"))));

        assertThat(second.rows().getFirst().classification()).isEqualTo(Classification.ADOPTED);
        assertThat(externalRecords.findByKey(keyOf(milestoneSource, "MS-0001", "2")))
                .isPresent();
        assertThat(externalRecords.all()).hasSize(2);
    }

    @Test
    void 行2_同じキーと同じ内容を別の取込で再送すると重複になり外部原本は1つのまま既存の外部原本IDを返す() {
        IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
        Ingested first = ingested(service().ingest(steward, file(milestoneSource, row)));

        Ingested second = ingested(service().ingest(steward, file(milestoneSource, row)));

        assertThat(externalRecords.all()).hasSize(1);
        RowResult resent = second.rows().getFirst();
        assertThat(resent.classification()).isEqualTo(Classification.DUPLICATE);
        assertThat(resent.externalRecordId()).isEqualTo(first.rows().getFirst().externalRecordId());
        ReceivedRecord received = receivedRecords.all().getLast();
        assertThat(received.ingestionId()).isEqualTo(second.ingestionId());
        assertThat(received.classificationNote()).contains("同じ原本を受け取り済み");
    }

    @Test
    void 行3_同じキーで内容の違う行は隔離になり外部原本の最初のハッシュとオブジェクトキーは変わらない() {
        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));
        ExternalRecord before = externalRecords.all().getFirst();

        Ingested second = ingested(
                service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2346"))));

        assertThat(externalRecords.all()).containsExactly(before);
        assertThat(second.rows().getFirst().classification()).isEqualTo(Classification.QUARANTINED);
        ReceivedRecord received = receivedRecords.all().getLast();
        assertThat(received.payloadHash()).isNotEqualTo(before.payloadHash());
        assertThat(received.classificationNote()).startsWith("2 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: ");
    }

    @Test
    void 行4_同じファイルの中の同じキーは行の順に先の行を採用し後の行は同じ内容なら重複違えば隔離() {
        Ingested result = ingested(service()
                .ingest(
                        steward,
                        file(
                                milestoneSource,
                                milestone(2, "MS-0001", "1", "CTABCDEFGH2345"),
                                milestone(3, "MS-0001", "1", "CTABCDEFGH2345"),
                                milestone(4, "MS-0001", "1", "CTABCDEFGH2346"))));

        assertThat(result.rows())
                .extracting(RowResult::lineNumber, RowResult::classification)
                .containsExactly(
                        tuple(2, Classification.ADOPTED),
                        tuple(3, Classification.DUPLICATE),
                        tuple(4, Classification.QUARANTINED));
        assertThat(externalRecords.all()).hasSize(1);
    }

    @Test
    void 行5_候補の情報源の取込は拒否し何も保存しない() {
        DataSource candidate = source("運送会社C-" + UUID.randomUUID(), "MILESTONE", ApprovalStatus.CANDIDATE);

        IngestionOutcome outcome =
                service().ingest(steward, file(candidate, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.DataSourceNotApproved.class);
        assertNothingWritten();
    }

    @Test
    void 行5_停止中の情報源の取込は拒否し何も保存しない() {
        DataSource suspended = source("運送会社S-" + UUID.randomUUID(), "MILESTONE", ApprovalStatus.SUSPENDED);

        IngestionOutcome outcome =
                service().ingest(steward, file(suspended, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.DataSourceNotApproved.class);
        assertNothingWritten();
    }

    @Test
    void 行5_ない情報源の取込は拒否し何も保存しない() {
        DataSource unknown = new DataSource(UUID.randomUUID(), "未登録の提供元", "MILESTONE", ApprovalStatus.APPROVED);

        IngestionOutcome outcome =
                service().ingest(steward, file(unknown, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.DataSourceNotFound.class);
        assertNothingWritten();
    }

    @Test
    void 行5b_データ責任者でない操作者の取込は拒否し何も保存しない() {
        IngestionOutcome outcome = service()
                .ingest(
                        actor(Role.TRACKING_MANAGER),
                        file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.NotPermitted.class);
        assertNothingWritten();
    }

    @Test
    void 行5b_役割は情報源より先に確かめる() {
        IngestionOutcome outcome = service()
                .ingest(
                        actor(Role.SYSTEM_ADMIN),
                        new IngestFileCommand(UUID.randomUUID(), new byte[0], 0, List.of(), List.of(), null));

        assertThat(outcome).isInstanceOf(IngestionOutcome.NotPermitted.class);
    }

    private void assertNothingWritten() {
        assertThat(calls).isEmpty();
        assertThat(ingestions.all()).isEmpty();
        assertThat(externalRecords.all()).isEmpty();
        assertThat(receivedRecords.all()).isEmpty();
    }

    @Test
    void 行7_別の提供元の情報源で同じ外部IDと外部版の行は別のキーでどちらも採用() {
        DataSource otherProvider = approvedSource("運送会社B-" + UUID.randomUUID(), "MILESTONE");

        Ingested first = ingested(
                service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345"))));
        Ingested second = ingested(
                service().ingest(steward, file(otherProvider, milestone(2, "MS-0001", "1", "CTABCDEFGH2345"))));

        assertThat(first.rows().getFirst().classification()).isEqualTo(Classification.ADOPTED);
        assertThat(second.rows().getFirst().classification()).isEqualTo(Classification.ADOPTED);
        assertThat(externalRecords.findByKey(keyOf(otherProvider, "MS-0001", "1")))
                .isPresent();
        assertThat(externalRecords.all()).hasSize(2);
    }

    @Test
    void 行7_別の原本種別の情報源で同じ外部IDと外部版の行は別のキーでどちらも採用() {
        DataSource voyageSource = approvedSource(milestoneSource.provider(), "VOYAGE");
        String voyageLine = "MS-0001,1," + ACQUIRED;
        IngestedRow voyageRow =
                new IngestedRow(2, 2, "MS-0001", "1", ACQUIRED, voyageLine.getBytes(StandardCharsets.UTF_8));

        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));
        Ingested voyage = ingested(service()
                .ingest(
                        steward,
                        new IngestFileCommand(
                                voyageSource.id(),
                                ("external_id,external_version,acquired_at\n" + voyageLine)
                                        .getBytes(StandardCharsets.UTF_8),
                                1,
                                List.of(voyageRow),
                                List.of(),
                                null)));

        assertThat(voyage.rows().getFirst().classification()).isEqualTo(Classification.ADOPTED);
        assertThat(externalRecords.all()).hasSize(2);
    }

    @Test
    void 行8_行の原本の保存に失敗したらその行の受信記録を書かず取込を失敗にする() {
        ExternalPayloadStorage failingOnSecondRow = new RecordingStorage(storage) {
            private int records;

            @Override
            public void store(PayloadObjectKey key, byte[] payload) {
                if (key.value().startsWith("externaldata/records/") && ++records == 2) {
                    throw new IllegalStateException("原本の保存に失敗した");
                }
                super.store(key, payload);
            }
        };
        IngestFileCommand command = file(
                milestoneSource,
                milestone(2, "MS-0001", "1", "CTABCDEFGH2345"),
                milestone(3, "MS-0002", "1", "CTABCDEFGH2346"));

        IngestionService failingService = service(failingOnSecondRow);

        assertThatThrownBy(() -> failingService.ingest(steward, command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("原本の保存に失敗した");
        assertThat(receivedRecords.all()).hasSize(1);
        assertThat(externalRecords.findByKey(keyOf(milestoneSource, "MS-0002", "1")))
                .isEmpty();
    }

    @Test
    void 行9b_同時の取込で同じ新しいキーを先に書かれたら一意制約の違反のまま例外を上げる() {
        IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
        IngestionService racingService = racingService(row);
        IngestFileCommand command = file(milestoneSource, row);

        assertThatThrownBy(() -> racingService.ingest(steward, command)).isInstanceOf(DuplicateKeyException.class);
        assertThat(receivedRecords.all()).isEmpty();
    }

    @Test
    void 行9b_同時の取込で失敗した行を取り込み直せば先に書かれた外部原本に対して同じ内容は重複違う内容は隔離() {
        IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
        IngestionService racingService = racingService(row);
        IngestFileCommand command = file(milestoneSource, row);
        assertThatThrownBy(() -> racingService.ingest(steward, command)).isInstanceOf(DuplicateKeyException.class);

        Ingested retried = ingested(racingService.ingest(steward, command));
        Ingested changed = ingested(
                racingService.ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2346"))));

        assertThat(retried.rows().getFirst().classification()).isEqualTo(Classification.DUPLICATE);
        assertThat(changed.rows().getFirst().classification()).isEqualTo(Classification.QUARANTINED);
    }

    /** 新しいキーを引いた直後に、同時の取込が同じキーの外部原本を先に書いたことにするサービス（行 9b）。 */
    private IngestionService racingService(IngestedRow row) {
        InMemoryExternalRecordRepository racing = new InMemoryExternalRecordRepository() {
            @Override
            public Optional<ExternalRecord> findByKey(IdempotencyKey key) {
                Optional<ExternalRecord> found = super.findByKey(key);
                if (found.isEmpty()) {
                    super.add(new ExternalRecord(
                            UUID.randomUUID(),
                            key,
                            PayloadHash.of(row.payload()),
                            PayloadObjectKey.newRecordPayload(),
                            NOW));
                }
                return found;
            }
        };
        return new IngestionService(sources, ingestions, racing, receivedRecords, new RecordingStorage(storage), clock);
    }

    @Test
    void 行10_取込者は操作者で取込と外部原本と受信記録の時刻はClockの時刻で取得時刻は行の値() {
        Ingested result = ingested(
                service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345"))));

        Ingestion ingestion = ingestions.all().getFirst();
        ReceivedRecord received = receivedRecords.all().getFirst();
        assertThat(ingestion.id()).isEqualTo(result.ingestionId());
        assertThat(ingestion.ingestedBy()).isEqualTo(steward.userId());
        assertThat(ingestion.ingestedAt()).isEqualTo(NOW);
        assertThat(externalRecords.all().getFirst().firstReceivedAt()).isEqualTo(NOW);
        assertThat(received.classifiedAt()).isEqualTo(NOW);
        assertThat(received.acquiredAt()).isEqualTo(ACQUIRED);
    }

    @Test
    void 行11_受け取った件数はコマンドの件数で分類別の件数は受信記録から数え合計が一致する() {
        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));

        Ingested result = ingested(service()
                .ingest(
                        steward,
                        file(
                                milestoneSource,
                                milestone(2, "MS-0001", "1", "CTABCDEFGH2345"),
                                milestone(3, "MS-0001", "1", "CTABCDEFGH2349"),
                                milestone(4, "MS-0002", "1", "CTABCDEFGH2346"))));

        assertThat(result.receivedCount()).isEqualTo(3);
        assertThat(ingestions.all().getLast().receivedCount()).isEqualTo(3);
        assertThat(result.countOf(Classification.ADOPTED)).isEqualTo(1);
        assertThat(result.countOf(Classification.DUPLICATE)).isEqualTo(1);
        assertThat(result.countOf(Classification.QUARANTINED)).isEqualTo(1);
        assertThat(result.countOf(Classification.ADOPTED)
                        + result.countOf(Classification.DUPLICATE)
                        + result.countOf(Classification.QUARANTINED))
                .isEqualTo(result.receivedCount());
    }

    @Test
    void 行12_ヘッダーだけの空のファイルは取込とファイル全体の原本を記録し件数0で受信記録はない() {
        IngestFileCommand empty = file(milestoneSource);

        Ingested result = ingested(service().ingest(steward, empty));

        Ingestion ingestion = ingestions.all().getFirst();
        assertThat(result.receivedCount()).isZero();
        assertThat(result.rows()).isEmpty();
        assertThat(ingestion.receivedCount()).isZero();
        assertThat(ingestion.dataSourceId()).isEqualTo(milestoneSource.id());
        assertThat(ingestion.fileHash()).isEqualTo(PayloadHash.of(empty.filePayload()));
        assertThat(storage.read(ingestion.fileObjectKey())).isEqualTo(empty.filePayload());
        assertThat(receivedRecords.all()).isEmpty();
        assertThat(calls).containsExactly("store:ingestions");
    }

    /**
     * 形式の誤り（Bolt 39b2 の行 1・5・6b・10・12〜17・20。UC-15 2a、BR-12 の件数の突き合わせ、ED-INV-02）。コマンドは直接作る
     * （CSV からの組み立てはステップ 5）。行の誤りとファイルの誤りは外部原本を持たない隔離の受信記録になる。
     */
    @Nested
    class 形式の誤り {

        private static final String FILE_NOTE_UNDECODABLE = "ファイル: 文字コードが UTF-8 ではありません。UTF-8 で保存し直したファイルを取り込んでください。";

        private static byte[] utf8(String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }

        private static String line(String externalId, String trackingNumber) {
            return externalId + ",1," + ACQUIRED + "," + trackingNumber;
        }

        private static RejectedRow rejected(int lineNumber, int lastLineNumber, FormatDefect defect, String raw) {
            return new RejectedRow(lineNumber, lastLineNumber, defect, List.of(), utf8(raw));
        }

        private IngestFileCommand command(
                byte[] file,
                int receivedCount,
                List<IngestedRow> rows,
                List<RejectedRow> rejectedRows,
                UnreadablePart unreadable) {
            return new IngestFileCommand(milestoneSource.id(), file, receivedCount, rows, rejectedRows, unreadable);
        }

        private UUID externalRecordIdOf(String externalId) {
            return externalRecords
                    .findByKey(keyOf(milestoneSource, externalId, "1"))
                    .orElseThrow()
                    .id();
        }

        private ReceivedRecord onlyKeylessQuarantine() {
            List<ReceivedRecord> keyless = receivedRecords.all().stream()
                    .filter(received -> received.externalRecordId() == null)
                    .toList();
            assertThat(keyless).hasSize(1);
            ReceivedRecord received = keyless.getFirst();
            assertThat(received.classification()).isEqualTo(Classification.QUARANTINED);
            assertThat(received.acquiredAt()).isNull();
            assertThat(received.classifiedAt()).isEqualTo(NOW);
            return received;
        }

        @Test
        void 行1_ヘッダーが読めないファイルは行を読まずファイル全体の原本を指すキーのない隔離を1件書き要確認にする() {
            byte[] file = utf8("external_id,\"external\"_version,acquired_at,tracking_number\n"
                    + line("MS-0001", "CTABCDEFGH2345") + "\n" + line("MS-0002", "CTABCDEFGH2346"));
            IngestFileCommand command = command(
                    file,
                    2,
                    List.of(),
                    List.of(),
                    new UnreadablePart(2, 2, FormatDefect.UNREADABLE_HEADER, List.of(), null));

            Ingested result = ingested(service().ingest(steward, command));

            Ingestion ingestion = ingestions.all().getFirst();
            ReceivedRecord received = onlyKeylessQuarantine();
            assertThat(received.payloadObjectKey()).isEqualTo(ingestion.fileObjectKey());
            assertThat(received.payloadHash()).isEqualTo(ingestion.fileHash());
            assertThat(received.ingestionId()).isEqualTo(ingestion.id());
            assertThat(received.classificationNote()).startsWith("ファイル: ヘッダーを読めません。");
            assertThat(calls).containsExactly("store:ingestions", "received:QUARANTINED");
            assertThat(ingestion.unparsedFromLine()).isEqualTo(2);
            assertThat(ingestion.unparsedLineCount()).isEqualTo(2);
            assertThat(result.rows()).isEmpty();
            assertThat(result.unreadable()).isEqualTo(new Unreadable(2, 2, received.id()));
            assertThat(result.countOf(Classification.QUARANTINED)).isEqualTo(1);
            assertThat(result.requiresReview()).isTrue();
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行1_ヘッダーが読めずデータ行がなければ取り込めなかった行はNULLと0でファイルの誤りの隔離により要確認にする() {
            IngestFileCommand command = command(
                    utf8("external_id,\"a\"b"),
                    0,
                    List.of(),
                    List.of(),
                    new UnreadablePart(null, 0, FormatDefect.UNREADABLE_HEADER, List.of(), null));

            Ingested result = ingested(service().ingest(steward, command));

            Ingestion ingestion = ingestions.all().getFirst();
            assertThat(ingestion.unparsedFromLine()).isNull();
            assertThat(ingestion.unparsedLineCount()).isZero();
            assertThat(onlyKeylessQuarantine().payloadObjectKey()).isEqualTo(ingestion.fileObjectKey());
            assertThat(result.unreadable().fromLine()).isNull();
            assertThat(result.requiresReview()).isTrue();
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行5_引用符の閉じ忘れは前の行を分類してから閉じ忘れからファイルの終わりまでの原文を保存しキーのない隔離を1件書く() {
            IngestedRow before = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
            String unclosed = "MS-0002,1,\"" + ACQUIRED + ",CTABCDEFGH2346\n" + line("MS-0003", "CTABCDEFGH2347") + "\n"
                    + line("MS-0004", "CTABCDEFGH2348");
            IngestFileCommand command = command(
                    utf8(MILESTONE_HEADER + "\n" + new String(before.payload(), StandardCharsets.UTF_8) + "\n"
                            + unclosed),
                    4,
                    List.of(before),
                    List.of(),
                    new UnreadablePart(3, 3, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8(unclosed)));

            Ingested result = ingested(service().ingest(steward, command));

            ReceivedRecord received = onlyKeylessQuarantine();
            assertThat(storage.read(received.payloadObjectKey())).isEqualTo(utf8(unclosed));
            assertThat(received.payloadHash()).isEqualTo(PayloadHash.of(utf8(unclosed)));
            assertThat(received.classificationNote())
                    .isEqualTo("3 行目からファイルの終わりまで: 引用符が閉じていません。3 行を取り込めませんでした。ファイルを確かめてください。");
            assertThat(calls)
                    .containsExactly(
                            "store:ingestions",
                            "store:records",
                            "received:ADOPTED",
                            "store:records",
                            "received:QUARANTINED");
            Ingestion ingestion = ingestions.all().getFirst();
            assertThat(ingestion.unparsedFromLine()).isEqualTo(3);
            assertThat(ingestion.unparsedLineCount()).isEqualTo(3);
            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::classification)
                    .containsExactly(tuple(2, Classification.ADOPTED));
            assertThat(result.unreadable()).isEqualTo(new Unreadable(3, 3, received.id()));
            assertThat(result.requiresReview()).isTrue();
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行6b_閉じた引用符の後の文字の行がファイルの終わりまで飲み込めば閉じ忘れと同じく取り込めなかった部分にする() {
            IngestedRow before = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
            String swallowed = "MS-0002,\"1\"x,\"" + ACQUIRED + ",CTABCDEFGH2346\n" + line("MS-0003", "CTABCDEFGH2347");
            IngestFileCommand command = command(
                    utf8(MILESTONE_HEADER + "\n" + new String(before.payload(), StandardCharsets.UTF_8) + "\n"
                            + swallowed),
                    3,
                    List.of(before),
                    List.of(),
                    new UnreadablePart(3, 2, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, List.of(), utf8(swallowed)));

            Ingested result = ingested(service().ingest(steward, command));

            ReceivedRecord received = onlyKeylessQuarantine();
            assertThat(storage.read(received.payloadObjectKey())).isEqualTo(utf8(swallowed));
            assertThat(received.classificationNote())
                    .isEqualTo("3 行目からファイルの終わりまで: 閉じた引用符の後に文字があります。2 行を取り込めませんでした。ファイルを確かめてください。");
            assertThat(ingestions.all().getFirst().unparsedLineCount()).isEqualTo(2);
            assertThat(result.rows()).extracting(RowResult::classification).containsExactly(Classification.ADOPTED);
            assertThat(result.requiresReview()).isTrue();
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行10_0バイトのファイルは必要な列がないファイルの誤りで件数0のままファイル全体の原本を指す隔離により要確認にする() {
            IngestFileCommand command = command(
                    new byte[0],
                    0,
                    List.of(),
                    List.of(),
                    new UnreadablePart(
                            null,
                            0,
                            FormatDefect.MISSING_COLUMN,
                            List.of("external_id", "external_version", "acquired_at", "tracking_number"),
                            null));

            Ingested result = ingested(service().ingest(steward, command));

            Ingestion ingestion = ingestions.all().getFirst();
            ReceivedRecord received = onlyKeylessQuarantine();
            assertThat(received.payloadObjectKey()).isEqualTo(ingestion.fileObjectKey());
            assertThat(received.classificationNote())
                    .isEqualTo("ファイル: 必要な列がありません（external_id、external_version、acquired_at、tracking_number）。");
            assertThat(ingestion.receivedCount()).isZero();
            assertThat(ingestion.unparsedFromLine()).isNull();
            assertThat(ingestion.unparsedLineCount()).isZero();
            assertThat(result.requiresReview()).isTrue();
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行10_ヘッダーだけで列がそろうファイルは取り込めなかった部分がなく要確認でない() {
            Ingested result = ingested(service().ingest(steward, file(milestoneSource)));

            assertThat(result.unreadable()).isNull();
            assertThat(result.requiresReview()).isFalse();
            assertThat(result.reconciles()).isTrue();
            assertThat(receivedRecords.all()).isEmpty();
        }

        @Test
        void 行12_文字コードの誤りはShift_JISのファイル全体の原本を指すキーのない隔離を1件書き最初のデータ行から受け取った件数のすべてを取り込めない() {
            byte[] shiftJis = (MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + ",東京\n"
                            + line("MS-0002", "CTABCDEFGH2346"))
                    .getBytes(Charset.forName("Shift_JIS"));
            IngestFileCommand command = command(
                    shiftJis,
                    2,
                    List.of(),
                    List.of(),
                    new UnreadablePart(2, 2, FormatDefect.UNDECODABLE, List.of(), null));

            Ingested result = ingested(service().ingest(steward, command));

            ReceivedRecord received = onlyKeylessQuarantine();
            assertThat(storage.read(received.payloadObjectKey())).isEqualTo(shiftJis);
            assertThat(received.classificationNote()).isEqualTo(FILE_NOTE_UNDECODABLE);
            Ingestion ingestion = ingestions.all().getFirst();
            assertThat(ingestion.unparsedFromLine()).isEqualTo(2);
            assertThat(ingestion.unparsedLineCount()).isEqualTo(2);
            assertThat(result.requiresReview()).isTrue();
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行13_形式の正しい行と誤りの行が混ざると物理行の順に誤りの行は原文を保存してからキーのない隔離にする() {
            IngestedRow first = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
            String broken = line("MS-0002", "CTABCDEFGH2346") + ",余分";
            IngestedRow third = milestone(4, "MS-0003", "1", "CTABCDEFGH2347");
            RejectedRow rejected = new RejectedRow(
                    3, 3, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 4 列", "この行 5 列"), utf8(broken));
            IngestFileCommand command = command(utf8("file"), 3, List.of(first, third), List.of(rejected), null);

            Ingested result = ingested(service().ingest(steward, command));

            ReceivedRecord received = onlyKeylessQuarantine();
            assertThat(storage.read(received.payloadObjectKey())).isEqualTo(utf8(broken));
            assertThat(received.payloadHash()).isEqualTo(PayloadHash.of(utf8(broken)));
            assertThat(received.classificationNote()).isEqualTo("3 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。");
            assertThat(calls)
                    .containsExactly(
                            "store:ingestions",
                            "store:records",
                            "received:ADOPTED",
                            "store:records",
                            "received:QUARANTINED",
                            "store:records",
                            "received:ADOPTED");
            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::classification, RowResult::externalRecordId)
                    .containsExactly(
                            tuple(2, Classification.ADOPTED, externalRecordIdOf("MS-0001")),
                            tuple(3, Classification.QUARANTINED, null),
                            tuple(4, Classification.ADOPTED, externalRecordIdOf("MS-0003")));
            assertThat(externalRecords.findByKey(keyOf(milestoneSource, "MS-0002", "1")))
                    .isEmpty();
            assertThat(result.rows().get(1).receivedRecordId()).isEqualTo(received.id());
            assertThat(result.countOf(Classification.ADOPTED)).isEqualTo(2);
            assertThat(result.countOf(Classification.QUARANTINED)).isEqualTo(1);
            assertThat(result.unreadable()).isNull();
            assertThat(result.requiresReview()).isFalse();
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行13_複数の物理行にまたがる誤りの行は注記に物理行の範囲を書く() {
            String broken = "MS-0002,\"1\"x,\"a\nb\"," + ACQUIRED + ",CTABCDEFGH2346";
            IngestFileCommand command = command(
                    utf8("file"),
                    2,
                    List.of(),
                    List.of(rejected(3, 4, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, broken)),
                    null);

            ingested(service().ingest(steward, command));

            assertThat(onlyKeylessQuarantine().classificationNote()).isEqualTo("3 行目から 4 行目まで: 閉じた引用符の後に文字があります。");
        }

        @Test
        void 行14_同じキーの誤りの行が先でも外部原本を引かず作らず後の正しい行を採用する() {
            assertSameKeyErrorRowIsIgnored(3, 2);
        }

        @Test
        void 行14_同じキーの誤りの行が後でも外部原本を引かず作らず先の正しい行を採用する() {
            assertSameKeyErrorRowIsIgnored(2, 3);
        }

        private void assertSameKeyErrorRowIsIgnored(int correctLine, int errorLine) {
            List<IdempotencyKey> lookups = new ArrayList<>();
            InMemoryExternalRecordRepository looking = new InMemoryExternalRecordRepository() {
                @Override
                public Optional<ExternalRecord> findByKey(IdempotencyKey key) {
                    lookups.add(key);
                    return super.findByKey(key);
                }
            };
            IngestedRow correct = milestone(correctLine, "MS-0001", "1", "CTABCDEFGH2345");
            RejectedRow error = rejected(
                    errorLine, errorLine, FormatDefect.BARE_QUOTE, "MS-0001,1," + ACQUIRED + ",CT\"ABCDEFGH2345");
            IngestFileCommand command = command(utf8("file"), 2, List.of(correct), List.of(error), null);

            Ingested result = ingested(new IngestionService(
                            sources,
                            ingestions,
                            looking,
                            new RecordingReceivedRecords(),
                            new RecordingStorage(storage),
                            clock)
                    .ingest(steward, command));

            assertThat(lookups).containsExactly(keyOf(milestoneSource, "MS-0001", "1"));
            assertThat(looking.all()).hasSize(1);
            assertThat(storage.read(looking.all().getFirst().payloadObjectKey()))
                    .isEqualTo(correct.payload());
            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::classification)
                    .containsExactly(
                            tuple(2, correctLine == 2 ? Classification.ADOPTED : Classification.QUARANTINED),
                            tuple(3, correctLine == 3 ? Classification.ADOPTED : Classification.QUARANTINED));
        }

        @Test
        void 行15_受け取った件数は行の結果の物理行の合計と取り込めなかった物理行の数の和に一致する() {
            String multiLine = "MS-0001,1," + ACQUIRED + ",CTABCDEFGH2345,\"備考の 1 行目\n備考の 2 行目\"";
            IngestedRow spanning = new IngestedRow(2, 3, "MS-0001", "1", ACQUIRED, utf8(multiLine));
            String unclosed = "MS-0003,1,\"x\n" + line("MS-0004", "CTABCDEFGH2348");
            IngestFileCommand command = command(
                    utf8("file"),
                    5,
                    List.of(spanning),
                    List.of(rejected(5, 5, FormatDefect.BARE_QUOTE, "MS-0002,1,a\"b,CTABCDEFGH2346")),
                    new UnreadablePart(6, 2, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8(unclosed)));

            Ingested result = ingested(service().ingest(steward, command));

            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::lastLineNumber)
                    .containsExactly(tuple(2, 3), tuple(5, 5));
            assertThat(result.receivedCount()).isEqualTo(5);
            assertThat(result.reconciles()).isTrue();
        }

        @Test
        void 行15_受け取った件数が行の結果と取り込めなかった物理行の和と違えば突き合わせは合わない() {
            IngestFileCommand command =
                    command(utf8("file"), 2, List.of(milestone(2, "MS-0001", "1", "CTABCDEFGH2345")), List.of(), null);

            Ingested result = ingested(service().ingest(steward, command));

            assertThat(result.reconciles()).isFalse();
        }

        @Test
        void 行15_取り込めなかった物理行があるのに始まりがない部分はコマンドにできない() {
            assertThatThrownBy(() -> new UnreadablePart(null, 2, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8("x")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UnreadablePart(3, 0, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8("x")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UnreadablePart(null, 0, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8("x")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行15_終わりが始まりより前の行はコマンドにできない() {
            assertThatThrownBy(() -> rejected(4, 3, FormatDefect.BARE_QUOTE, "x"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new IngestedRow(4, 3, "MS-0001", "1", ACQUIRED, utf8("x")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行15_外部原本を持たない行の結果は隔離だけ() {
            assertThatThrownBy(() -> new RowResult(2, 2, Classification.ADOPTED, null, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行16_誤りの行の原本の保存に失敗したらその行の受信記録を書かず例外のまま上げる() {
            IngestionService failing = service(failingOnRecordStore(2));
            IngestFileCommand command = command(
                    utf8("file"),
                    2,
                    List.of(milestone(2, "MS-0001", "1", "CTABCDEFGH2345")),
                    List.of(rejected(3, 3, FormatDefect.BARE_QUOTE, "MS-0002,1,a\"b,CTABCDEFGH2346")),
                    null);

            assertThatThrownBy(() -> failing.ingest(steward, command))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("原本の保存に失敗した");
            assertThat(receivedRecords.all())
                    .extracting(ReceivedRecord::classification)
                    .containsExactly(Classification.ADOPTED);
        }

        @Test
        void 行16_取り込めなかった部分の原本の保存に失敗したら受信記録を書かず例外のまま上げる() {
            IngestionService failing = service(failingOnRecordStore(1));
            IngestFileCommand command = command(
                    utf8("file"),
                    1,
                    List.of(),
                    List.of(),
                    new UnreadablePart(2, 1, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8("MS-0001,\"1")));

            assertThatThrownBy(() -> failing.ingest(steward, command)).isInstanceOf(IllegalStateException.class);
            assertThat(receivedRecords.all()).isEmpty();
        }

        private ExternalPayloadStorage failingOnRecordStore(int failingCall) {
            return new RecordingStorage(storage) {
                private int records;

                @Override
                public void store(PayloadObjectKey key, byte[] payload) {
                    if (key.value().startsWith("externaldata/records/") && ++records == failingCall) {
                        throw new IllegalStateException("原本の保存に失敗した");
                    }
                    super.store(key, payload);
                }
            };
        }

        @Test
        void 行17_注記が1000文字を超えたら末尾をで切る() {
            List<String> longColumns = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                longColumns.add("very_long_required_column_name_" + i);
            }
            IngestFileCommand command = command(
                    utf8("x"),
                    0,
                    List.of(),
                    List.of(),
                    new UnreadablePart(null, 0, FormatDefect.MISSING_COLUMN, longColumns, null));

            ingested(service().ingest(steward, command));

            String note = onlyKeylessQuarantine().classificationNote();
            assertThat(note.codePointCount(0, note.length())).isEqualTo(1000);
            assertThat(note)
                    .startsWith("ファイル: 必要な列がありません（very_long_required_column_name_0、")
                    .endsWith("…");
        }

        @Test
        void 行20_内容の違う原本の隔離の注記の時刻は秒までのUTCと明記した形にする() {
            service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));

            service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2346")));

            assertThat(receivedRecords.all().getLast().classificationNote())
                    .isEqualTo("2 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-03 09:00:00 UTC）。");
        }
    }

    /** 保存の呼び出しを記録する（保存 → 受信記録の順を確かめる）。 */
    private class RecordingStorage implements ExternalPayloadStorage {

        private final ExternalPayloadStorage delegate;

        RecordingStorage(ExternalPayloadStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public void store(PayloadObjectKey key, byte[] payload) {
            delegate.store(key, payload);
            calls.add("store:" + key.value().split("/")[1]);
        }

        @Override
        public byte[] read(PayloadObjectKey key) {
            return delegate.read(key);
        }
    }

    /** 受信記録の追記を記録する。 */
    private class RecordingReceivedRecords extends InMemoryReceivedRecordRepository {

        @Override
        public void add(ReceivedRecord receivedRecord) {
            receivedRecords.add(receivedRecord);
            calls.add("received:" + receivedRecord.classification());
        }

        @Override
        public int countByIngestionAndClassification(UUID ingestionId, Classification classification) {
            return receivedRecords.countByIngestionAndClassification(ingestionId, classification);
        }
    }
}

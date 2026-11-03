package com.example.cargotracker.externaldata.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
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
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 取込のサービス（Bolt 39b の行 1〜5b・7〜8・9b・10〜12。US-14 AC1〜AC4、BR-12、BR-18、ED-INV-01・ED-INV-02）。メモリの実装と
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
        return new IngestedRow(lineNumber, externalId, version, ACQUIRED, line.getBytes(StandardCharsets.UTF_8));
    }

    private static IngestFileCommand file(DataSource source, IngestedRow... rows) {
        StringBuilder content = new StringBuilder(MILESTONE_HEADER);
        for (IngestedRow row : rows) {
            content.append('\n').append(new String(row.payload(), StandardCharsets.UTF_8));
        }
        return new IngestFileCommand(
                source.id(), content.toString().getBytes(StandardCharsets.UTF_8), rows.length, List.of(rows));
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
        IngestedRow row = milestone(2, "MS-0001", "1", "TRK-0001");

        Ingested result = ingested(service().ingest(steward, file(milestoneSource, row)));

        ExternalRecord record = externalRecords
                .findByKey(keyOf(milestoneSource, "MS-0001", "1"))
                .orElseThrow();
        ReceivedRecord received = receivedRecords.all().getFirst();
        assertThat(record.payloadHash()).isEqualTo(PayloadHash.of(row.payload()));
        assertThat(storage.read(record.payloadObjectKey())).isEqualTo(row.payload());
        assertThat(record.firstReceivedAt()).isEqualTo(NOW);
        assertThat(received.classification()).isEqualTo(Classification.ADOPTED);
        assertThat(received.classificationNote()).isNull();
        assertThat(received.externalRecordId()).isEqualTo(record.id());
        assertThat(received.ingestionId()).isEqualTo(result.ingestionId());
        assertThat(received.payloadObjectKey()).isEqualTo(record.payloadObjectKey());
        assertThat(result.rows()).containsExactly(new RowResult(2, Classification.ADOPTED, record.id(), received.id()));
    }

    @Test
    void 行1_原本はファイル全体と行の順に保存してから受信記録を書く() {
        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001")));

        assertThat(calls).containsExactly("store:ingestions", "store:records", "received:ADOPTED");
    }

    @Test
    void 行2_同じキーと同じ内容を別の取込で再送すると重複になり外部原本は1つのまま既存の外部原本IDを返す() {
        IngestedRow row = milestone(2, "MS-0001", "1", "TRK-0001");
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
        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001")));
        ExternalRecord before = externalRecords.all().getFirst();

        Ingested second =
                ingested(service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0002"))));

        assertThat(externalRecords.all()).containsExactly(before);
        assertThat(second.rows().getFirst().classification()).isEqualTo(Classification.QUARANTINED);
        ReceivedRecord received = receivedRecords.all().getLast();
        assertThat(received.payloadHash()).isNotEqualTo(before.payloadHash());
        assertThat(received.classificationNote()).contains("同じ外部 ID・外部版で内容の違う原本を受け取った（最初の受信: " + NOW + "）");
    }

    @Test
    void 行4_同じファイルの中の同じキーは行の順に先の行を採用し後の行は同じ内容なら重複違えば隔離() {
        Ingested result = ingested(service()
                .ingest(
                        steward,
                        file(
                                milestoneSource,
                                milestone(2, "MS-0001", "1", "TRK-0001"),
                                milestone(3, "MS-0001", "1", "TRK-0001"),
                                milestone(4, "MS-0001", "1", "TRK-0002"))));

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

        IngestionOutcome outcome = service().ingest(steward, file(candidate, milestone(2, "MS-0001", "1", "TRK-0001")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.DataSourceNotApproved.class);
        assertNothingWritten();
    }

    @Test
    void 行5_停止中の情報源の取込は拒否し何も保存しない() {
        DataSource suspended = source("運送会社S-" + UUID.randomUUID(), "MILESTONE", ApprovalStatus.SUSPENDED);

        IngestionOutcome outcome = service().ingest(steward, file(suspended, milestone(2, "MS-0001", "1", "TRK-0001")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.DataSourceNotApproved.class);
        assertNothingWritten();
    }

    @Test
    void 行5_ない情報源の取込は拒否し何も保存しない() {
        DataSource unknown = new DataSource(UUID.randomUUID(), "未登録の提供元", "MILESTONE", ApprovalStatus.APPROVED);

        IngestionOutcome outcome = service().ingest(steward, file(unknown, milestone(2, "MS-0001", "1", "TRK-0001")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.DataSourceNotFound.class);
        assertNothingWritten();
    }

    @Test
    void 行5b_データ責任者でない操作者の取込は拒否し何も保存しない() {
        IngestionOutcome outcome = service()
                .ingest(actor(Role.TRACKING_MANAGER), file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001")));

        assertThat(outcome).isInstanceOf(IngestionOutcome.NotPermitted.class);
        assertNothingWritten();
    }

    @Test
    void 行5b_役割は情報源より先に確かめる() {
        IngestionOutcome outcome = service()
                .ingest(actor(Role.SYSTEM_ADMIN), new IngestFileCommand(UUID.randomUUID(), new byte[0], 0, List.of()));

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

        Ingested first =
                ingested(service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001"))));
        Ingested second =
                ingested(service().ingest(steward, file(otherProvider, milestone(2, "MS-0001", "1", "TRK-0001"))));

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
                new IngestedRow(2, "MS-0001", "1", ACQUIRED, voyageLine.getBytes(StandardCharsets.UTF_8));

        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001")));
        Ingested voyage = ingested(service()
                .ingest(
                        steward,
                        new IngestFileCommand(
                                voyageSource.id(),
                                ("external_id,external_version,acquired_at\n" + voyageLine)
                                        .getBytes(StandardCharsets.UTF_8),
                                1,
                                List.of(voyageRow))));

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
                milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001"), milestone(3, "MS-0002", "1", "TRK-0002"));

        assertThatThrownBy(() -> service(failingOnSecondRow).ingest(steward, command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("原本の保存に失敗した");
        assertThat(receivedRecords.all()).hasSize(1);
        assertThat(externalRecords.findByKey(keyOf(milestoneSource, "MS-0002", "1")))
                .isEmpty();
    }

    @Test
    void 行9b_同時の取込で同じ新しいキーを先に書かれたら一意制約の違反のまま例外を上げる() {
        IngestedRow row = milestone(2, "MS-0001", "1", "TRK-0001");
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
        IngestionService racingService = new IngestionService(
                sources, ingestions, racing, receivedRecords, new RecordingStorage(storage), clock);

        assertThatThrownBy(() -> racingService.ingest(steward, file(milestoneSource, row)))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(receivedRecords.all()).isEmpty();
    }

    @Test
    void 行10_取込者は操作者で取込と外部原本と受信記録の時刻はClockの時刻で取得時刻は行の値() {
        Ingested result =
                ingested(service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001"))));

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
        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "TRK-0001")));

        Ingested result = ingested(service()
                .ingest(
                        steward,
                        file(
                                milestoneSource,
                                milestone(2, "MS-0001", "1", "TRK-0001"),
                                milestone(3, "MS-0001", "1", "TRK-0009"),
                                milestone(4, "MS-0002", "1", "TRK-0002"))));

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

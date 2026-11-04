package com.example.cargotracker.externaldata.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.RejectedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Unreadable;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateExternalRecordException;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryAdoptedValueRepository;
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
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.lang.reflect.RecordComponent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 取込のサービス（Bolt 39b の行 1〜5b・7〜8・9b・10〜12、形式の誤りは入れ子のクラスで Bolt 39b2 の行番号。US-14 AC1〜AC4、BR-12、BR-18、
 * ED-INV-01・ED-INV-02）。メモリの実装と
 * 固定の Clock で確かめる。取得時刻は取込時刻より前、主要実績の行は payload に追跡番号を持ち、航海の行は持たない（T-230）。
 */
class IngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00.123456Z");
    private static final Instant ACQUIRED = Instant.parse("2026-11-03T08:30:00Z");
    private static final String MILESTONE_HEADER =
            "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";
    private static final Instant OCCURRED = Instant.parse("2026-11-03T08:00:00Z");

    /** 主要実績の行の値の列（追跡番号の後。出発・東京・取得時刻より前の発生時刻。T-230）。 */
    private static final String MILESTONE_TAIL = ",DEPARTURE,JPTYO," + OCCURRED;

    private final List<String> calls = new ArrayList<>();
    private final InMemoryDataSourceRepository sources = new InMemoryDataSourceRepository();
    private final InMemoryIngestionRepository ingestions = new InMemoryIngestionRepository();
    /** 外部原本を引いたキーの記録（形式の誤り・値の誤りの行で引かないことを確かめる。行 14・18、変異 V8）。 */
    private final List<IdempotencyKey> lookups = new ArrayList<>();

    private final InMemoryExternalRecordRepository externalRecords = new InMemoryExternalRecordRepository() {
        @Override
        public Optional<ExternalRecord> findByKey(IdempotencyKey key) {
            lookups.add(key);
            return super.findByKey(key);
        }
    };
    private final InMemoryReceivedRecordRepository receivedRecords = new InMemoryReceivedRecordRepository();
    private final InMemoryAdoptedValueRepository adoptedValues = new InMemoryAdoptedValueRepository();
    private final InMemoryExternalPayloadStorage storage = new InMemoryExternalPayloadStorage();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    /** 発行したイベント（出したイベントの値と、出さないことを発行の記録で確かめる。T-236）。 */
    private final List<Object> published = new ArrayList<>();
    /** 発行を記録する publisher。呼び出しの記録にも型の名前を残す（受信記録 → 採用値 → イベントの順を確かめる。Bolt 39b3）。 */
    private final ApplicationEventPublisher publisher = event -> {
        published.add(event);
        calls.add("event:" + event.getClass().getSimpleName());
    };

    private final AuthenticatedActor steward = actor(Role.DATA_STEWARD);
    private final DataSource milestoneSource = approvedSource("運送会社A-" + UUID.randomUUID(), "MILESTONE");

    private IngestionService service() {
        return service(new RecordingStorage(storage));
    }

    private IngestionService service(ExternalPayloadStorage payloadStorage) {
        return new IngestionService(
                sources,
                ingestions,
                externalRecords,
                new RecordingReceivedRecords(),
                new RecordingAdoptedValues(),
                payloadStorage,
                publisher,
                clock);
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
        String line = externalId + "," + version + "," + ACQUIRED + "," + trackingNumber + MILESTONE_TAIL;
        return new IngestedRow(
                lineNumber,
                lineNumber,
                externalId,
                version,
                ACQUIRED,
                milestoneValues(trackingNumber),
                line.getBytes(StandardCharsets.UTF_8));
    }

    /** 値の既定（出発・東京・発生時刻）の主要実績の値。取込のサービスは採用値の材料にだけ使う（Bolt 39b3）。 */
    private static MilestoneValues milestoneValues(String trackingNumber) {
        return new MilestoneValues(
                new TrackingNumberValue(trackingNumber),
                MilestoneKindValue.DEPARTURE,
                new Location("JPTYO"),
                new UtcInstant(OCCURRED));
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

        assertThat(calls)
                .containsExactly(
                        "store:ingestions",
                        "store:records",
                        "received:ADOPTED",
                        "adopted:MILESTONE",
                        "event:MilestoneAdoptedValueAccepted");
    }

    @Test
    void 行1_行13_採用の主要実績の行は採用値を1つ追記し要約は値から作り情報版は外部版で公開した時刻は取込の時刻() {
        IngestedRow row = milestone(2, "MS-0001", "7", "CTABCDEFGH2345");

        ingested(service().ingest(steward, file(milestoneSource, row)));

        ReceivedRecord received = receivedRecords.all().getFirst();
        assertThat(adoptedValues.all()).singleElement().satisfies(adopted -> {
            assertThat(adopted.receivedRecordId()).isEqualTo(received.id());
            assertThat(adopted.targetType()).isEqualTo(TargetType.MILESTONE);
            assertThat(adopted.valueSummary()).isEqualTo("DEPARTURE;JPTYO;2026-11-03T08:00:00.000000Z");
            assertThat(adopted.infoVersion()).isEqualTo("7");
            assertThat(adopted.publishedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void 行14_主要実績の採用値の対象のキーは外部IDではなく追跡番号() {
        ingested(service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345"))));

        assertThat(adoptedValues.findByReceivedRecordId(
                        receivedRecords.all().getFirst().id()))
                .hasValueSatisfying(adopted -> assertThat(adopted.targetKey()).isEqualTo("CTABCDEFGH2345"));
    }

    @Test
    void 行1_行14_採用の航海の行は対象のキーを航海番号にした採用値を1つ追記する() {
        DataSource voyageSource = approvedSource("船会社B-" + UUID.randomUUID(), "VOYAGE");
        String voyageLine = "V2611E,3," + ACQUIRED + ",JPTYO;;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;";
        VoyageValues voyageValues = new VoyageValues(
                "V2611E",
                List.of(
                        new PortCallValue(
                                new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-20T00:00:00Z"))),
                        new PortCallValue(
                                new Location("USLAX"), new UtcInstant(Instant.parse("2026-12-01T00:00:00Z")), null)));
        IngestedRow voyageRow = new IngestedRow(
                2, 2, "V2611E", "3", ACQUIRED, voyageValues, voyageLine.getBytes(StandardCharsets.UTF_8));

        ingested(service()
                .ingest(
                        steward,
                        new IngestFileCommand(
                                voyageSource.id(),
                                ("external_id,external_version,acquired_at,port_calls\n" + voyageLine)
                                        .getBytes(StandardCharsets.UTF_8),
                                1,
                                List.of(voyageRow),
                                List.of(),
                                null)));

        assertThat(adoptedValues.all()).singleElement().satisfies(adopted -> {
            assertThat(adopted.receivedRecordId())
                    .isEqualTo(receivedRecords.all().getFirst().id());
            assertThat(adopted.targetType()).isEqualTo(TargetType.VOYAGE);
            assertThat(adopted.targetKey()).isEqualTo("V2611E");
            assertThat(adopted.valueSummary())
                    .isEqualTo("JPTYO;;2026-11-20T00:00:00.000000Z|USLAX;2026-12-01T00:00:00.000000Z;");
            assertThat(adopted.infoVersion()).isEqualTo("3");
            assertThat(adopted.publishedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void 行2_重複の行は採用値を追記せずイベントも出さない() {
        IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
        ingested(service().ingest(steward, file(milestoneSource, row)));
        calls.clear();
        published.clear();

        Ingested resent = ingested(service().ingest(steward, file(milestoneSource, row)));

        assertThat(resent.rows().getFirst().classification()).isEqualTo(Classification.DUPLICATE);
        assertThat(calls)
                .as("重複の行では採用値の追記もイベントの発行も呼ばない（T-236）")
                .containsExactly("store:ingestions", "store:records", "received:DUPLICATE");
        assertThat(published).isEmpty();
    }

    @Test
    void 行3_内容の違う原本の隔離は採用値を追記せずキーと理由の区分CONTENT_MISMATCHと物理行の範囲を持つDE14を1つ出す() {
        service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2345")));
        calls.clear();
        published.clear();

        Ingested changed = ingested(
                service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "1", "CTABCDEFGH2346"))));

        assertThat(calls)
                .as("隔離の行では採用値の追記を呼ばない（T-236）")
                .containsExactly(
                        "store:ingestions", "store:records", "received:QUARANTINED", "event:ExternalRecordQuarantined");
        assertThat(published)
                .containsExactly(new ExternalRecordQuarantined(
                        changed.rows().getFirst().receivedRecordId(),
                        changed.ingestionId(),
                        milestoneSource.id(),
                        milestoneSource.provider(),
                        "MILESTONE",
                        "MS-0001",
                        "1",
                        ExternalRecordQuarantined.CONTENT_MISMATCH,
                        2,
                        2,
                        steward.userId(),
                        steward.companyId(),
                        new UtcInstant(NOW)));
    }

    @Test
    void 行6_行13_航海の採用は出典と寄港の並びと情報版と操作者を持つDE12を1つ出し公開した時刻は取込の時刻で取得時刻はファイルの値() {
        DataSource voyageSource = approvedSource("船会社B-" + UUID.randomUUID(), "VOYAGE");
        String voyageLine = "V2611E,3," + ACQUIRED + ",JPTYO;;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;";
        UtcInstant departure = new UtcInstant(Instant.parse("2026-11-20T00:00:00Z"));
        UtcInstant arrival = new UtcInstant(Instant.parse("2026-12-01T00:00:00Z"));
        VoyageValues voyageValues = new VoyageValues(
                "V2611E",
                List.of(
                        new PortCallValue(new Location("JPTYO"), null, departure),
                        new PortCallValue(new Location("USLAX"), arrival, null)));
        IngestedRow voyageRow = new IngestedRow(
                2, 2, "V2611E", "3", ACQUIRED, voyageValues, voyageLine.getBytes(StandardCharsets.UTF_8));

        Ingested result = ingested(service()
                .ingest(
                        steward,
                        new IngestFileCommand(
                                voyageSource.id(),
                                ("external_id,external_version,acquired_at,port_calls\n" + voyageLine)
                                        .getBytes(StandardCharsets.UTF_8),
                                1,
                                List.of(voyageRow),
                                List.of(),
                                null)));

        UUID receivedRecordId = result.rows().getFirst().receivedRecordId();
        assertThat(published)
                .as("DE-13 は出ない")
                .containsExactly(new VoyageAdoptedValueUpdated(
                        adoptedValues.all().getFirst().id(),
                        result.ingestionId(),
                        voyageSource.id(),
                        new Source(SourceKind.EXTERNAL_RECORD, receivedRecordId.toString(), new UtcInstant(ACQUIRED)),
                        "V2611E",
                        List.of(
                                new AdoptedPortCall(new Location("JPTYO"), null, departure),
                                new AdoptedPortCall(new Location("USLAX"), arrival, null)),
                        "3",
                        steward.userId(),
                        steward.companyId(),
                        new UtcInstant(NOW)));
    }

    @Test
    void 行7_行13_主要実績の採用は追跡番号と種類と場所と発生時刻と外部IDと出典を持つDE13を1つ出す() {
        Ingested result = ingested(
                service().ingest(steward, file(milestoneSource, milestone(2, "MS-0001", "7", "CTABCDEFGH2345"))));

        UUID receivedRecordId = result.rows().getFirst().receivedRecordId();
        assertThat(published)
                .as("DE-12 は出ない")
                .containsExactly(new MilestoneAdoptedValueAccepted(
                        adoptedValues.all().getFirst().id(),
                        result.ingestionId(),
                        milestoneSource.id(),
                        new Source(SourceKind.EXTERNAL_RECORD, receivedRecordId.toString(), new UtcInstant(ACQUIRED)),
                        "CTABCDEFGH2345",
                        "DEPARTURE",
                        new Location("JPTYO"),
                        new UtcInstant(OCCURRED),
                        "MS-0001",
                        "7",
                        steward.userId(),
                        steward.companyId(),
                        new UtcInstant(NOW)));
    }

    @Test
    void 行8_同じ取込の複数の採用と後の同じキーと間の行の誤りは物理行の順に受信記録と採用値とイベントを書き重複には出さない() {
        RejectedRow broken = new RejectedRow(
                3, 3, FormatDefect.COLUMN_COUNT_MISMATCH, List.of(), "MS-0009,1,余分".getBytes(StandardCharsets.UTF_8));
        List<IngestedRow> rows = List.of(
                milestone(2, "MS-0001", "1", "CTABCDEFGH2345"),
                milestone(4, "MS-0001", "1", "CTABCDEFGH2345"),
                milestone(5, "MS-0001", "1", "CTABCDEFGH2346"),
                milestone(6, "MS-0002", "1", "CTABCDEFGH2347"));
        IngestFileCommand command = new IngestFileCommand(
                milestoneSource.id(), "file".getBytes(StandardCharsets.UTF_8), 5, rows, List.of(broken), null);

        Ingested result = ingested(service().ingest(steward, command));

        assertThat(calls)
                .filteredOn(call -> !call.startsWith("store:"))
                .containsExactly(
                        "received:ADOPTED",
                        "adopted:MILESTONE",
                        "event:MilestoneAdoptedValueAccepted",
                        "received:QUARANTINED",
                        "event:ExternalRecordQuarantined",
                        "received:DUPLICATE",
                        "received:QUARANTINED",
                        "event:ExternalRecordQuarantined",
                        "received:ADOPTED",
                        "adopted:MILESTONE",
                        "event:MilestoneAdoptedValueAccepted");
        List<UUID> receivedIds =
                result.rows().stream().map(RowResult::receivedRecordId).toList();
        assertThat(published)
                .extracting(event -> switch (event) {
                    case MilestoneAdoptedValueAccepted accepted ->
                        accepted.source().reference();
                    case ExternalRecordQuarantined quarantined ->
                        quarantined.receivedRecordId().toString();
                    default -> throw new AssertionError(event);
                })
                .containsExactly(
                        receivedIds.get(0).toString(),
                        receivedIds.get(1).toString(),
                        receivedIds.get(3).toString(),
                        receivedIds.get(4).toString());
        assertThat(published)
                .filteredOn(MilestoneAdoptedValueAccepted.class::isInstance)
                .extracting(event -> ((MilestoneAdoptedValueAccepted) event).adoptedValueId())
                .containsExactlyElementsOf(
                        adoptedValues.all().stream().map(AdoptedValue::id).toList());
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
        assertThat(received.classificationNote()).isEqualTo("2 行目: 同じ原本を受け取り済みです。");
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
        // 航海の外部 ID は航海番号の形にする（T-230）。主要実績と同じ外部 ID でも原本種別が違えば別のキー
        String voyageLine = "V-101,1," + ACQUIRED + ",JPTYO;;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;";
        VoyageValues voyageValues = new VoyageValues(
                "V-101",
                List.of(
                        new PortCallValue(
                                new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-20T00:00:00Z"))),
                        new PortCallValue(
                                new Location("USLAX"), new UtcInstant(Instant.parse("2026-12-01T00:00:00Z")), null)));
        IngestedRow voyageRow = new IngestedRow(
                2, 2, "V-101", "1", ACQUIRED, voyageValues, voyageLine.getBytes(StandardCharsets.UTF_8));

        service().ingest(steward, file(milestoneSource, milestone(2, "V-101", "1", "CTABCDEFGH2345")));
        Ingested voyage = ingested(service()
                .ingest(
                        steward,
                        new IngestFileCommand(
                                voyageSource.id(),
                                ("external_id,external_version,acquired_at,port_calls\n" + voyageLine)
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
    void 行9b_行12_同時の取込で同じ新しいキーを先に書かれたら名前付きの例外を捕まえずに上げる() {
        IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
        IngestionService racingService = racingService(row);
        IngestFileCommand command = file(milestoneSource, row);

        assertThatThrownBy(() -> racingService.ingest(steward, command))
                .isInstanceOf(DuplicateExternalRecordException.class);
        assertThat(receivedRecords.all()).isEmpty();
    }

    @Test
    void 行9b_同時の取込で失敗した行を取り込み直せば先に書かれた外部原本に対して同じ内容は重複違う内容は隔離() {
        IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
        IngestionService racingService = racingService(row);
        IngestFileCommand command = file(milestoneSource, row);
        assertThatThrownBy(() -> racingService.ingest(steward, command))
                .isInstanceOf(DuplicateExternalRecordException.class);

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
        return new IngestionService(
                sources,
                ingestions,
                racing,
                receivedRecords,
                adoptedValues,
                new RecordingStorage(storage),
                publisher,
                clock);
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
            return externalId + ",1," + ACQUIRED + "," + trackingNumber + MILESTONE_TAIL;
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
            assertThat(calls)
                    .containsExactly("store:ingestions", "received:QUARANTINED", "event:ExternalRecordQuarantined");
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
                            "adopted:MILESTONE",
                            "event:MilestoneAdoptedValueAccepted",
                            "store:records",
                            "received:QUARANTINED",
                            "event:ExternalRecordQuarantined");
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
                    .isEqualTo("ファイル: 必要な列がありません（external_id、external_version、acquired_at、tracking_number）。"
                            + "選んだ情報源の種別に合ったファイルか確かめてください。合っていれば提供元に確かめてください。");
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
            assertThat(received.classificationNote()).isEqualTo("3 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。提供元に確かめてください。");
            assertThat(calls)
                    .containsExactly(
                            "store:ingestions",
                            "store:records",
                            "received:ADOPTED",
                            "adopted:MILESTONE",
                            "event:MilestoneAdoptedValueAccepted",
                            "store:records",
                            "received:QUARANTINED",
                            "event:ExternalRecordQuarantined",
                            "store:records",
                            "received:ADOPTED",
                            "adopted:MILESTONE",
                            "event:MilestoneAdoptedValueAccepted");
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

        /** キーのない隔離の DE-14（Bolt 39b3 の行 4・5）。外部 ID・外部版は null、理由の区分は形式の誤りの名前。 */
        private ExternalRecordQuarantined keyless(
                UUID receivedRecordId, FormatDefect defect, Integer firstLine, Integer lastLine) {
            return new ExternalRecordQuarantined(
                    receivedRecordId,
                    ingestions.all().getFirst().id(),
                    milestoneSource.id(),
                    milestoneSource.provider(),
                    "MILESTONE",
                    null,
                    null,
                    defect.name(),
                    firstLine,
                    lastLine,
                    steward.userId(),
                    steward.companyId(),
                    new UtcInstant(NOW));
        }

        @Test
        void 行4_行の誤りは採用値を追記せず外部IDと外部版がnullで理由の区分が形式の誤りの名前のDE14を物理行の範囲で1つ出す() {
            IngestFileCommand command = command(
                    utf8("file"),
                    2,
                    List.of(),
                    List.of(rejected(3, 4, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, "MS-0002,\"1\"x,\"a\nb\"")),
                    null);

            ingested(service().ingest(steward, command));

            assertThat(calls)
                    .as("行の誤りでは採用値の追記を呼ばない（T-236）")
                    .containsExactly(
                            "store:ingestions",
                            "store:records",
                            "received:QUARANTINED",
                            "event:ExternalRecordQuarantined");
            assertThat(published)
                    .containsExactly(
                            keyless(onlyKeylessQuarantine().id(), FormatDefect.TEXT_AFTER_CLOSING_QUOTE, 3, 4));
        }

        @Test
        void 行5_ファイルの誤りは物理行の範囲もnullのDE14を1つ出す() {
            IngestFileCommand command = command(
                    utf8("file"),
                    2,
                    List.of(),
                    List.of(),
                    new UnreadablePart(2, 2, FormatDefect.UNDECODABLE, List.of(), null));

            ingested(service().ingest(steward, command));

            assertThat(published)
                    .containsExactly(keyless(onlyKeylessQuarantine().id(), FormatDefect.UNDECODABLE, null, null));
        }

        @Test
        void 行5_引用符の閉じ忘れは前の行のイベントの後に始まりの物理行とファイルの終わりの物理行のDE14を1つ出す() {
            IngestedRow before = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");
            String unclosed = "MS-0002,1,\"" + ACQUIRED + ",CTABCDEFGH2346\n" + line("MS-0003", "CTABCDEFGH2347") + "\n"
                    + line("MS-0004", "CTABCDEFGH2348");
            IngestFileCommand command = command(
                    utf8("file"),
                    4,
                    List.of(before),
                    List.of(),
                    new UnreadablePart(3, 3, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8(unclosed)));

            ingested(service().ingest(steward, command));

            assertThat(published).hasSize(2);
            assertThat(published.getFirst()).isInstanceOf(MilestoneAdoptedValueAccepted.class);
            assertThat(published.getLast())
                    .isEqualTo(keyless(onlyKeylessQuarantine().id(), FormatDefect.UNCLOSED_QUOTE, 3, 5));
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

            assertThat(onlyKeylessQuarantine().classificationNote())
                    .isEqualTo("3 行目から 4 行目まで: 閉じた引用符の後に文字があります。提供元に確かめてください。");
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
            IngestedRow correct = milestone(correctLine, "MS-0001", "1", "CTABCDEFGH2345");
            RejectedRow error = rejected(
                    errorLine, errorLine, FormatDefect.BARE_QUOTE, "MS-0001,1," + ACQUIRED + ",CT\"ABCDEFGH2345");
            IngestFileCommand command = command(utf8("file"), 2, List.of(correct), List.of(error), null);

            Ingested result = ingested(service().ingest(steward, command));

            assertThat(lookups).containsExactly(keyOf(milestoneSource, "MS-0001", "1"));
            assertThat(externalRecords.all()).hasSize(1);
            assertThat(storage.read(externalRecords.all().getFirst().payloadObjectKey()))
                    .isEqualTo(correct.payload());
            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::classification)
                    .containsExactly(
                            tuple(2, correctLine == 2 ? Classification.ADOPTED : Classification.QUARANTINED),
                            tuple(3, correctLine == 3 ? Classification.ADOPTED : Classification.QUARANTINED));
        }

        @Test
        void 行15_受け取った件数は行の結果の物理行の合計と取り込めなかった物理行の数の和に一致する() {
            String multiLine = line("MS-0001", "CTABCDEFGH2345") + ",\"備考の 1 行目\n備考の 2 行目\"";
            IngestedRow spanning =
                    new IngestedRow(2, 3, "MS-0001", "1", ACQUIRED, milestoneValues("CTABCDEFGH2345"), utf8(multiLine));
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
            byte[] raw = utf8("x");
            List<String> none = List.of();

            assertThatThrownBy(() -> new UnreadablePart(null, 2, FormatDefect.UNCLOSED_QUOTE, none, raw))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UnreadablePart(3, 0, FormatDefect.UNCLOSED_QUOTE, none, raw))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UnreadablePart(null, 0, FormatDefect.UNCLOSED_QUOTE, none, raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行15_終わりが始まりより前の行はコマンドにできない() {
            byte[] raw = utf8("x");
            List<String> none = List.of();

            assertThatThrownBy(() -> new RejectedRow(4, 3, FormatDefect.BARE_QUOTE, none, raw))
                    .isInstanceOf(IllegalArgumentException.class);
            MilestoneValues values = milestoneValues("CTABCDEFGH2345");
            assertThatThrownBy(() -> new IngestedRow(4, 3, "MS-0001", "1", ACQUIRED, values, raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行15_外部原本を持たない行の結果は隔離だけ() {
            UUID receivedRecordId = UUID.randomUUID();

            assertThatThrownBy(() -> new RowResult(2, 2, Classification.ADOPTED, null, receivedRecordId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行1_行の誤りにならない形式の誤りは行の誤りのコマンドにできない() {
            byte[] raw = utf8("x");
            List<String> none = List.of();

            for (FormatDefect defect : List.of(
                    FormatDefect.UNCLOSED_QUOTE,
                    FormatDefect.UNDECODABLE,
                    FormatDefect.UNREADABLE_HEADER,
                    FormatDefect.MISSING_COLUMN,
                    FormatDefect.DUPLICATE_COLUMN)) {
                assertThatThrownBy(() -> new RejectedRow(2, 2, defect, none, raw))
                        .as("行の誤りの %s", defect)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        void 行1_ファイルの誤りは原文を持たずファイルの終わりまでの範囲は原文を持つ() {
            byte[] raw = utf8("x");
            List<String> none = List.of();

            assertThatThrownBy(() -> new UnreadablePart(2, 1, FormatDefect.MISSING_COLUMN, none, raw))
                    .as("原文を持つファイルの誤り")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UnreadablePart(2, 1, FormatDefect.UNCLOSED_QUOTE, none, null))
                    .as("原文を持たない閉じ忘れ")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UnreadablePart(2, 1, FormatDefect.BARE_QUOTE, none, null))
                    .as("原文を持たない飲み込み")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 行1_ファイルの誤りにもファイルの終わりまでの範囲にもならない形式の誤りは取り込めなかった部分にできない() {
            byte[] raw = utf8("x");
            List<String> none = List.of();

            for (FormatDefect defect : List.of(
                    FormatDefect.COLUMN_COUNT_MISMATCH,
                    FormatDefect.EMPTY_VALUE,
                    FormatDefect.INVALID_TIME,
                    FormatDefect.TOO_LONG)) {
                assertThatThrownBy(() -> new UnreadablePart(2, 1, defect, none, raw))
                        .as("原文付きの %s", defect)
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> new UnreadablePart(2, 1, defect, none, null))
                        .as("原文のない %s", defect)
                        .isInstanceOf(IllegalArgumentException.class);
            }
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

    /**
     * 値の誤り（Bolt 39b2b の行 17・18・20。UC-15 2a、ED-INV-02）。値の誤りの行は組み立てが行の誤りにするので、取込のサービスは 39b2 の
     * とおり原文を保存してからキーのない隔離にする。取込のサービスは受信記録の列に取り込む行の値を書かない（値は採用値の材料にだけ使う。Bolt 39b3）。
     */
    @Nested
    class 値の誤り {

        private static final String WRONG_TRACKING_NUMBER = "CTABCDEFGH234O";

        private static byte[] utf8(String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }

        private static RejectedRow valueError(int lineNumber, String externalId, FormatDefect defect, String column) {
            String raw = externalId + ",1," + ACQUIRED + "," + WRONG_TRACKING_NUMBER + MILESTONE_TAIL;
            return new RejectedRow(lineNumber, lineNumber, defect, List.of(column), utf8(raw));
        }

        private IngestFileCommand command(int receivedCount, List<IngestedRow> rows, List<RejectedRow> rejectedRows) {
            return new IngestFileCommand(milestoneSource.id(), utf8("file"), receivedCount, rows, rejectedRows, null);
        }

        @Test
        void 行17_正しい行と値の誤りの行が混ざれば行の結果は物理行の順で値の誤りの行は原文を保存してキーのない隔離にする() {
            RejectedRow wrong = valueError(3, "MS-0002", FormatDefect.INVALID_TRACKING_NUMBER, "tracking_number");
            IngestFileCommand command = command(
                    3,
                    List.of(
                            milestone(2, "MS-0001", "1", "CTABCDEFGH2345"),
                            milestone(4, "MS-0003", "1", "CTABCDEFGH2347")),
                    List.of(wrong));

            Ingested result = ingested(service().ingest(steward, command));

            assertThat(result.rows())
                    .extracting(RowResult::lineNumber, RowResult::classification)
                    .containsExactly(
                            tuple(2, Classification.ADOPTED),
                            tuple(3, Classification.QUARANTINED),
                            tuple(4, Classification.ADOPTED));
            assertThat(result.reconciles()).isTrue();
            List<ReceivedRecord> keyless = receivedRecords.all().stream()
                    .filter(received -> received.externalRecordId() == null)
                    .toList();
            assertThat(keyless).hasSize(1);
            ReceivedRecord quarantined = keyless.getFirst();
            assertThat(quarantined.classificationNote())
                    .isEqualTo("3 行目: 追跡番号の形が違います（tracking_number）。CT と英大文字・数字 12 桁（0・O・1・I・L を除く）で書いてください。");
            assertThat(storage.read(quarantined.payloadObjectKey())).isEqualTo(wrong.payload());
            assertThat(externalRecords.findByKey(keyOf(milestoneSource, "MS-0002", "1")))
                    .isEmpty();
        }

        @Test
        void 行18_同じキーの値の誤りの行が先でも外部原本を引かず作らず後の正しい行を採用する() {
            assertOnlyCorrectRowIsAdopted(3, 2);
        }

        @Test
        void 行18_同じキーの値の誤りの行が後でも外部原本を引かず作らず先の正しい行を採用する() {
            assertOnlyCorrectRowIsAdopted(2, 3);
        }

        private void assertOnlyCorrectRowIsAdopted(int correctLine, int errorLine) {
            IngestedRow correct = milestone(correctLine, "MS-0001", "1", "CTABCDEFGH2345");
            IngestFileCommand command = command(
                    2, List.of(correct), List.of(valueError(errorLine, "MS-0001", FormatDefect.INVALID_KIND, "kind")));

            Ingested result = ingested(service().ingest(steward, command));

            assertThat(lookups)
                    .as("値の誤りの行では冪等性キーの外部原本を引かない（変異 V8）")
                    .containsExactly(keyOf(milestoneSource, "MS-0001", "1"));
            assertThat(externalRecords.all()).hasSize(1);
            assertThat(storage.read(externalRecords.all().getFirst().payloadObjectKey()))
                    .isEqualTo(correct.payload());
            assertThat(result.rows())
                    .filteredOn(row -> row.lineNumber() == correctLine)
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED);
        }

        @Test
        void 行20_受信記録と外部原本は取り込む行の値を持たずpayloadは行の原文と同じバイト列() {
            IngestedRow row = milestone(2, "MS-0001", "1", "CTABCDEFGH2345");

            ingested(service().ingest(steward, command(1, List.of(row), List.of())));

            ReceivedRecord received = receivedRecords.all().getFirst();
            assertThat(storage.read(received.payloadObjectKey())).isEqualTo(row.payload());
            for (Class<?> type : List.of(ReceivedRecord.class, ExternalRecord.class)) {
                assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType))
                        .as("%s の列", type.getSimpleName())
                        .noneMatch(RecordValues.class::isAssignableFrom);
            }
        }
    }

    /**
     * 原本の大きさの上限（Bolt 39b4 の行 5・6）。上限は物理データ行 10,000 件と 41,943,040 バイト（40 MiB）で、承認の状態の確かめの後に
     * 判定する。大きさは値だけで確かめ、1 万行の原本を組み立てない（行 6）。上限の値は定数を参照せず数で書く（定数を変えたら落ちる）。
     */
    @Nested
    class 原本の大きさの上限 {

        private static IngestFileCommand sized(DataSource source, int byteLength, int receivedCount) {
            return new IngestFileCommand(source.id(), new byte[byteLength], receivedCount, List.of(), List.of(), null);
        }

        @Test
        void 行5_物理データ行が10001件のファイルは拒否し原本を保存しない() {
            IngestionOutcome outcome = service().ingest(steward, sized(milestoneSource, 100, 10_001));

            assertThat(outcome).isInstanceOf(IngestionOutcome.FileTooLarge.class);
            assertNothingWritten();
            assertThat(published).isEmpty();
        }

        @Test
        void 行5_41943041バイトのファイルは拒否し原本を保存しない() {
            IngestionOutcome outcome = service().ingest(steward, sized(milestoneSource, 41_943_041, 1));

            assertThat(outcome).isInstanceOf(IngestionOutcome.FileTooLarge.class);
            assertNothingWritten();
            assertThat(published).isEmpty();
        }

        @Test
        void 行5_承認されていない情報源の大きすぎるファイルは承認の状態で拒否する() {
            DataSource candidate = source("運送会社C-" + UUID.randomUUID(), "MILESTONE", ApprovalStatus.CANDIDATE);

            IngestionOutcome outcome = service().ingest(steward, sized(candidate, 41_943_041, 10_001));

            assertThat(outcome).isInstanceOf(IngestionOutcome.DataSourceNotApproved.class);
            assertNothingWritten();
        }

        @Test
        void 行5_拒否する経路ではファイルの原本を複製しない() {
            DataSource candidate = source("運送会社C-" + UUID.randomUUID(), "MILESTONE", ApprovalStatus.CANDIDATE);
            IngestFileCommand notApproved = spy(sized(candidate, 100, 1));
            IngestFileCommand tooLarge = spy(sized(milestoneSource, 41_943_041, 1));

            service().ingest(steward, notApproved);
            service().ingest(steward, tooLarge);

            verify(notApproved, never()).filePayload();
            verify(tooLarge, never()).filePayload();
        }

        @Test
        void 行6_物理データ行が10000件ちょうどのファイルは受けて取り込む() {
            Ingested result = ingested(service().ingest(steward, sized(milestoneSource, 100, 10_000)));

            assertThat(result.receivedCount()).isEqualTo(10_000);
            assertThat(calls).containsExactly("store:ingestions");
        }

        @Test
        void 行6_41943040バイトちょうどのファイルは受けて取り込む() {
            ingested(service().ingest(steward, sized(milestoneSource, 41_943_040, 1)));

            Ingestion ingestion = ingestions.all().getFirst();
            assertThat(storage.read(ingestion.fileObjectKey())).hasSize(41_943_040);
        }
    }

    /**
     * Bolt 39c3 の行 1〜5・11・12（受信記録の行の範囲と、ファイルの誤りの「どうすればよいか」。開始準備の決定 (5)・(8)）。39b2・39b3 の同じ番号の
     * 行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39c3_隔離の理由 {

        private static byte[] utf8(String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }

        private IngestFileCommand command(
                int receivedCount, List<IngestedRow> rows, List<RejectedRow> rejectedRows, UnreadablePart unreadable) {
            return new IngestFileCommand(
                    milestoneSource.id(), utf8("file"), receivedCount, rows, rejectedRows, unreadable);
        }

        private ReceivedRecord onlyReceivedRecord() {
            assertThat(receivedRecords.all()).hasSize(1);
            return receivedRecords.all().getFirst();
        }

        private ReceivedRecord receivedRecordOf(UUID id) {
            return receivedRecords.all().stream()
                    .filter(received -> received.id().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        private static IngestedRow milestoneOver(int lineNumber, int lastLineNumber, String externalId, String tn) {
            String line = externalId + ",1," + ACQUIRED + "," + tn + MILESTONE_TAIL;
            return new IngestedRow(
                    lineNumber, lastLineNumber, externalId, "1", ACQUIRED, milestoneValues(tn), utf8(line));
        }

        @Test
        void 行1_形式の正しい行の受信記録の行の範囲は採用と重複と内容の違う隔離のどれも行の結果の範囲と同じ() {
            // 3 行目から 4 行目は引用符の中の改行で 2 物理行にまたがる行。5 行目は 2 行目と同じ内容（重複）、6 行目は 3 行目と内容が違う（隔離）
            IngestFileCommand command = command(
                    5,
                    List.of(
                            milestone(2, "MS-0001", "1", "CTABCDEFGH2345"),
                            milestoneOver(3, 4, "MS-0002", "CTABCDEFGH2346"),
                            milestone(5, "MS-0001", "1", "CTABCDEFGH2345"),
                            milestone(6, "MS-0002", "1", "CTABCDEFGH2347")),
                    List.of(),
                    null);

            Ingested result = ingested(service().ingest(steward, command));

            assertThat(result.rows())
                    .extracting(
                            RowResult::classification,
                            row -> receivedRecordOf(row.receivedRecordId()).lineRange())
                    .containsExactly(
                            tuple(Classification.ADOPTED, new PhysicalLineRange(2, 2)),
                            tuple(Classification.ADOPTED, new PhysicalLineRange(3, 4)),
                            tuple(Classification.DUPLICATE, new PhysicalLineRange(5, 5)),
                            tuple(Classification.QUARANTINED, new PhysicalLineRange(6, 6)));
        }

        @Test
        void 行2_行の誤りの受信記録の行の範囲は行の誤りの範囲と同じ() {
            IngestFileCommand command = command(
                    3,
                    List.of(),
                    List.of(
                            new RejectedRow(
                                    2,
                                    2,
                                    FormatDefect.INVALID_TRACKING_NUMBER,
                                    List.of("tracking_number"),
                                    utf8("MS-0001")),
                            new RejectedRow(
                                    3,
                                    4,
                                    FormatDefect.TEXT_AFTER_CLOSING_QUOTE,
                                    List.of(),
                                    utf8("MS-0002,\"1\"x,\"a\nb\""))),
                    null);

            Ingested result = ingested(service().ingest(steward, command));

            assertThat(result.rows())
                    .extracting(row -> receivedRecordOf(row.receivedRecordId()).lineRange())
                    .containsExactly(new PhysicalLineRange(2, 2), new PhysicalLineRange(3, 4));
        }

        @ParameterizedTest(name = "{0} 行目から {1} 行")
        @CsvSource({"12, 29, 40", "12, 1, 12"})
        void 行3_ファイルの終わりまで取り込めなかった範囲の受信記録の行の範囲は始まりから始まりと数から1を引いた行まで(int fromLine, int lineCount, int lastLine) {
            IngestFileCommand command = command(
                    fromLine - 2 + lineCount,
                    List.of(),
                    List.of(),
                    new UnreadablePart(fromLine, lineCount, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8("\"")));

            ingested(service().ingest(steward, command));

            assertThat(onlyReceivedRecord().lineRange()).isEqualTo(new PhysicalLineRange(fromLine, lastLine));
        }

        static Stream<UnreadablePart> ファイルの誤り() {
            return Stream.of(
                    new UnreadablePart(2, 2, FormatDefect.UNREADABLE_HEADER, List.of(), null),
                    new UnreadablePart(null, 0, FormatDefect.MISSING_COLUMN, List.of("external_id"), null),
                    new UnreadablePart(2, 2, FormatDefect.UNDECODABLE, List.of(), null));
        }

        @ParameterizedTest
        @MethodSource("ファイルの誤り")
        void 行4_ファイルの誤りの受信記録はデータ行の有無と文字コードの誤りによらず行の範囲を持たない(UnreadablePart part) {
            IngestFileCommand command = command(part.lineCount(), List.of(), List.of(), part);

            ingested(service().ingest(steward, command));

            assertThat(onlyReceivedRecord().lineRange()).isNull();
        }

        @Test
        void 行5_隔離のすべての種類でDE14の物理行の範囲と受信記録の行の範囲が一致する() {
            IngestFileCommand rows = command(
                    5,
                    List.of(
                            milestone(2, "MS-0001", "1", "CTABCDEFGH2345"),
                            milestone(3, "MS-0001", "1", "CTABCDEFGH2346")),
                    List.of(new RejectedRow(
                            4, 4, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 7 列", "この行 8 列"), utf8("x"))),
                    new UnreadablePart(5, 2, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8("\"")));
            ingested(service().ingest(steward, rows));
            IngestFileCommand fileError = command(
                    2, List.of(), List.of(), new UnreadablePart(2, 2, FormatDefect.UNDECODABLE, List.of(), null));
            ingested(service().ingest(steward, fileError));

            List<ExternalRecordQuarantined> quarantined = published.stream()
                    .filter(ExternalRecordQuarantined.class::isInstance)
                    .map(ExternalRecordQuarantined.class::cast)
                    .toList();
            assertThat(quarantined)
                    .extracting(ExternalRecordQuarantined::reason)
                    .containsExactly(
                            ExternalRecordQuarantined.CONTENT_MISMATCH,
                            FormatDefect.COLUMN_COUNT_MISMATCH.name(),
                            FormatDefect.UNCLOSED_QUOTE.name(),
                            FormatDefect.UNDECODABLE.name());
            for (ExternalRecordQuarantined event : quarantined) {
                PhysicalLineRange range =
                        receivedRecordOf(event.receivedRecordId()).lineRange();
                assertThat(range == null ? null : range.first())
                        .as(event.reason())
                        .isEqualTo(event.firstLine());
                assertThat(range == null ? null : range.last())
                        .as(event.reason())
                        .isEqualTo(event.lastLine());
            }
        }

        @Test
        void 行11_必要な列がないファイルの注記は選んだ情報源の種別に合うファイルかを確かめる次の行動を持つ() {
            IngestFileCommand command = command(
                    2,
                    List.of(),
                    List.of(),
                    new UnreadablePart(2, 2, FormatDefect.MISSING_COLUMN, List.of("kind", "occurred_at"), null));

            ingested(service().ingest(steward, command));

            assertThat(onlyReceivedRecord().classificationNote())
                    .isEqualTo(
                            "ファイル: 必要な列がありません（kind、occurred_at）。" + "選んだ情報源の種別に合ったファイルか確かめてください。合っていれば提供元に確かめてください。");
        }

        @Test
        void 行12_同じ名前の列があるファイルの注記は提供元に確かめる次の行動を持つ() {
            IngestFileCommand command = command(
                    2,
                    List.of(),
                    List.of(),
                    new UnreadablePart(2, 2, FormatDefect.DUPLICATE_COLUMN, List.of("external_id"), null));

            ingested(service().ingest(steward, command));

            assertThat(onlyReceivedRecord().classificationNote())
                    .isEqualTo("ファイル: 同じ名前の列があります（external_id）。提供元に確かめてください。");
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

    /** 採用値の追記を記録する（追記しないことを件数ではなく呼び出しで確かめる。T-236）。 */
    private class RecordingAdoptedValues extends InMemoryAdoptedValueRepository {

        @Override
        public void add(AdoptedValue adoptedValue) {
            adoptedValues.add(adoptedValue);
            calls.add("adopted:" + adoptedValue.targetType());
        }

        @Override
        public Optional<AdoptedValue> findByReceivedRecordId(UUID receivedRecordId) {
            return adoptedValues.findByReceivedRecordId(receivedRecordId);
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

        @Override
        public List<ReceivedRecord> quarantinedOf(UUID ingestionId, int limit) {
            return receivedRecords.quarantinedOf(ingestionId, limit);
        }
    }
}

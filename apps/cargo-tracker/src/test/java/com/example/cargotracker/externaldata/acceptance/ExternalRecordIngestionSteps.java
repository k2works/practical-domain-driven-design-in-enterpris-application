package com.example.cargotracker.externaldata.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.PilotReadiness;
import com.example.cargotracker.externaldata.application.internal.queryservices.PilotReadinessQueryService;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryDataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryIngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.InMemoryReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 外部原本の取込と分類の受入シナリオのステップ（US-14 AC2・AC3・AC4、BR-12、BR-18、ED-INV-01・ED-INV-02。Bolt 39b）。取込のサービスへは
 * コマンドを直接渡す（CSV の解析と組み立ては 39b2）。主要実績の行は payload に追跡番号を持ち、取得時刻は取込時刻より前（T-230）。
 */
public class ExternalRecordIngestionSteps {

    private static final String MILESTONE_HEADER = "external_id,external_version,acquired_at,tracking_number";

    /** 受入シナリオのデータ責任者。 */
    private static final AuthenticatedActor DATA_STEWARD = new AuthenticatedActor(
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000039b01")),
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000039b02")),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");

    private final InMemoryDataSourceRepository dataSources;
    private final InMemoryIngestionRepository ingestions;
    private final InMemoryExternalRecordRepository externalRecords;
    private final InMemoryReceivedRecordRepository receivedRecords;
    private final InMemoryExternalPayloadStorage payloadStorage;
    private final IngestionService ingestionService;
    private final PilotReadinessQueryService pilotReadinessQueryService;
    private final Clock clock;

    private final Map<String, DataSource> sourcesByProvider = new HashMap<>();
    private final List<IngestionOutcome> outcomes = new ArrayList<>();
    private ExternalRecord firstExternalRecord;
    private PilotReadiness readiness;

    @SuppressWarnings("java:S107") // シナリオで確かめるメモリの実装と入力ポートをそのまま受け取る
    public ExternalRecordIngestionSteps(
            InMemoryDataSourceRepository dataSources,
            InMemoryIngestionRepository ingestions,
            InMemoryExternalRecordRepository externalRecords,
            InMemoryReceivedRecordRepository receivedRecords,
            InMemoryExternalPayloadStorage payloadStorage,
            IngestionService ingestionService,
            PilotReadinessQueryService pilotReadinessQueryService,
            Clock clock) {
        this.dataSources = dataSources;
        this.ingestions = ingestions;
        this.externalRecords = externalRecords;
        this.receivedRecords = receivedRecords;
        this.payloadStorage = payloadStorage;
        this.ingestionService = ingestionService;
        this.pilotReadinessQueryService = pilotReadinessQueryService;
        this.clock = clock;
    }

    @前提("提供元 {string} の原本種別 {string} の情報源が {string} である")
    public void 情報源がある(String provider, String recordType, String status) {
        DataSource source = new DataSource(UUID.randomUUID(), provider, recordType(recordType), approvalStatus(status));
        dataSources.add(source);
        sourcesByProvider.put(provider, source);
    }

    @もし("パイロットの開始を判定する")
    public void パイロットの開始を判定する() {
        readiness = pilotReadinessQueryService.readiness();
    }

    @ならば("開始は拒否され {string} と示される")
    public void 開始は拒否される(String reason) {
        assertThat(readiness.canStart()).isFalse();
        assertThat(readiness.reason()).contains(reason);
    }

    @ならば("開始でき、承認済みの情報源は {int} 件と示される")
    public void 開始できる(int approvedCount) {
        assertThat(readiness.canStart()).isTrue();
        assertThat(readiness.reason()).isEmpty();
        assertThat(readiness.approvedCount()).isEqualTo(approvedCount);
    }

    @もし("データ責任者が提供元 {string} の情報源から外部 ID {string}・外部版 {string}・追跡番号 {string}・取得時刻 {string} の主要実績の行を取り込む")
    public void 主要実績の行を取り込む(
            String provider, String externalId, String version, String trackingNumber, String acquired) {
        Instant acquiredAt = Instant.parse(acquired);
        // 取得時刻は取込時刻より前（T-230）
        assertThat(acquiredAt).isBefore(clock.instant());
        String line = externalId + "," + version + "," + acquiredAt + "," + trackingNumber;
        IngestedRow row = new IngestedRow(2, externalId, version, acquiredAt, line.getBytes(StandardCharsets.UTF_8));
        byte[] file = (MILESTONE_HEADER + "\n" + line).getBytes(StandardCharsets.UTF_8);
        outcomes.add(ingestionService.ingest(
                DATA_STEWARD,
                new IngestFileCommand(sourcesByProvider.get(provider).id(), file, 1, List.of(row))));
        if (firstExternalRecord == null && !externalRecords.all().isEmpty()) {
            firstExternalRecord = externalRecords.all().getFirst();
        }
    }

    @ならば("取込は情報源が承認されていないとして拒否される")
    public void 承認されていないとして拒否される() {
        assertThat(lastOutcome()).isInstanceOf(IngestionOutcome.DataSourceNotApproved.class);
    }

    @ならば("原本・取込・外部原本・受信記録はどれも保存されない")
    public void 何も保存されない() {
        assertThat(payloadStorage.count()).isZero();
        assertThat(ingestions.all()).isEmpty();
        assertThat(externalRecords.all()).isEmpty();
        assertThat(receivedRecords.all()).isEmpty();
    }

    @ならば("行は {string} に分類され、注記は {string} である")
    public void 行の分類と注記(String classification, String note) {
        RowResult row = lastRow();
        assertThat(row.classification()).isEqualTo(classification(classification));
        ReceivedRecord received = receivedRecords.all().stream()
                .filter(record -> record.id().equals(row.receivedRecordId()))
                .findFirst()
                .orElseThrow();
        assertThat(received.classificationNote()).isEqualTo(note);
    }

    @ならば("最初の取込の外部原本が返される")
    public void 最初の外部原本が返される() {
        assertThat(lastRow().externalRecordId()).isEqualTo(firstExternalRecord.id());
    }

    @ならば("外部原本は 1 件だけで、最初の原本のハッシュとオブジェクトキーのままである")
    public void 外部原本は最初のまま() {
        assertThat(externalRecords.all()).containsExactly(firstExternalRecord);
    }

    @ならば("受信記録は {int} 件で、分類は順に {string}・{string} である")
    public void 受信記録の分類(int count, String first, String second) {
        assertThat(receivedRecords.all())
                .hasSize(count)
                .extracting(ReceivedRecord::classification)
                .containsExactly(classification(first), classification(second));
    }

    private IngestionOutcome lastOutcome() {
        assertThat(outcomes).isNotEmpty();
        return outcomes.getLast();
    }

    private RowResult lastRow() {
        assertThat(lastOutcome()).isInstanceOf(Ingested.class);
        return ((Ingested) lastOutcome()).rows().getFirst();
    }

    private static String recordType(String name) {
        return switch (name) {
            case "主要実績" -> "MILESTONE";
            case "航海" -> "VOYAGE";
            default -> throw new IllegalArgumentException("未知の原本種別: " + name);
        };
    }

    private static ApprovalStatus approvalStatus(String name) {
        return switch (name) {
            case "候補" -> ApprovalStatus.CANDIDATE;
            case "承認済み" -> ApprovalStatus.APPROVED;
            case "停止中" -> ApprovalStatus.SUSPENDED;
            default -> throw new IllegalArgumentException("未知の選定・承認状態: " + name);
        };
    }

    private static Classification classification(String name) {
        return switch (name) {
            case "採用" -> Classification.ADOPTED;
            case "重複" -> Classification.DUPLICATE;
            case "隔離" -> Classification.QUARANTINED;
            default -> throw new IllegalArgumentException("未知の分類: " + name);
        };
    }
}

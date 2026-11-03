package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.Ingestion;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取込のサービス。データ責任者が承認済みの情報源のファイルを取り込み、形式の正しい行ごとに原本を保存してから、冪等性キーで採用・重複・
 * 隔離に分類した受信記録を取込に関連付けて残す（US-14 AC1〜AC4、BR-12、BR-18、ED-INV-01・ED-INV-02。Bolt 39b）。
 *
 * <p>拒否の順は役割 → 情報源の有無 → 承認の状態で、拒否なら原本も保存しない（受け付けていない）。1 つの取込は 1 つのトランザクション
 * （外側）で、原本の保存は独立したトランザクションで残る（ADR-004 の改訂）。原本の保存の失敗と外部原本の一意制約の違反（同時の取込）は
 * 例外のまま上げて取込全体を巻き戻す。時刻は Clock から 1 回取り、取込・外部原本・受信記録に使う（取込を 1 つの時点として再現できる）。
 */
@Service // JIG がユースケースとして読む印（組み立ては ExternalDataConfiguration）
public class IngestionService {

    private static final String DUPLICATE_NOTE = "同じ原本を受け取り済み";
    private static final String QUARANTINED_NOTE = "同じ外部 ID・外部版で内容の違う原本を受け取った（最初の受信: %s）";

    private final DataSourceRepository dataSources;
    private final IngestionRepository ingestions;
    private final ExternalRecordRepository externalRecords;
    private final ReceivedRecordRepository receivedRecords;
    private final ExternalPayloadStorage payloadStorage;
    private final Clock clock;

    public IngestionService(
            DataSourceRepository dataSources,
            IngestionRepository ingestions,
            ExternalRecordRepository externalRecords,
            ReceivedRecordRepository receivedRecords,
            ExternalPayloadStorage payloadStorage,
            Clock clock) {
        this.dataSources = dataSources;
        this.ingestions = ingestions;
        this.externalRecords = externalRecords;
        this.receivedRecords = receivedRecords;
        this.payloadStorage = payloadStorage;
        this.clock = clock;
    }

    /**
     * 1 ファイルを取り込む。
     *
     * @param operator 操作者（データ責任者）
     * @param command 取込のコマンド
     */
    @Transactional
    public IngestionOutcome ingest(AuthenticatedActor operator, IngestFileCommand command) {
        if (!operator.hasRole(Role.DATA_STEWARD)) {
            return new IngestionOutcome.NotPermitted();
        }
        Optional<DataSource> found = dataSources.findById(command.dataSourceId());
        if (found.isEmpty()) {
            return new IngestionOutcome.DataSourceNotFound();
        }
        DataSource source = found.get();
        if (source.approvalStatus() != ApprovalStatus.APPROVED) {
            return new IngestionOutcome.DataSourceNotApproved();
        }
        Instant now = clock.instant();
        Ingestion ingestion = recordIngestion(operator, command, source, now);
        List<RowResult> results = new ArrayList<>();
        for (IngestedRow row : command.rows()) {
            results.add(classify(source, ingestion, row, now));
        }
        return new Ingested(ingestion.id(), ingestion.receivedCount(), countByClassification(ingestion), results);
    }

    private Ingestion recordIngestion(
            AuthenticatedActor operator, IngestFileCommand command, DataSource source, Instant now) {
        byte[] file = command.filePayload();
        PayloadObjectKey fileKey = PayloadObjectKey.newIngestionFile();
        payloadStorage.store(fileKey, file);
        Ingestion ingestion = new Ingestion(
                UUID.randomUUID(),
                source.id(),
                fileKey,
                PayloadHash.of(file),
                operator.userId(),
                now,
                command.receivedCount(),
                null,
                0);
        ingestions.add(ingestion);
        return ingestion;
    }

    private RowResult classify(DataSource source, Ingestion ingestion, IngestedRow row, Instant now) {
        byte[] payload = row.payload();
        PayloadObjectKey payloadKey = PayloadObjectKey.newRecordPayload();
        payloadStorage.store(payloadKey, payload);
        PayloadHash hash = PayloadHash.of(payload);
        IdempotencyKey key =
                new IdempotencyKey(source.provider(), source.recordType(), row.externalId(), row.externalVersion());
        Optional<ExternalRecord> existing = externalRecords.findByKey(key);
        Classification classification = Classification.classify(existing.map(ExternalRecord::payloadHash), hash);
        ExternalRecord externalRecord = existing.orElseGet(() -> {
            ExternalRecord adopted = new ExternalRecord(UUID.randomUUID(), key, hash, payloadKey, now);
            externalRecords.add(adopted);
            return adopted;
        });
        ReceivedRecord received = new ReceivedRecord(
                UUID.randomUUID(),
                externalRecord.id(),
                ingestion.id(),
                hash,
                payloadKey,
                row.acquiredAt(),
                classification,
                noteOf(classification, externalRecord),
                now);
        receivedRecords.add(received);
        return new RowResult(row.lineNumber(), classification, externalRecord.id(), received.id());
    }

    private static String noteOf(Classification classification, ExternalRecord externalRecord) {
        return switch (classification) {
            case ADOPTED -> null;
            case DUPLICATE -> DUPLICATE_NOTE;
            case QUARANTINED -> QUARANTINED_NOTE.formatted(externalRecord.firstReceivedAt());
        };
    }

    private Map<Classification, Integer> countByClassification(Ingestion ingestion) {
        Map<Classification, Integer> counts = new EnumMap<>(Classification.class);
        for (Classification classification : Classification.values()) {
            counts.put(
                    classification, receivedRecords.countByIngestionAndClassification(ingestion.id(), classification));
        }
        return counts;
    }
}

package com.example.cargotracker.externaldata.application.internal.commandservices;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.RejectedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Unreadable;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
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
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取込のサービス。データ責任者が承認済みの情報源のファイルを取り込み、形式の正しい行ごとに原本を保存してから、冪等性キーで採用・重複・
 * 隔離に分類した受信記録を取込に関連付けて残す（US-14 AC1〜AC4、BR-12、BR-18、ED-INV-01・ED-INV-02。Bolt 39b）。形式の誤りの行は
 * 原文を保存してから、ファイルの誤りはファイル全体の原本を指して、外部原本を持たない隔離（キーのない隔離）の受信記録にし、理由と物理行の範囲を
 * 業務の言葉で注記に書く。誤りの行は冪等性キーを信用できないので外部原本を引かず作らない（UC-15 2a。Bolt 39b2）。分類が採用の行は、
 * 受信記録の後に採用値を 1 つ追記する（US-14 AC1。重複・隔離には作らない。Bolt 39b3）。行ごとに受信記録 → 採用値 → イベントの順で、
 * 採用は DE-12（航海）か DE-13（主要実績）、隔離（内容の違う原本とキーのない隔離）は DE-14 を取込のトランザクションの中で出し、重複には出さない
 * （巻き戻ればイベント発行記録も残らない。ADR-003。Bolt 39b3）。
 *
 * <p>拒否の順は役割 → 情報源の有無 → 承認の状態で、拒否なら原本も保存しない（受け付けていない）。1 つの取込は 1 つのトランザクション
 * （外側）で、原本の保存は独立したトランザクションで残る（ADR-004 の改訂）。原本の保存の失敗と外部原本の一意制約の違反（同時の取込）は
 * 例外のまま上げて取込全体を巻き戻す。時刻は Clock から 1 回取り、取込・外部原本・受信記録に使う（取込を 1 つの時点として再現できる）。
 * 処理の順は、ファイル全体の原本 → 取込の行（取り込めなかった行の始まりと数を入れ、更新しない）→ 物理行の順の行 → 取り込めなかった部分。
 */
@Service // JIG がユースケースとして読む印（組み立ては ExternalDataConfiguration）
public class IngestionService {

    /** 受信記録の注記の上限（{@code classification_note VARCHAR(1000)}）。 */
    private static final int NOTE_LIMIT = 1000;

    /** 同じ原本の重複の注記（「&lt;物理行の範囲&gt;: &lt;理由&gt;。」の形。Bolt 39b2 の開発レビュー）。 */
    private static final String DUPLICATE_NOTE = "%s: 同じ原本を受け取り済みです。";
    /** 内容の違う原本の隔離の注記（「&lt;物理行の範囲&gt;: &lt;理由&gt;。」の形。Bolt 39b2 の確認ポイント 6）。 */
    private static final String QUARANTINED_NOTE = "%s: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: %s）。";

    /** 注記の時刻（秒まで、UTC と明記。保存した文は見る人のタイムゾーンで変えられないため。Bolt 39b2 の確認ポイント 6）。 */
    private static final DateTimeFormatter NOTE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final DataSourceRepository dataSources;
    private final IngestionRepository ingestions;
    private final ExternalRecordRepository externalRecords;
    private final ReceivedRecordRepository receivedRecords;
    private final AdoptedValueRepository adoptedValues;
    private final ExternalPayloadStorage payloadStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @SuppressWarnings("java:S107") // リポジトリのポート 5 つと保存と発行と時計を受ける組み立ての入口（ExternalDataConfiguration）
    public IngestionService(
            DataSourceRepository dataSources,
            IngestionRepository ingestions,
            ExternalRecordRepository externalRecords,
            ReceivedRecordRepository receivedRecords,
            AdoptedValueRepository adoptedValues,
            ExternalPayloadStorage payloadStorage,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.dataSources = dataSources;
        this.ingestions = ingestions;
        this.externalRecords = externalRecords;
        this.receivedRecords = receivedRecords;
        this.adoptedValues = adoptedValues;
        this.payloadStorage = payloadStorage;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 1 つの取込の文脈（情報源・取込・操作者・取込の時刻）。行ごとの処理とイベントの値に渡す。 */
    private record Run(DataSource source, Ingestion ingestion, AuthenticatedActor operator, Instant now) {}

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
        Run run = new Run(source, ingestion, operator, now);
        List<RowResult> results = new ArrayList<>();
        List<IngestedRow> rows = command.rows();
        List<RejectedRow> rejectedRows = command.rejectedRows();
        int next = 0;
        int nextRejected = 0;
        while (next < rows.size() || nextRejected < rejectedRows.size()) {
            boolean rowFirst = nextRejected >= rejectedRows.size()
                    || (next < rows.size()
                            && rows.get(next).lineNumber()
                                    < rejectedRows.get(nextRejected).lineNumber());
            results.add(rowFirst ? classify(run, rows.get(next++)) : quarantine(run, rejectedRows.get(nextRejected++)));
        }
        Unreadable unreadable = command.unreadable() == null ? null : quarantine(run, command.unreadable());
        return new Ingested(
                ingestion.id(), ingestion.receivedCount(), countByClassification(ingestion), results, unreadable);
    }

    private Ingestion recordIngestion(
            AuthenticatedActor operator, IngestFileCommand command, DataSource source, Instant now) {
        byte[] file = command.filePayload();
        PayloadObjectKey fileKey = PayloadObjectKey.newIngestionFile();
        payloadStorage.store(fileKey, file);
        UnreadablePart unreadable = command.unreadable();
        Ingestion ingestion = new Ingestion(
                UUID.randomUUID(),
                source.id(),
                fileKey,
                PayloadHash.of(file),
                operator.userId(),
                now,
                command.receivedCount(),
                unreadable == null ? null : unreadable.fromLine(),
                unreadable == null ? 0 : unreadable.lineCount());
        ingestions.add(ingestion);
        return ingestion;
    }

    private RowResult classify(Run run, IngestedRow row) {
        DataSource source = run.source();
        Ingestion ingestion = run.ingestion();
        Instant now = run.now();
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
                noteOf(classification, externalRecord, row),
                now);
        receivedRecords.add(received);
        Optional<Object> event =
                switch (classification) {
                    case ADOPTED -> Optional.of(adopt(run, received, row));
                    case QUARANTINED ->
                        Optional.of(quarantinedEventOf(
                                run,
                                received,
                                key,
                                ExternalRecordQuarantined.CONTENT_MISMATCH,
                                row.lineNumber(),
                                row.lastLineNumber()));
                    // 重複は採用値に影響しないので、採用値もイベントもない
                    case DUPLICATE -> Optional.empty();
                };
        event.ifPresent(eventPublisher::publishEvent);
        return new RowResult(
                row.lineNumber(), row.lastLineNumber(), classification, externalRecord.id(), received.id());
    }

    /** 採用の受信記録の採用値を追記し、そのイベント（DE-12・DE-13）を返す（組み立ては {@link AdoptionFactory}。Bolt 39b3）。 */
    private Object adopt(Run run, ReceivedRecord received, IngestedRow row) {
        AdoptionFactory.Adoption adoption = AdoptionFactory.adopt(
                received, row, run.ingestion().id(), run.source().id(), run.operator(), run.now());
        adoptedValues.add(adoption.adoptedValue());
        return adoption.event();
    }

    /**
     * 外部原本を隔離した（DE-14。Bolt 39b3）。キーのない隔離は冪等性キーが {@code null} で、外部 ID・外部版も {@code null} になる。
     * ファイルの誤りは物理行の範囲も {@code null}。
     */
    private static ExternalRecordQuarantined quarantinedEventOf(
            Run run, ReceivedRecord received, IdempotencyKey key, String reason, Integer firstLine, Integer lastLine) {
        return new ExternalRecordQuarantined(
                received.id(),
                run.ingestion().id(),
                run.source().id(),
                run.source().provider(),
                run.source().recordType(),
                key == null ? null : key.externalId(),
                key == null ? null : key.externalVersion(),
                reason,
                firstLine,
                lastLine,
                run.operator().userId(),
                run.operator().companyId(),
                new UtcInstant(run.now()));
    }

    /** 行の誤り。行の原文を保存してから、キーのない隔離の受信記録を書き、外部原本を隔離した（DE-14）を出す。 */
    private RowResult quarantine(Run run, RejectedRow row) {
        byte[] payload = row.payload();
        PayloadObjectKey payloadKey = PayloadObjectKey.newRecordPayload();
        payloadStorage.store(payloadKey, payload);
        ReceivedRecord received = addKeylessQuarantine(
                run, PayloadHash.of(payload), payloadKey, row.defect().rowNote(row.range(), row.details()));
        eventPublisher.publishEvent(
                quarantinedEventOf(run, received, null, row.defect().name(), row.lineNumber(), row.lastLineNumber()));
        return new RowResult(row.lineNumber(), row.lastLineNumber(), Classification.QUARANTINED, null, received.id());
    }

    /**
     * 取り込めなかった部分。ファイルの誤りはファイル全体の原本を指し、ファイルの終わりまで飲み込んだ範囲はその原文を保存してから、キーのない
     * 隔離の受信記録を 1 つ書き、外部原本を隔離した（DE-14）を出す（物理行の範囲は、ファイルの誤りは {@code null}、ファイルの終わりまでの
     * 範囲は始まりからファイルの終わりの物理行まで）。
     */
    private Unreadable quarantine(Run run, UnreadablePart part) {
        Ingestion ingestion = run.ingestion();
        ReceivedRecord received;
        Integer firstLine = null;
        Integer lastLine = null;
        if (part.fileDefect()) {
            received = addKeylessQuarantine(
                    run,
                    ingestion.fileHash(),
                    ingestion.fileObjectKey(),
                    part.defect().fileNote(part.details()));
        } else {
            byte[] payload = part.payload();
            PayloadObjectKey payloadKey = PayloadObjectKey.newRecordPayload();
            payloadStorage.store(payloadKey, payload);
            received = addKeylessQuarantine(
                    run,
                    PayloadHash.of(payload),
                    payloadKey,
                    part.defect().untilEndOfFileNote(part.fromLine(), part.lineCount(), part.details()));
            firstLine = part.fromLine();
            lastLine = part.fromLine() + part.lineCount() - 1;
        }
        eventPublisher.publishEvent(
                quarantinedEventOf(run, received, null, part.defect().name(), firstLine, lastLine));
        return new Unreadable(part.fromLine(), part.lineCount(), received.id());
    }

    private ReceivedRecord addKeylessQuarantine(Run run, PayloadHash hash, PayloadObjectKey payloadKey, String note) {
        ReceivedRecord received = new ReceivedRecord(
                UUID.randomUUID(),
                null,
                run.ingestion().id(),
                hash,
                payloadKey,
                null,
                Classification.QUARANTINED,
                withinNoteLimit(note),
                run.now());
        receivedRecords.add(received);
        return received;
    }

    private static String noteOf(Classification classification, ExternalRecord externalRecord, IngestedRow row) {
        return switch (classification) {
            case ADOPTED -> null;
            case DUPLICATE -> DUPLICATE_NOTE.formatted(row.range().wording());
            case QUARANTINED ->
                QUARANTINED_NOTE.formatted(row.range().wording(), NOTE_TIME.format(externalRecord.firstReceivedAt()));
        };
    }

    /** 注記が上限の文字数（コードポイント）を超えたら、末尾を「…」で切る。 */
    private static String withinNoteLimit(String note) {
        if (note.codePointCount(0, note.length()) <= NOTE_LIMIT) {
            return note;
        }
        return note.substring(0, note.offsetByCodePoints(0, NOTE_LIMIT - 1)) + "…";
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

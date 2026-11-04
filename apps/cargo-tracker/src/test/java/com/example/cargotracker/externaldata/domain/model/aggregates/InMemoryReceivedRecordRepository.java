package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedQuarantine;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * メモリ上の受信記録のリポジトリ（テスト用。Bolt 39b）。追記と数えることと、取込の隔離を読むこと（Bolt 39c3）と、判断を待つ隔離を読み数えること
 * （Bolt 39d。メモリの取込と外部原本のリポジトリを結ぶ）だけを持つ。
 */
public class InMemoryReceivedRecordRepository implements ReceivedRecordRepository {

    private final List<ReceivedRecord> records = new CopyOnWriteArrayList<>();
    private final InMemoryIngestionRepository ingestions;
    private final InMemoryExternalRecordRepository externalRecords;
    private final InMemoryReceivedRecordDecisionRepository decisions;

    /** 判断を書かない呼び手（取込のテストと受入シナリオ）の組み立て。判断の表は空のまま。 */
    public InMemoryReceivedRecordRepository(
            InMemoryIngestionRepository ingestions, InMemoryExternalRecordRepository externalRecords) {
        this(ingestions, externalRecords, new InMemoryReceivedRecordDecisionRepository());
    }

    /** 判断のある隔離を判断を待つ隔離から除くため、メモリの判断のリポジトリを受け取る（Bolt 39d2）。 */
    public InMemoryReceivedRecordRepository(
            InMemoryIngestionRepository ingestions,
            InMemoryExternalRecordRepository externalRecords,
            InMemoryReceivedRecordDecisionRepository decisions) {
        this.ingestions = Objects.requireNonNull(ingestions, "ingestions");
        this.externalRecords = Objects.requireNonNull(externalRecords, "externalRecords");
        this.decisions = Objects.requireNonNull(decisions, "decisions");
    }

    @Override
    public void add(ReceivedRecord receivedRecord) {
        records.add(Objects.requireNonNull(receivedRecord, "receivedRecord"));
    }

    @Override
    public int countByIngestionAndClassification(UUID ingestionId, Classification classification) {
        return (int) records.stream()
                .filter(receivedRecord -> receivedRecord.ingestionId().equals(ingestionId))
                .filter(receivedRecord -> receivedRecord.classification() == classification)
                .count();
    }

    /**
     * 範囲のないものを先に行の範囲の始まりの順、同じなら受信記録 ID の順（SQL の {@code ORDER BY first_line_number NULLS FIRST, id} と同じ
     * 順。ID は DB と同じく文字列の表現の順で比べる）。
     */
    @Override
    public List<ReceivedRecord> quarantinedOf(UUID ingestionId, int limit) {
        return records.stream()
                .filter(receivedRecord -> receivedRecord.ingestionId().equals(ingestionId))
                .filter(receivedRecord -> receivedRecord.classification() == Classification.QUARANTINED)
                .sorted(Comparator.comparing(
                                ReceivedRecord::lineRange,
                                Comparator.nullsFirst(Comparator.comparingInt(PhysicalLineRange::first)))
                        .thenComparing(receivedRecord -> receivedRecord.id().toString()))
                .limit(limit)
                .toList();
    }

    /**
     * 判断を待つ隔離（Bolt 39d）。メモリの取込のリポジトリと結び（取込のない受信記録は落ちる）、外部原本の外部 ID・外部版を引く。SQL の
     * {@code ORDER BY i.ingested_at DESC, r.ingestion_id, r.first_line_number NULLS FIRST, r.id} と同じ順（UUID は DB と同じく文字列の表現の
     * 順で比べる）。メモリの判断のリポジトリに判断のある隔離は除く（Bolt 39d2）。
     */
    @Override
    public List<UndecidedQuarantine> undecidedQuarantines(UUID ingestionIdOrNull, int offset, int limit) {
        return undecided(ingestionIdOrNull)
                .sorted(Comparator.comparing(UndecidedQuarantine::ingestedAt)
                        .reversed()
                        .thenComparing(quarantine -> quarantine.ingestionId().toString())
                        .thenComparing(
                                UndecidedQuarantine::lineRange,
                                Comparator.nullsFirst(Comparator.comparingInt(PhysicalLineRange::first)))
                        .thenComparing(
                                quarantine -> quarantine.receivedRecordId().toString()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public int countUndecidedQuarantines(UUID ingestionIdOrNull) {
        return (int) undecided(ingestionIdOrNull).count();
    }

    /** メモリの受信記録はどれも取込 ID を持つ（取込のない受信記録を持てない）。 */
    @Override
    public Optional<ReceivedRecord> findById(UUID id) {
        return records.stream()
                .filter(receivedRecord -> receivedRecord.id().equals(id))
                .findFirst();
    }

    @Override
    public Optional<UndecidedQuarantine> undecidedQuarantine(UUID receivedRecordId) {
        return undecided(null)
                .filter(quarantine -> quarantine.receivedRecordId().equals(receivedRecordId))
                .findFirst();
    }

    private Stream<UndecidedQuarantine> undecided(UUID ingestionIdOrNull) {
        return records.stream()
                .filter(receivedRecord -> receivedRecord.classification() == Classification.QUARANTINED)
                .filter(receivedRecord -> !decisions.existsFor(receivedRecord.id()))
                .filter(receivedRecord -> ingestionIdOrNull == null
                        || receivedRecord.ingestionId().equals(ingestionIdOrNull))
                .flatMap(receivedRecord -> ingestions.findById(receivedRecord.ingestionId()).stream()
                        .map(ingestion -> undecided(receivedRecord, ingestion)));
    }

    private UndecidedQuarantine undecided(ReceivedRecord receivedRecord, Ingestion ingestion) {
        Optional<ExternalRecord> externalRecord = receivedRecord.externalRecordId() == null
                ? Optional.empty()
                : externalRecords.all().stream()
                        .filter(candidate -> candidate.id().equals(receivedRecord.externalRecordId()))
                        .findFirst();
        return new UndecidedQuarantine(
                receivedRecord.id(),
                ingestion.id(),
                ingestion.ingestedAt(),
                ingestion.dataSourceId(),
                receivedRecord.lineRange(),
                externalRecord.map(found -> found.key().externalId()).orElse(null),
                externalRecord.map(found -> found.key().externalVersion()).orElse(null),
                receivedRecord.classificationNote());
    }

    /** シナリオの開始時に消す（受入シナリオ。Bolt 39b）。 */
    public void clear() {
        records.clear();
    }

    /** 追記した受信記録（ポートの外。テストの確かめ）。追記の順。 */
    public List<ReceivedRecord> all() {
        return List.copyOf(records);
    }
}

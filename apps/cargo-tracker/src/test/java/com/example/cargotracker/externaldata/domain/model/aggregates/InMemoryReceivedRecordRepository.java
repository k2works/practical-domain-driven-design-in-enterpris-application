package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** メモリ上の受信記録のリポジトリ（テスト用。Bolt 39b）。追記と数えることと、取込の隔離を読むこと（Bolt 39c3）だけを持つ。 */
public class InMemoryReceivedRecordRepository implements ReceivedRecordRepository {

    private final List<ReceivedRecord> records = new CopyOnWriteArrayList<>();

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

    /** シナリオの開始時に消す（受入シナリオ。Bolt 39b）。 */
    public void clear() {
        records.clear();
    }

    /** 追記した受信記録（ポートの外。テストの確かめ）。追記の順。 */
    public List<ReceivedRecord> all() {
        return List.copyOf(records);
    }
}

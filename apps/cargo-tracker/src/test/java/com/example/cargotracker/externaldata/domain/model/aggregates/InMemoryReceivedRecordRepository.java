package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** メモリ上の受信記録のリポジトリ（テスト用。Bolt 39b）。追記と数えることだけを持つ。 */
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

    /** シナリオの開始時に消す（受入シナリオ。Bolt 39b）。 */
    public void clear() {
        records.clear();
    }

    /** 追記した受信記録（ポートの外。テストの確かめ）。追記の順。 */
    public List<ReceivedRecord> all() {
        return List.copyOf(records);
    }
}

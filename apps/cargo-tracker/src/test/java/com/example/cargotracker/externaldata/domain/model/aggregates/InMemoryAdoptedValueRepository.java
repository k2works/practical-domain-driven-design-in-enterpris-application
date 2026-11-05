package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * メモリ上の採用値のリポジトリ（テスト用。Bolt 39b3）。追記と受信記録 ID での照会と、この外部版の採用値の照会（Bolt 39d3。メモリの受信記録の
 * リポジトリを結ぶ）だけを持つ。受信記録ごとに 1 つの一意制約を写し、同じ受信記録の 2 つ目の追加は MyBatis の実装と同じく
 * {@link DuplicateAdoptedValueException} にする（行 15）。
 */
public class InMemoryAdoptedValueRepository implements AdoptedValueRepository {

    private final List<AdoptedValue> values = new CopyOnWriteArrayList<>();
    private final InMemoryReceivedRecordRepository receivedRecords;

    public InMemoryAdoptedValueRepository(InMemoryReceivedRecordRepository receivedRecords) {
        this.receivedRecords = Objects.requireNonNull(receivedRecords, "receivedRecords");
    }

    @Override
    public synchronized void add(AdoptedValue adoptedValue) {
        Objects.requireNonNull(adoptedValue, "adoptedValue");
        if (findByReceivedRecordId(adoptedValue.receivedRecordId()).isPresent()) {
            throw DuplicateAdoptedValueException.of(adoptedValue.receivedRecordId(), null);
        }
        values.add(adoptedValue);
    }

    @Override
    public Optional<AdoptedValue> findByReceivedRecordId(UUID receivedRecordId) {
        return values.stream()
                .filter(value -> value.receivedRecordId().equals(receivedRecordId))
                .findFirst();
    }

    /**
     * この外部版の採用値（Bolt 39d3）。メモリの受信記録のリポジトリと結んで外部原本 ID で絞り、SQL の
     * {@code ORDER BY a.published_at DESC, a.id DESC} の最初と同じもの（ID は DB と同じく文字列の表現の順で比べる）。
     */
    @Override
    public Optional<AdoptedValue> currentOf(UUID externalRecordId) {
        return values.stream()
                .filter(value -> receivedRecords
                        .findById(value.receivedRecordId())
                        .map(ReceivedRecord::externalRecordId)
                        .filter(externalRecordId::equals)
                        .isPresent())
                .max(Comparator.comparing(AdoptedValue::publishedAt)
                        .thenComparing(value -> value.id().toString()));
    }

    /** シナリオの開始時に消す（受入シナリオ）。 */
    public void clear() {
        values.clear();
    }

    /** 追記した採用値（ポートの外。テストの確かめ）。追記の順。 */
    public List<AdoptedValue> all() {
        return List.copyOf(values);
    }
}

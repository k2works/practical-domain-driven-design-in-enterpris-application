package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * メモリ上の採用値のリポジトリ（テスト用。Bolt 39b3）。追記と受信記録 ID での照会だけを持つ。受信記録ごとに 1 つの一意制約を写し、同じ
 * 受信記録の 2 つ目の追加は MyBatis の実装と同じく {@link DuplicateAdoptedValueException} にする（行 15）。
 */
public class InMemoryAdoptedValueRepository implements AdoptedValueRepository {

    private final List<AdoptedValue> values = new CopyOnWriteArrayList<>();

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

    /** シナリオの開始時に消す（受入シナリオ）。 */
    public void clear() {
        values.clear();
    }

    /** 追記した採用値（ポートの外。テストの確かめ）。追記の順。 */
    public List<AdoptedValue> all() {
        return List.copyOf(values);
    }
}

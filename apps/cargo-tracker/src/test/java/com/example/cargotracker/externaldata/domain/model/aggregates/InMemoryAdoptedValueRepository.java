package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** メモリ上の採用値のリポジトリ（テスト用。Bolt 39b3）。追記と受信記録 ID での照会だけを持つ。 */
public class InMemoryAdoptedValueRepository implements AdoptedValueRepository {

    private final List<AdoptedValue> values = new CopyOnWriteArrayList<>();

    @Override
    public void add(AdoptedValue adoptedValue) {
        values.add(Objects.requireNonNull(adoptedValue, "adoptedValue"));
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

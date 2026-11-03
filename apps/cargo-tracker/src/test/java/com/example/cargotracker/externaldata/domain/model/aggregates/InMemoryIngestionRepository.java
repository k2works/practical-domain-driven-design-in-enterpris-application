package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** メモリ上の取込のリポジトリ（テスト用。Bolt 39b）。追記だけを持つ。 */
public class InMemoryIngestionRepository implements IngestionRepository {

    private final List<Ingestion> ingestions = new CopyOnWriteArrayList<>();

    @Override
    public void add(Ingestion ingestion) {
        ingestions.add(Objects.requireNonNull(ingestion, "ingestion"));
    }

    /** シナリオの開始時に消す（受入シナリオ。Bolt 39b）。 */
    public void clear() {
        ingestions.clear();
    }

    /** 追記した取込（ポートの外。テストの確かめ）。追記の順。 */
    public List<Ingestion> all() {
        return List.copyOf(ingestions);
    }
}

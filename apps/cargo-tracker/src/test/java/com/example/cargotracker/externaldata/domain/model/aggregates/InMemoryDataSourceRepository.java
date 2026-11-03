package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** メモリ上の情報源のリポジトリ（テスト用。Bolt 39b）。情報源はテストが {@link #add(DataSource)} で入れる。 */
public class InMemoryDataSourceRepository implements DataSourceRepository {

    private final Map<UUID, DataSource> sources = new ConcurrentHashMap<>();

    /** 情報源を入れる（ポートの外。テストの準備）。 */
    public void add(DataSource source) {
        Objects.requireNonNull(source, "source");
        sources.put(source.id(), source);
    }

    /** シナリオの開始時に消す（受入シナリオ。Bolt 39b）。 */
    public void clear() {
        sources.clear();
    }

    /** 入れた情報源のすべて（ポートの外。受入シナリオが提供元の名前で引く。Bolt 39b2）。 */
    public List<DataSource> all() {
        return List.copyOf(sources.values());
    }

    @Override
    public Optional<DataSource> findById(UUID id) {
        return Optional.ofNullable(sources.get(id));
    }

    @Override
    public int countApproved() {
        return (int) sources.values().stream()
                .filter(source -> source.approvalStatus() == ApprovalStatus.APPROVED)
                .count();
    }
}

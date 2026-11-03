package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
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

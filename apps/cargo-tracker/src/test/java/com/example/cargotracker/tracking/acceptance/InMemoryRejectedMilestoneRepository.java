package com.example.cargotracker.tracking.acceptance;

import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestoneRepository;
import java.util.ArrayList;
import java.util.List;

/** メモリ上の拒否の記録のリポジトリ（業務ルール層の受入シナリオと単体テスト。Bolt 39g）。同じ出典（種類と参照）は 1 件だけ（一意制約と同じ）。 */
public class InMemoryRejectedMilestoneRepository implements RejectedMilestoneRepository {

    private final List<RejectedMilestone> saved = new ArrayList<>();

    @Override
    public synchronized boolean existsBySource(Source source) {
        return saved.stream()
                .anyMatch(rejected -> rejected.source().kind() == source.kind()
                        && rejected.source().reference().equals(source.reference()));
    }

    @Override
    public synchronized void saveIfAbsent(RejectedMilestone rejected) {
        if (!existsBySource(rejected.source())) {
            saved.add(rejected);
        }
    }

    public synchronized List<RejectedMilestone> all() {
        return List.copyOf(saved);
    }

    public synchronized void clear() {
        saved.clear();
    }
}

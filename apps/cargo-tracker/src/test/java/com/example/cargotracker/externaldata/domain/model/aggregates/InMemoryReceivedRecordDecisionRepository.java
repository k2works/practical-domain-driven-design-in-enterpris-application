package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * メモリ上の受信記録の判断のリポジトリ（テスト用。Bolt 39d2）。追記と判断の有無の照会だけを持つ。受信記録ごとに 1 つの一意制約を写し、同じ
 * 受信記録の 2 つ目の追加は MyBatis の実装と同じく {@link DuplicateReceivedRecordDecisionException} にする（行 6）。メモリの受信記録の
 * リポジトリが、判断のある隔離を判断を待つ隔離から除くために読む。
 */
public class InMemoryReceivedRecordDecisionRepository implements ReceivedRecordDecisionRepository {

    private final List<ReceivedRecordDecision> decisions = new CopyOnWriteArrayList<>();

    @Override
    public synchronized void add(ReceivedRecordDecision decision) {
        Objects.requireNonNull(decision, "decision");
        if (existsFor(decision.receivedRecordId())) {
            throw DuplicateReceivedRecordDecisionException.of(decision.receivedRecordId(), null);
        }
        decisions.add(decision);
    }

    @Override
    public boolean existsFor(UUID receivedRecordId) {
        return decisions.stream()
                .anyMatch(decision -> decision.receivedRecordId().equals(receivedRecordId));
    }

    /** シナリオの開始時に消す（受入シナリオ）。 */
    public void clear() {
        decisions.clear();
    }

    /** 追記した判断（ポートの外。テストの確かめ）。追記の順。 */
    public List<ReceivedRecordDecision> all() {
        return List.copyOf(decisions);
    }
}

package com.example.cargotracker.identity.acceptance;

import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の回復コードのリポジトリ（Bolt 30）。
 * MyBatis と同じく、消費は未使用のときだけ原子的に行う。回復コードは不変なので写しを作らずに返す。
 */
public class InMemoryRecoveryCodeRepository implements RecoveryCodeRepository {

    private final Map<UUID, RecoveryCode> store = new LinkedHashMap<>();

    @Override
    public synchronized void replaceAll(UserId userId, List<RecoveryCode> codes) {
        List<RecoveryCode> issued = RecoveryCode.requireIssuedSet(userId, codes);
        store.values().removeIf(code -> code.userId().equals(userId));
        issued.forEach(code -> store.put(code.id(), code));
    }

    @Override
    public synchronized List<RecoveryCode> findUnusedByUserId(UserId userId) {
        return store.values().stream()
                .filter(code -> code.userId().equals(userId) && !code.isUsed())
                .sorted(Comparator.comparing(
                                (RecoveryCode code) -> code.issuedAt().instant())
                        .thenComparing(RecoveryCode::id))
                .toList();
    }

    @Override
    public synchronized boolean use(UserId userId, UUID recoveryCodeId, UtcInstant usedAt) {
        RecoveryCode stored = store.get(recoveryCodeId);
        if (stored == null || !stored.userId().equals(userId) || stored.isUsed()) {
            return false;
        }
        store.put(recoveryCodeId, stored.use(usedAt));
        return true;
    }

    /** シナリオの開始時に回復コードを消す。 */
    public synchronized void clear() {
        store.clear();
    }
}

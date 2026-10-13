package com.example.cargotracker.identity.acceptance;

import com.example.cargotracker.identity.domain.model.aggregates.DuplicateTotpEnrollmentException;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上の TOTP の資格のリポジトリ（Bolt 30）。
 * MyBatis と同じく、刻みの使用は条件付きで原子的に行う。資格は不変なので写しを作らずに返す。
 */
public class InMemoryTotpCredentialRepository implements TotpCredentialRepository {

    private final Map<UserId, TotpCredential> store = new ConcurrentHashMap<>();

    @Override
    public void add(TotpCredential credential) {
        if (store.putIfAbsent(credential.userId(), credential) != null) {
            throw new DuplicateTotpEnrollmentException("その利用者の TOTP の資格は既にある", null);
        }
    }

    @Override
    public Optional<TotpCredential> findByUserId(UserId userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public boolean useStep(UserId userId, long step) {
        boolean[] used = {false};
        store.computeIfPresent(userId, (id, stored) -> {
            if (!stored.accepts(step)) {
                return stored;
            }
            used[0] = true;
            return stored.use(step);
        });
        return used[0];
    }

    /** シナリオの開始時に資格を消す。 */
    public void clear() {
        store.clear();
    }
}

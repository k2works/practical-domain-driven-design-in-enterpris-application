package com.example.cargotracker.identity.acceptance;

import com.example.cargotracker.identity.domain.model.aggregates.FailureCount;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailure;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオで使う、メモリ上のログインの失敗のリポジトリ（Bolt 30）。
 * MyBatis の外部キーの代わりに登録した利用者を覚え、存在しない利用者の失敗では記録を作らない。
 * ログインの失敗は不変なので写しを作らずに返す。
 */
public class InMemoryLoginFailureRepository implements LoginFailureRepository {

    private final Set<UserId> registeredUsers = ConcurrentHashMap.newKeySet();
    private final Map<UserId, LoginFailure> store = new ConcurrentHashMap<>();

    /** 失敗を数えてよい利用者として登録する（MyBatis では利用者の表の行に当たる）。 */
    public void registerUser(UserId userId) {
        registeredUsers.add(userId);
    }

    @Override
    public LoginFailure findByUserId(UserId userId) {
        return store.getOrDefault(userId, LoginFailure.none(userId));
    }

    @Override
    public void prepare(UserId userId, UtcInstant at) {
        if (registeredUsers.contains(userId)) {
            store.putIfAbsent(userId, LoginFailure.none(userId));
        }
    }

    @Override
    public FailureCount recordFailure(UserId userId, UtcInstant failedAt, LockPolicy policy) {
        boolean[] counted = {false};
        LoginFailure after = store.computeIfPresent(userId, (id, stored) -> {
            counted[0] = !stored.isLocked(failedAt);
            return stored.recordFailure(failedAt, policy);
        });
        return new FailureCount(counted[0], after == null ? LoginFailure.none(userId) : after);
    }

    @Override
    public void recordSuccess(UserId userId, UtcInstant succeededAt) {
        store.computeIfPresent(userId, (id, stored) -> stored.recordSuccess(succeededAt));
    }

    /** シナリオの開始時に記録と登録した利用者を消す。 */
    public void clear() {
        store.clear();
        registeredUsers.clear();
    }
}

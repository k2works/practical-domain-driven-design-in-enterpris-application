package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.FailureCount;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailure;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * ログインの失敗のリポジトリの MyBatis 実装（Bolt 30）。失敗の加算とロックの判定は、マッパーの 1 文の条件付きの
 * 更新で行い、数えたかは更新の件数で決める（Bolt 32）。数えた後の値は同じトランザクションで読み直す（更新が行ロックを
 * 持つので、同時の失敗は先のコミットを待ってから評価し直され、自分の加算までを含んだ値になる）。
 */
@Repository
public class MyBatisLoginFailureRepository implements LoginFailureRepository {

    private final AuthenticationFactorMapper mapper;

    public MyBatisLoginFailureRepository(AuthenticationFactorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LoginFailure findByUserId(UserId userId) {
        return mapper.selectLoginFailure(userId.value())
                .map(row -> LoginFailure.reconstitute(
                        new UserId(row.userId()),
                        row.consecutiveFailures(),
                        row.lockedUntil() == null
                                ? null
                                : new UtcInstant(row.lockedUntil().toInstant())))
                .orElseGet(() -> LoginFailure.none(userId));
    }

    @Override
    public void prepare(UserId userId, UtcInstant at) {
        try {
            mapper.insertLoginFailureIfAbsent(userId.value(), toOffset(at));
        } catch (DuplicateKeyException _) {
            // 同じ利用者の最初の失敗が同時に届いた（先の作成が通っている）。トランザクションの外で呼ぶので、ここで受けてよい
        }
    }

    @Override
    public FailureCount recordFailure(UserId userId, UtcInstant failedAt, LockPolicy policy) {
        int counted = mapper.incrementLoginFailureIfNotLocked(
                userId.value(),
                toOffset(failedAt),
                policy.maxConsecutiveFailures(),
                toOffset(policy.lockedUntil(failedAt)));
        return new FailureCount(counted == 1, findByUserId(userId));
    }

    @Override
    public void recordSuccess(UserId userId, UtcInstant succeededAt) {
        mapper.resetLoginFailure(userId.value(), toOffset(succeededAt));
    }

    @Override
    public boolean unlock(UserId userId, UtcInstant now) {
        return mapper.unlockLoginFailureIfLocked(userId.value(), toOffset(now)) == 1;
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }
}

package com.example.cargotracker.identity.infrastructure.persistence;

import com.example.cargotracker.identity.domain.model.aggregates.LoginFailure;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;

/**
 * ログインの失敗のリポジトリの MyBatis 実装（Bolt 30）。失敗の加算とロックの判定は、マッパーの 1 文の条件付きの
 * 更新で行う。数えた後の値は読み直して返す（同時の失敗では、もう一方の加算を含んだ値になることがある）。
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
    public LoginFailure recordFailure(UserId userId, UtcInstant failedAt, LockPolicy policy) {
        OffsetDateTime at = toOffset(failedAt);
        mapper.insertLoginFailureIfAbsent(userId.value(), at);
        mapper.incrementLoginFailureIfNotLocked(
                userId.value(), at, policy.maxConsecutiveFailures(), toOffset(policy.lockedUntil(failedAt)));
        return findByUserId(userId);
    }

    @Override
    public void recordSuccess(UserId userId, UtcInstant succeededAt) {
        mapper.resetLoginFailure(userId.value(), toOffset(succeededAt));
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }
}

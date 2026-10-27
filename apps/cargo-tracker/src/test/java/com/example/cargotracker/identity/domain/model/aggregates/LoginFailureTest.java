package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginFailureTest {

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-10-11T00:00:00Z");
    private static final LockPolicy POLICY = new LockPolicy();

    private static UtcInstant at(Duration offset) {
        return new UtcInstant(T0.plus(offset));
    }

    private static LoginFailure failTimes(int times) {
        LoginFailure failure = LoginFailure.none(USER);
        for (int i = 0; i < times; i++) {
            failure = failure.recordFailure(at(Duration.ofSeconds(i)), POLICY);
        }
        return failure;
    }

    @Test
    void 失敗の記録のない利用者は0回でロックしていない() {
        LoginFailure failure = LoginFailure.none(USER);

        assertThat(failure.consecutiveFailures()).isZero();
        assertThat(failure.lockedUntil()).isEmpty();
        assertThat(failure.isLocked(at(Duration.ZERO))).isFalse();
    }

    @Test
    void 失敗が4回続いてもロックしない() {
        LoginFailure failure = failTimes(4);

        assertThat(failure.consecutiveFailures()).isEqualTo(4);
        assertThat(failure.isLocked(at(Duration.ofSeconds(4)))).isFalse();
    }

    @Test
    void 失敗が5回続くと5回目の時刻から15分ロックする() {
        LoginFailure failure = failTimes(5);
        UtcInstant fifth = at(Duration.ofSeconds(4));

        assertThat(failure.consecutiveFailures()).isEqualTo(5);
        assertThat(failure.lockedUntil())
                .contains(new UtcInstant(fifth.instant().plus(Duration.ofMinutes(15))));
        assertThat(failure.isLocked(fifth)).isTrue();
    }

    @Test
    void ロックは14分59秒では解けず15分で解ける() {
        LoginFailure failure = failTimes(5);
        Instant fifth = T0.plusSeconds(4);

        assertThat(failure.isLocked(
                        new UtcInstant(fifth.plus(Duration.ofMinutes(15).minusSeconds(1)))))
                .isTrue();
        assertThat(failure.isLocked(new UtcInstant(fifth.plus(Duration.ofMinutes(15)))))
                .isFalse();
    }

    @Test
    void ロック中の失敗は数えずロックの期限も延ばさない() {
        LoginFailure locked = failTimes(5);

        LoginFailure again = locked.recordFailure(at(Duration.ofMinutes(10)), POLICY);

        assertThat(again.consecutiveFailures()).isEqualTo(5);
        assertThat(again.lockedUntil()).isEqualTo(locked.lockedUntil());
    }

    @Test
    void ロックが解けた後の失敗は1回目から数え直す() {
        LoginFailure locked = failTimes(5);

        LoginFailure afterLock = locked.recordFailure(at(Duration.ofMinutes(20)), POLICY);

        assertThat(afterLock.consecutiveFailures()).isEqualTo(1);
        assertThat(afterLock.lockedUntil()).isEmpty();
        assertThat(afterLock.isLocked(at(Duration.ofMinutes(20)))).isFalse();
    }

    @Test
    void 成功すると回数を0に戻す() {
        LoginFailure failure = failTimes(3).recordSuccess(at(Duration.ofMinutes(1)));

        assertThat(failure.consecutiveFailures()).isZero();
        assertThat(failure.lockedUntil()).isEmpty();
    }

    @Test
    void ロックが解けた後の成功はロックを外す() {
        LoginFailure failure = failTimes(5).recordSuccess(at(Duration.ofMinutes(20)));

        assertThat(failure.consecutiveFailures()).isZero();
        assertThat(failure.lockedUntil()).isEmpty();
    }

    @Test
    void ロック中の成功はロックを消さない() {
        LoginFailure locked = failTimes(5);

        LoginFailure afterSuccess = locked.recordSuccess(at(Duration.ofMinutes(1)));

        assertThat(afterSuccess.consecutiveFailures()).isEqualTo(5);
        assertThat(afterSuccess.lockedUntil()).isEqualTo(locked.lockedUntil());
    }

    // システム管理者のロックの解除（Bolt 38c。US-18 AC3 の運用の側）

    @Test
    void ロック中なら解除で回数を0に戻しロックを外す() {
        LoginFailure unlocked = failTimes(5).unlock(at(Duration.ofMinutes(1)));

        assertThat(unlocked.consecutiveFailures()).isZero();
        assertThat(unlocked.lockedUntil()).isEmpty();
        assertThat(unlocked.isLocked(at(Duration.ofMinutes(1)))).isFalse();
    }

    @Test
    void ロック中でなければ解除しても変えない() {
        LoginFailure failing = failTimes(3);
        LoginFailure expired = failTimes(5);

        assertThat(failing.unlock(at(Duration.ofMinutes(1))).consecutiveFailures())
                .isEqualTo(3);
        assertThat(expired.unlock(at(Duration.ofMinutes(20))).lockedUntil()).isEqualTo(expired.lockedUntil());
    }
}

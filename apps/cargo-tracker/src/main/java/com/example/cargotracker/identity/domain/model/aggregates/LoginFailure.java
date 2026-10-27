package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.rules.LockPolicy;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;

/**
 * ログインの失敗。利用者ごとのログインの password・TOTP・回復コードの連続の誤りの回数と、ロックの期限を持つ（IA-INV-03）。
 * ロック中の失敗は数えず期限も延ばさない。ロックが解けた後の失敗は 1 回目から数え直す。ロック中でなければ、2 つの要素がそろった
 * ログインの成功で 0 に戻す（Bolt 32。Bolt 34c で注釈とそろえた）。ロック中なら、システム管理者の解除でも 0 に戻す（Bolt 38c）。
 * 利用者とは別の集約にし、利用者の版に触れずにリポジトリの条件付きの更新で原子的に保存する（ADR-011、ADR-012）。
 * 失敗の記録のない利用者は「0 回・ロックなし」とし、存在しないメールアドレスの失敗では作らない。
 */
@AggregateRoot
public final class LoginFailure {

    private final UserId userId;
    private final int consecutiveFailures;
    private final UtcInstant lockedUntil;

    private LoginFailure(UserId userId, int consecutiveFailures, UtcInstant lockedUntil) {
        this.userId = Objects.requireNonNull(userId, "userId");
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("連続の失敗の回数は 0 以上");
        }
        this.consecutiveFailures = consecutiveFailures;
        this.lockedUntil = lockedUntil;
    }

    /** 失敗の記録のない利用者。0 回でロックしていない。 */
    public static LoginFailure none(UserId userId) {
        return new LoginFailure(userId, 0, null);
    }

    /** 保存されている状態からログインの失敗を組み立てる（リポジトリが使う）。ロックの期限は、なければ null。 */
    public static LoginFailure reconstitute(UserId userId, int consecutiveFailures, UtcInstant lockedUntil) {
        return new LoginFailure(userId, consecutiveFailures, lockedUntil);
    }

    public UserId userId() {
        return userId;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    public Optional<UtcInstant> lockedUntil() {
        return Optional.ofNullable(lockedUntil);
    }

    /** その時刻にロックしているか。期限の時刻ちょうどで解ける。 */
    public boolean isLocked(UtcInstant now) {
        return lockedUntil != null && now.instant().isBefore(lockedUntil.instant());
    }

    /** その時刻の失敗を記録したログインの失敗を返す。 */
    public LoginFailure recordFailure(UtcInstant failedAt, LockPolicy policy) {
        if (isLocked(failedAt)) {
            return this;
        }
        int failures = (lockedUntil == null ? consecutiveFailures : 0) + 1;
        return new LoginFailure(userId, failures, policy.shouldLock(failures) ? policy.lockedUntil(failedAt) : null);
    }

    /**
     * その時刻の成功を記録したログインの失敗を返す。回数を 0 に戻しロックを外す。ロック中なら変えない
     * （ロックの確かめと成功の間に、並行の失敗でロックしたときにロックを消さない）。
     */
    public LoginFailure recordSuccess(UtcInstant succeededAt) {
        return isLocked(succeededAt) ? this : none(userId);
    }

    /**
     * その時刻にシステム管理者がロックを解除したログインの失敗を返す（Bolt 38c。US-18 AC3 の運用の側）。ロック中なら回数を 0 に戻し
     * ロックを外す。ロック中でなければ変えない（期限の切れたロックと失敗の途中の回数は、解除の操作では変えない）。
     */
    public LoginFailure unlock(UtcInstant now) {
        return isLocked(now) ? none(userId) : this;
    }
}

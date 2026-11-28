package com.example.cargotracker.identity.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;

/**
 * ロックの方針。password か TOTP の連続の失敗が上限に達したら、その失敗の時刻から一定の時間ロックする
 * （IA-INV-03、BR-14、SEC-02）。
 * 判定は「連続の回数が上限以上」の形に保つ。リポジトリの SQL（AuthenticationFactorMapper.xml の CASE）が同じ形で
 * 判定するため、形を変えるときは SQL も変え、契約テストで両方を確かめる。
 */
@DomainRule
public class LockPolicy {

    /** 連続の失敗の上限。 */
    public static final int MAX_CONSECUTIVE_FAILURES = 5;

    /** ロックの長さ。 */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /** 連続の失敗の上限（リポジトリの条件付きの更新に渡す）。 */
    public int maxConsecutiveFailures() {
        return MAX_CONSECUTIVE_FAILURES;
    }

    /** その回数の連続の失敗でロックするか。 */
    public boolean shouldLock(int consecutiveFailures) {
        return consecutiveFailures >= maxConsecutiveFailures();
    }

    /** その時刻の失敗でロックしたときの期限。 */
    public UtcInstant lockedUntil(UtcInstant failedAt) {
        return new UtcInstant(failedAt.instant().plus(LOCK_DURATION));
    }
}

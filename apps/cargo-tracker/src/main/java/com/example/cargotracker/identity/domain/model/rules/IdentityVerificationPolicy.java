package com.example.cargotracker.identity.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Optional;

/**
 * 本人確認の日時の規則。システム管理者が TOTP の登録を解除するときに記録する本人確認の日時は、今より後でなく、今から 7 日より前でない
 * （ちょうど 7 日前は受け付ける。Bolt 38c の開始準備の決定 (4)、2026-10-27、human:kakimomokuri）。古い確認で解除させない。
 */
@DomainRule
public final class IdentityVerificationPolicy {

    /** 受け付ける本人確認の古さの上限。 */
    public static final Duration MAX_AGE = Duration.ofDays(7);

    private IdentityVerificationPolicy() {}

    /**
     * 本人確認の日時が範囲の外なら、その理由。範囲の中なら空。
     *
     * @param verifiedAt 本人確認の日時
     * @param now 今の時刻
     */
    public static Optional<Violation> violationOf(UtcInstant verifiedAt, UtcInstant now) {
        if (verifiedAt.instant().isAfter(now.instant())) {
            return Optional.of(Violation.FUTURE);
        }
        if (verifiedAt.instant().isBefore(now.instant().minus(MAX_AGE))) {
            return Optional.of(Violation.TOO_OLD);
        }
        return Optional.empty();
    }

    /** 範囲の外の理由。 */
    public enum Violation {
        /** 今より後。 */
        FUTURE,
        /** 今から 7 日より前。 */
        TOO_OLD
    }
}

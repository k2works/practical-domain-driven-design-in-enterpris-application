package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 鮮度の判定。判定の時刻が次の予定の実績の予定時刻 + 許容範囲以上なら鮮度超過にする（FRS-02、NFR-FRESH-01。Bolt 39g4。【業務の判断】(aq)）。
 * ちょうど許容範囲は超過（FRS-02 の「36 時間以上」。経路設計の情報鮮度の判定の「同時刻は足りる」と逆）。次の予定の実績がなければ判定しない。
 * 許容範囲は設定の値で、組み立てが渡す（設定 {@code cargotracker.tracking.freshness-tolerance}）。判定は保存しない（読むときの判定。【業務の判断】(ar)）。
 * 時計とリポジトリを持たない（照会サービスが時計から判定の時刻を渡す）。経路設計の情報鮮度の判定とは許容範囲と決定者が違うので共有しない。
 *
 * @param tolerance 許容範囲（正）
 */
@DomainRule
public record FreshnessPolicy(Duration tolerance) {

    /** @throws IllegalArgumentException 許容範囲が正でない */
    public FreshnessPolicy {
        Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.isZero() || tolerance.isNegative()) {
            throw new IllegalArgumentException("鮮度の許容範囲は正の値にしてください: " + tolerance);
        }
    }

    /**
     * 鮮度超過か。
     *
     * @param nextExpectedAt 次の予定の実績の予定時刻（なければ空）
     * @param judgedAt 判定の時刻
     * @return 判定の時刻が予定時刻 + 許容範囲以上なら真（次の予定の実績がなければ偽）
     */
    public boolean exceeded(Optional<UtcInstant> nextExpectedAt, UtcInstant judgedAt) {
        return nextExpectedAt
                .map(expected -> !judgedAt.instant().isBefore(expected.instant().plus(tolerance)))
                .orElse(false);
    }
}

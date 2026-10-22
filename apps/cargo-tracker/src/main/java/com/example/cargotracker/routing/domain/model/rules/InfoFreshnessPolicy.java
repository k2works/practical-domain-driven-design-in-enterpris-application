package com.example.cargotracker.routing.domain.model.rules;

import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 情報鮮度の判定。候補の区間の航海ごとに、判定時刻が「取得時刻 + 許容時間」より後なら情報不足にする（US-06 AC4、R-INV-09、D-89。
 * Bolt 37b2）。同時刻は足りる（期限・接続の「同時刻・同値は適合」にそろえる）。許容時間は設定の値で、組み立てが渡す。
 * 時計とリポジトリを持たない。追跡の鮮度の判定とは許容範囲と決定者が違うので共有しない。
 */
@DomainRule
public final class InfoFreshnessPolicy {

    private final Duration tolerance;

    /**
     * @param tolerance 許容時間（正）
     * @throws IllegalArgumentException 許容時間が正でない
     */
    public InfoFreshnessPolicy(Duration tolerance) {
        Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.isZero() || tolerance.isNegative()) {
            throw new IllegalArgumentException("許容時間は正の値にしてください: " + tolerance);
        }
        this.tolerance = tolerance;
    }

    /** 許容時間。 */
    public Duration tolerance() {
        return tolerance;
    }

    /**
     * 判定する。
     *
     * @return 情報不足の区間の理由（区間の順。足りれば空）
     */
    public List<ExclusionReason> evaluate(List<Leg> legs, UtcInstant judgedAt) {
        return legs.stream()
                .filter(leg -> judgedAt.instant()
                        .isAfter(leg.infoAcquiredAt().instant().plus(tolerance)))
                .map(leg -> ExclusionReason.infoInsufficient(
                        leg.voyageNumber(), leg.infoAcquiredAt(), tolerance, leg.infoVersion()))
                .toList();
    }
}

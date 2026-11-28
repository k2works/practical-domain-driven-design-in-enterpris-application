package com.example.cargotracker.identity.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * KPI の週次の算出の規則（US-21 AC4。W9 の Bolt 44-1。台帳 D-171。仮）。週は UTC の月曜 00:00 始まり。中央値は件数が偶数なら真ん中の 2 つの平均、
 * P90 は最近順位法（昇順に並べた {@code ceil(0.9 × 件数)} 番目）。自己照会率は照会総数に対する顧客 Web で完結した照会数の割合（パーセント）。
 * 値のない週は値を持たない（0 とみなさない）。状態を持たない純粋な計算だけを置く。
 */
@DomainRule
public final class KpiStatistics {

    private KpiStatistics() {}

    /** 指定の時刻が属する週の始まり（UTC の月曜 00:00）。 */
    public static Instant weekStart(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    /** 中央値。件数が偶数なら真ん中の 2 つの平均。値がなければ空。 */
    public static Optional<Duration> median(List<Duration> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        List<Duration> sorted =
                values.stream().sorted(Comparator.naturalOrder()).toList();
        int size = sorted.size();
        if (size % 2 == 1) {
            return Optional.of(sorted.get(size / 2));
        }
        return Optional.of(sorted.get(size / 2 - 1).plus(sorted.get(size / 2)).dividedBy(2));
    }

    /** P90（最近順位法）。値がなければ空。 */
    public static Optional<Duration> percentile90(List<Duration> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        List<Duration> sorted =
                values.stream().sorted(Comparator.naturalOrder()).toList();
        int rank = (int) Math.ceil(0.9 * sorted.size());
        return Optional.of(sorted.get(rank - 1));
    }

    /** 自己照会率（パーセント）。照会総数が 0 なら空。 */
    public static Optional<Double> selfServiceRate(long total, long selfServed) {
        if (total <= 0) {
            return Optional.empty();
        }
        return Optional.of(selfServed * 100.0 / total);
    }
}

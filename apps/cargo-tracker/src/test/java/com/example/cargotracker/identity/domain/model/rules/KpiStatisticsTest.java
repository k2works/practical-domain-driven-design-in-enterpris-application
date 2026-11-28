package com.example.cargotracker.identity.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** KPI の週次の算出の規則（US-21 AC4。W9 の Bolt 44-1。台帳 D-171。仮）。週は UTC の月曜 00:00 始まり、中央値と P90 は次の定義。 */
class KpiStatisticsTest {

    @ParameterizedTest
    @CsvSource({
        "2026-11-16T00:00:00Z, 2026-11-16T00:00:00Z", // 月曜の 00:00 ちょうどはその週の始まり
        "2026-11-15T23:59:59Z, 2026-11-09T00:00:00Z", // 日曜の終わりは前の週
        "2026-11-18T12:34:56Z, 2026-11-16T00:00:00Z",
        "2026-11-22T23:59:59Z, 2026-11-16T00:00:00Z",
        "2026-11-23T00:00:00Z, 2026-11-23T00:00:00Z",
    })
    void 週は_UTC_の月曜_0時始まり(String instant, String expectedWeekStart) {
        assertThat(KpiStatistics.weekStart(Instant.parse(instant))).isEqualTo(Instant.parse(expectedWeekStart));
    }

    private static List<Duration> hours(long... values) {
        return java.util.Arrays.stream(values).mapToObj(Duration::ofHours).toList();
    }

    @Test
    void 中央値は奇数なら真ん中_偶数なら真ん中の二つの平均() {
        assertThat(KpiStatistics.median(hours(5, 1, 3))).contains(Duration.ofHours(3));
        assertThat(KpiStatistics.median(hours(4, 1, 3, 2))).contains(Duration.ofMinutes(150));
    }

    @Test
    void P90_は最近順位法で_昇順の_ceil_0_9_n_番目() {
        // n = 10 → 9 番目、n = 1 → 1 番目、n = 11 → 10 番目
        assertThat(KpiStatistics.percentile90(hours(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
                .contains(Duration.ofHours(9));
        assertThat(KpiStatistics.percentile90(hours(7))).contains(Duration.ofHours(7));
        assertThat(KpiStatistics.percentile90(hours(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)))
                .contains(Duration.ofHours(10));
    }

    @Test
    void 値がなければ中央値も_P90_もない() {
        assertThat(KpiStatistics.median(List.of())).isEmpty();
        assertThat(KpiStatistics.percentile90(List.of())).isEmpty();
    }

    @Test
    void 自己照会率は照会総数に対する_Web_で完結した数_総数が0ならない() {
        assertThat(KpiStatistics.selfServiceRate(10, 4)).contains(40.0);
        assertThat(KpiStatistics.selfServiceRate(3, 1))
                .hasValueSatisfying(rate -> assertThat(rate).isBetween(33.33, 33.34));
        assertThat(KpiStatistics.selfServiceRate(0, 0)).isEmpty();
    }
}

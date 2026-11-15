package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.KpiWeeklyRow;
import com.example.cargotracker.platform.web.DurationDisplay;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

/**
 * 週次の KPI の 1 行（画面に出す文字。US-21 AC4。W9 の Bolt 44-1）。週は UTC の月曜から日曜までで、範囲を明示する。値のない項目は null
 * （画面は「算出しない」と示す。0 とみなさない）。
 *
 * @param week 週の範囲（例: 2026-10-05 〜 2026-10-11（UTC））
 * @param leadTimeCount KPI-01 の算出に使った見積依頼の件数
 * @param median KPI-01 の中央値（なければ null）
 * @param percentile90 KPI-01 の P90（なければ null）
 * @param inquiryTotal KPI-02 の照会総数
 * @param inquirySelfServed 顧客 Web で完結した照会数
 * @param selfServiceRate KPI-02 自己照会率（照会総数が 0 なら null）
 */
public record KpiWeeklyView(
        String week,
        int leadTimeCount,
        String median,
        String percentile90,
        long inquiryTotal,
        long inquirySelfServed,
        String selfServiceRate) {

    static KpiWeeklyView from(KpiWeeklyRow row) {
        Instant lastDay = row.weekStart().plus(Duration.ofDays(6));
        return new KpiWeeklyView(
                date(row.weekStart()) + " 〜 " + date(lastDay) + "（UTC）",
                row.leadTimeCount(),
                row.median().map(DurationDisplay::hoursAndMinutes).orElse(null),
                row.percentile90().map(DurationDisplay::hoursAndMinutes).orElse(null),
                row.inquiryTotal(),
                row.inquirySelfServed(),
                percent(row.selfServiceRate()));
    }

    private static String date(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static String percent(Optional<Double> rate) {
        return rate.map(value -> String.format(Locale.ROOT, "%.1f%%", value)).orElse(null);
    }
}

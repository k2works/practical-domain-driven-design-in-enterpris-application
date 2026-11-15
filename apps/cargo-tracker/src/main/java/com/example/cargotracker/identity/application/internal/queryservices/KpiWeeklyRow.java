package com.example.cargotracker.identity.application.internal.queryservices;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 週次の KPI の 1 週の値（US-21 AC4。W9 の Bolt 44-1）。KPI-01 は週内に最初の提示をした見積依頼のリードタイム、KPI-02 は週内に受け付けた照会。
 *
 * @param weekStart 週の始まり（UTC の月曜 00:00）
 * @param leadTimeCount KPI-01 の算出に使った見積依頼の件数
 * @param median KPI-01 の中央値（件数が 0 なら空）
 * @param percentile90 KPI-01 の P90（件数が 0 なら空）
 * @param inquiryTotal 照会総数
 * @param inquirySelfServed 顧客 Web で完結した照会数
 * @param selfServiceRate KPI-02 自己照会率（パーセント。照会総数が 0 なら空）
 */
public record KpiWeeklyRow(
        Instant weekStart,
        int leadTimeCount,
        Optional<Duration> median,
        Optional<Duration> percentile90,
        long inquiryTotal,
        long inquirySelfServed,
        Optional<Double> selfServiceRate) {}

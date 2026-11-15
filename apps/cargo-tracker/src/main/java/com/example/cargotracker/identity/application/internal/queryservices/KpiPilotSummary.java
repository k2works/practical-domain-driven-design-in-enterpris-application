package com.example.cargotracker.identity.application.internal.queryservices;

import java.time.Duration;
import java.util.Optional;

/**
 * パイロット中の値（直近の週数ぶんをまとめた KPI-01・KPI-02。基準値と並べる。US-21 AC5。W9 の Bolt 44-2）。
 *
 * @param leadTimeCount KPI-01 の算出に使った見積依頼の件数
 * @param median KPI-01 の中央値
 * @param percentile90 KPI-01 の P90
 * @param inquiryTotal 照会総数
 * @param inquirySelfServed 顧客 Web で完結した照会数
 * @param selfServiceRate KPI-02 自己照会率（パーセント）
 */
public record KpiPilotSummary(
        int leadTimeCount,
        Optional<Duration> median,
        Optional<Duration> percentile90,
        long inquiryTotal,
        long inquirySelfServed,
        Optional<Double> selfServiceRate) {}

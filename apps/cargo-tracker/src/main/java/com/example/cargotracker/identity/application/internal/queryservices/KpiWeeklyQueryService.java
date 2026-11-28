package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.TrackingInquiryStatistics;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import com.example.cargotracker.identity.domain.model.rules.KpiStatistics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 週次の KPI を照会する入力ポート（US-21 AC4。W9 の Bolt 44-1。台帳 D-171。仮）。週は UTC の月曜 00:00 始まり。KPI-01 は、その週に最初の見積りを
 * 提示した見積依頼のリードタイムの中央値と P90。KPI-02 は、その週に受け付けた照会の総数に対する顧客 Web で完結した数の割合（追跡の公開 API から引く）。
 * PV-01 の除外（訓練データ・顧客都合の取消し）の記録の仕組みはまだ無いので、全件を母集団にする（台帳 D-171）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class KpiWeeklyQueryService {

    private static final Duration WEEK = Duration.ofDays(7);

    private final KpiObservationRepository observations;
    private final TrackingInquiryStatistics inquiryStatistics;

    public KpiWeeklyQueryService(KpiObservationRepository observations, TrackingInquiryStatistics inquiryStatistics) {
        this.observations = observations;
        this.inquiryStatistics = inquiryStatistics;
    }

    /**
     * 今週を含む最近の週の値を、新しい週から順に返す。
     *
     * @param now 今の時刻（今週の判定）
     * @param weeks 返す週の数
     */
    @Transactional(readOnly = true)
    public List<KpiWeeklyRow> recentWeeks(Instant now, int weeks) {
        Map<Instant, List<Duration>> leadTimesByWeek = leadTimesByPresentationWeek();
        List<KpiWeeklyRow> rows = new ArrayList<>();
        Instant weekStart = KpiStatistics.weekStart(now);
        for (int i = 0; i < weeks; i++) {
            Instant start = weekStart.minus(WEEK.multipliedBy(i));
            List<Duration> leadTimes = leadTimesByWeek.getOrDefault(start, List.of());
            TrackingInquiryStatistics.Counts counts = inquiryStatistics.countBetween(start, start.plus(WEEK));
            rows.add(new KpiWeeklyRow(
                    start,
                    leadTimes.size(),
                    KpiStatistics.median(leadTimes),
                    KpiStatistics.percentile90(leadTimes),
                    counts.total(),
                    counts.selfServed(),
                    KpiStatistics.selfServiceRate(counts.total(), counts.selfServed())));
        }
        return rows;
    }

    /**
     * パイロット中の値。今週を含む直近の週数ぶんのリードタイムと照会をまとめて求める（基準値と並べる。US-21 AC5。W9 の Bolt 44-2）。
     * 中央値と P90 は、週ごとの値ではなく、その範囲のすべてのリードタイムから求める。
     */
    @Transactional(readOnly = true)
    public KpiPilotSummary pilotSummary(Instant now, int weeks) {
        Instant currentWeek = KpiStatistics.weekStart(now);
        Instant from = currentWeek.minus(WEEK.multipliedBy(weeks - 1L));
        Instant to = currentWeek.plus(WEEK);
        List<Duration> leadTimes = leadTimesByPresentationWeek().entrySet().stream()
                .filter(entry ->
                        !entry.getKey().isBefore(from) && entry.getKey().isBefore(to))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
        TrackingInquiryStatistics.Counts counts = inquiryStatistics.countBetween(from, to);
        return new KpiPilotSummary(
                leadTimes.size(),
                KpiStatistics.median(leadTimes),
                KpiStatistics.percentile90(leadTimes),
                counts.total(),
                counts.selfServed(),
                KpiStatistics.selfServiceRate(counts.total(), counts.selfServed()));
    }

    /** 最初の提示をした見積依頼のリードタイムを、最初の提示をした週ごとにまとめる。 */
    private Map<Instant, List<Duration>> leadTimesByPresentationWeek() {
        return observations.findAll().stream()
                .filter(observation -> observation.firstPresentedAt().isPresent())
                .collect(Collectors.groupingBy(
                        observation -> KpiStatistics.weekStart(
                                observation.firstPresentedAt().orElseThrow().instant()),
                        Collectors.mapping(observation -> observation.leadTime().orElseThrow(), Collectors.toList())));
    }
}

package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.queryservices.KpiComparison;
import com.example.cargotracker.identity.application.internal.queryservices.KpiPilotSummary;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.platform.web.DurationDisplay;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 基準値とパイロット中の値の比較の表示（S-22。US-21 AC5。W9 の Bolt 44-2）。値がなければ null（画面は「未登録」「算出しない」と示す）。
 * 差は、PV-01 の合否の判定ではなく、判断の材料として示す（KPI-01 は基準値に対する短縮・延長の割合、KPI-02 はポイントの差）。
 *
 * @param rows 比較の行（KPI-01 の中央値・90 パーセンタイル、KPI-02 の自己照会率）
 * @param baselines 登録済みの基準値の説明（期間・算出方法・登録者・登録時刻）
 * @param incompletePublications 未完了のイベントの発行の件数
 */
public record KpiComparisonView(List<Row> rows, List<BaselineNote> baselines, long incompletePublications) {

    public KpiComparisonView {
        rows = List.copyOf(rows);
        baselines = List.copyOf(baselines);
    }

    /**
     * 比較の 1 行。
     *
     * @param item 項目名
     * @param baseline 基準値（なければ null）
     * @param pilot パイロット中の値（なければ null）
     * @param difference 差（どちらかがなければ null）
     */
    public record Row(String item, String baseline, String pilot, String difference) {}

    /**
     * 登録済みの基準値の説明。
     *
     * @param kpi KPI の表記
     * @param period 期間
     * @param method 算出方法
     * @param registrant 登録者の表示名
     * @param registeredAt 登録時刻
     */
    public record BaselineNote(String kpi, String period, String method, String registrant, String registeredAt) {}

    static KpiComparisonView from(KpiComparison comparison) {
        KpiPilotSummary pilot = comparison.pilot();
        Optional<BigDecimal> baseline01p50 = comparison.kpi01().map(KpiBaseline::valueP50);
        Optional<BigDecimal> baseline01p90 = comparison.kpi01().flatMap(KpiBaseline::valueP90);
        Optional<BigDecimal> baseline02 = comparison.kpi02().map(KpiBaseline::valueP50);
        List<Row> rows = List.of(
                new Row(
                        "KPI-01 中央値",
                        baseline01p50.map(KpiComparisonView::minutes).orElse(null),
                        pilot.median().map(DurationDisplay::hoursAndMinutes).orElse(null),
                        leadTimeDifference(baseline01p50, pilot.median())),
                new Row(
                        "KPI-01 90 パーセンタイル",
                        baseline01p90.map(KpiComparisonView::minutes).orElse(null),
                        pilot.percentile90()
                                .map(DurationDisplay::hoursAndMinutes)
                                .orElse(null),
                        leadTimeDifference(baseline01p90, pilot.percentile90())),
                new Row(
                        "KPI-02 自己照会率",
                        baseline02.map(value -> percent(value.doubleValue())).orElse(null),
                        pilot.selfServiceRate().map(KpiComparisonView::percent).orElse(null),
                        rateDifference(baseline02, pilot.selfServiceRate())));
        List<BaselineNote> notes = new java.util.ArrayList<>();
        comparison
                .kpi01()
                .ifPresent(b -> notes.add(note(b, comparison.kpi01Registrant().orElse(""))));
        comparison
                .kpi02()
                .ifPresent(b -> notes.add(note(b, comparison.kpi02Registrant().orElse(""))));
        return new KpiComparisonView(rows, notes, comparison.incompletePublications());
    }

    private static BaselineNote note(KpiBaseline baseline, String registrant) {
        return new BaselineNote(
                baseline.kpi().code(),
                baseline.periodFrom() + " 〜 " + baseline.periodTo(),
                baseline.method(),
                registrant,
                DateTimeDisplay.staff(baseline.registeredAt().instant()));
    }

    private static String minutes(BigDecimal minutes) {
        return DurationDisplay.hoursAndMinutes(
                Duration.ofSeconds(minutes.multiply(BigDecimal.valueOf(60)).longValue()));
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /** KPI-01 の差。基準値に対する割合（短縮・延長）。 */
    private static String leadTimeDifference(Optional<BigDecimal> baselineMinutes, Optional<Duration> pilot) {
        if (baselineMinutes.isEmpty()
                || pilot.isEmpty()
                || baselineMinutes.get().signum() == 0) {
            return null;
        }
        double base = baselineMinutes.get().doubleValue();
        double now = pilot.get().toSeconds() / 60.0;
        double change = (now - base) / base * 100.0;
        if (Math.abs(change) < 0.05) {
            return "変化なし";
        }
        return change < 0
                ? String.format(Locale.ROOT, "%.1f%% 短縮", -change)
                : String.format(Locale.ROOT, "%.1f%% 延長", change);
    }

    /** KPI-02 の差。ポイントの差（改善は正）。 */
    private static String rateDifference(Optional<BigDecimal> baseline, Optional<Double> pilot) {
        if (baseline.isEmpty() || pilot.isEmpty()) {
            return null;
        }
        double diff = pilot.get() - baseline.get().doubleValue();
        return String.format(Locale.ROOT, "%+.1f ポイント", diff);
    }
}

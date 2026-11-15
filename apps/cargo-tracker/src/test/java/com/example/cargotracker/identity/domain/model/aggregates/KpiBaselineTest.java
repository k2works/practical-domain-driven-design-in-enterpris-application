package com.example.cargotracker.identity.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * パイロット開始前の基準値（US-21 AC5、KPI-INV-02、PV-01。W9 の Bolt 44-2。台帳 D-172。仮）。期間はパイロット開始前の 4 週間（28 日）、値は KPI-01 が
 * 分、KPI-02 が割合（パーセント）。算出方法と登録者を持ち、訂正は前の記録を指す新しい記録で行う。
 */
class KpiBaselineTest {

    private static final UserId AUDITOR = new UserId(UUID.fromString("00000000-0000-0000-0000-0000044c0001"));
    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-11-16T01:00:00Z"));
    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 11, 1);

    private static KpiBaseline kpi01(LocalDate from, LocalDate to, String p50, String p90, String method) {
        return KpiBaseline.register(
                KpiKind.KPI_01,
                from,
                to,
                new BigDecimal(p50),
                p90 == null ? Optional.empty() : Optional.of(new BigDecimal(p90)),
                method,
                AUDITOR,
                NOW,
                Optional.empty());
    }

    @Test
    void 基準値は期間と値と算出方法と登録者と登録時刻を持つ() {
        KpiBaseline baseline = kpi01(FROM, TO, "480.00", "1440.00", "  現行の受付台帳から見積りの回答までを算出  ");

        assertThat(baseline.kpi()).isEqualTo(KpiKind.KPI_01);
        assertThat(baseline.periodFrom()).isEqualTo(FROM);
        assertThat(baseline.periodTo()).isEqualTo(TO);
        assertThat(baseline.valueP50()).isEqualByComparingTo("480");
        assertThat(baseline.valueP90())
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("1440"));
        assertThat(baseline.method()).isEqualTo("現行の受付台帳から見積りの回答までを算出");
        assertThat(baseline.registeredBy()).isEqualTo(AUDITOR);
        assertThat(baseline.registeredAt()).isEqualTo(NOW);
        assertThat(baseline.supersedes()).isEmpty();
    }

    @Test
    void KPI02は割合だけで_90パーセンタイルを持たない() {
        KpiBaseline baseline = KpiBaseline.register(
                KpiKind.KPI_02,
                FROM,
                TO,
                new BigDecimal("42.50"),
                Optional.empty(),
                "電話・メールの照会の記録から算出",
                AUDITOR,
                NOW,
                Optional.empty());

        assertThat(baseline.valueP50()).isEqualByComparingTo("42.5");
        assertThat(baseline.valueP90()).isEmpty();
        BigDecimal rate = new BigDecimal("42.50");
        Optional<BigDecimal> p90 = Optional.of(new BigDecimal("50"));
        Optional<UUID> none = Optional.empty();
        assertThatThrownBy(() -> KpiBaseline.register(KpiKind.KPI_02, FROM, TO, rate, p90, "算出方法", AUDITOR, NOW, none))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KPI-02");
    }

    @Test
    void 期間は4週間_28日でなければならない() {
        LocalDate shorter = TO.minusDays(1);
        LocalDate longer = TO.plusDays(1);
        assertThatThrownBy(() -> kpi01(FROM, shorter, "480", null, "算出方法"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("28 日");
        assertThatThrownBy(() -> kpi01(FROM, longer, "480", null, "算出方法")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kpi01(TO, FROM, "480", null, "算出方法")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 値の範囲と算出方法を確かめる() {
        // KPI-01 は 0 より大きい分、90 パーセンタイルは中央値以上
        assertThatThrownBy(() -> kpi01(FROM, TO, "0", null, "算出方法")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kpi01(FROM, TO, "480", "100", "算出方法")).isInstanceOf(IllegalArgumentException.class);
        // KPI-02 は 0〜100 のパーセント
        BigDecimal over = new BigDecimal("100.01");
        Optional<BigDecimal> noP90 = Optional.empty();
        Optional<UUID> noPrevious = Optional.empty();
        assertThatThrownBy(() ->
                        KpiBaseline.register(KpiKind.KPI_02, FROM, TO, over, noP90, "算出方法", AUDITOR, NOW, noPrevious))
                .isInstanceOf(IllegalArgumentException.class);
        // 算出方法は必須で 4000 文字まで
        assertThatThrownBy(() -> kpi01(FROM, TO, "480", null, "  ")).isInstanceOf(IllegalArgumentException.class);
        String tooLong = "あ".repeat(4001);
        assertThatThrownBy(() -> kpi01(FROM, TO, "480", null, tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 訂正は前の記録を指す新しい記録で行う() {
        KpiBaseline first = kpi01(FROM, TO, "480", null, "算出方法");
        KpiBaseline corrected = KpiBaseline.register(
                KpiKind.KPI_01,
                FROM,
                TO,
                new BigDecimal("500"),
                Optional.empty(),
                "訂正した算出方法",
                AUDITOR,
                NOW,
                Optional.of(first.id()));

        assertThat(corrected.supersedes()).contains(first.id());
        assertThat(corrected.id()).isNotEqualTo(first.id());
    }
}

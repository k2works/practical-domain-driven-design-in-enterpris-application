package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import java.util.Objects;
import java.util.Optional;

/**
 * 基準値とパイロット中の値を並べたもの（US-21 AC5。W9 の Bolt 44-2）と、未完了の発行の件数（S-22 に示す）。
 *
 * @param kpi01 KPI-01 の最新の基準値（未登録なら空）
 * @param kpi02 KPI-02 の最新の基準値（未登録なら空）
 * @param kpi01Registrant KPI-01 の基準値の登録者の表示名（基準値がなければ空）
 * @param kpi02Registrant KPI-02 の基準値の登録者の表示名（基準値がなければ空）
 * @param pilot パイロット中の値
 * @param incompletePublications 未完了のイベントの発行の件数
 */
public record KpiComparison(
        Optional<KpiBaseline> kpi01,
        Optional<String> kpi01Registrant,
        Optional<KpiBaseline> kpi02,
        Optional<String> kpi02Registrant,
        KpiPilotSummary pilot,
        long incompletePublications) {

    public KpiComparison {
        Objects.requireNonNull(kpi01, "kpi01");
        Objects.requireNonNull(kpi01Registrant, "kpi01Registrant");
        Objects.requireNonNull(kpi02Registrant, "kpi02Registrant");
        Objects.requireNonNull(kpi02, "kpi02");
        Objects.requireNonNull(pilot, "pilot");
    }
}

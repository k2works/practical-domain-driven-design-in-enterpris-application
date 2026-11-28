package com.example.cargotracker.identity.application.internal.commands;

import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.annotation.ddd.Command;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * KPI の基準値を登録するコマンド（US-21 AC5。W9 の Bolt 44-2）。
 *
 * @param kpi KPI の種類
 * @param periodFrom 期間の開始日
 * @param periodTo 期間の終了日（28 日。両端を含む）
 * @param valueP50 KPI-01 は中央値（分）、KPI-02 は自己照会率（パーセント）
 * @param valueP90 KPI-01 の 90 パーセンタイル（分。省略できる）。KPI-02 は持たない
 * @param method 算出方法
 */
@Command
public record RegisterKpiBaselineCommand(
        KpiKind kpi,
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal valueP50,
        Optional<BigDecimal> valueP90,
        String method) {

    public RegisterKpiBaselineCommand {
        Objects.requireNonNull(kpi, "kpi");
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(periodTo, "periodTo");
        Objects.requireNonNull(valueP50, "valueP50");
        Objects.requireNonNull(valueP90, "valueP90");
        Objects.requireNonNull(method, "method");
    }
}

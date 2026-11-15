package com.example.cargotracker.identity.interfaces.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * KPI 基準値の登録の入力（S-22。W9 の Bolt 44-2）。日付と数値の変換は Spring のフォームの変換に任せる（日付の読み取りを画面の部品に散らさない）。
 * 変換できない入力は、値が空のまま（null）届き、登録の段階で「入力してください」と案内する。
 *
 * @param kpi KPI の表記（KPI-01・KPI-02）
 * @param periodFrom 期間の開始日
 * @param periodTo 期間の終了日
 * @param valueP50 KPI-01 は中央値（分）、KPI-02 は自己照会率（パーセント）
 * @param valueP90 KPI-01 の 90 パーセンタイル（分。省略できる）
 * @param method 算出方法
 */
public record KpiBaselineForm(
        String kpi,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
        BigDecimal valueP50,
        BigDecimal valueP90,
        String method) {

    /** 入力のない初期の状態。 */
    static KpiBaselineForm empty() {
        return new KpiBaselineForm("KPI-01", null, null, null, null, "");
    }
}

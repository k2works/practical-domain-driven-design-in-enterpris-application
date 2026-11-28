package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 期間の照会の件数。KPI-02 の算式の材料（W9 の Bolt 44-1）。
 *
 * @param total 照会総数（経路を問わず、引継ぎが不要なものも含む）
 * @param selfServed 顧客 Web で完結した照会数（顧客 Web の自己照会で、有人対応へ移っていないもの）
 */
@ValueObject
public record InquiryCountSummary(long total, long selfServed) {

    public InquiryCountSummary {
        if (total < 0 || selfServed < 0 || selfServed > total) {
            throw new IllegalArgumentException("件数が正しくない: 総数 " + total + "、Web で完結 " + selfServed);
        }
    }
}

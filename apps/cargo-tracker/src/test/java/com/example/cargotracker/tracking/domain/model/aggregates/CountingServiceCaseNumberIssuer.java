package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;

/** 年にかかわらず連番を数えるテスト用の代役（発行した数を {@link #issued} に持つ。Bolt 40-7〜40-9）。 */
public final class CountingServiceCaseNumberIssuer implements ServiceCaseNumberIssuer {

    /** これまでに振った数。 */
    public int issued;

    @Override
    public CaseNumber next(int year) {
        issued++;
        return CaseNumber.of(year, issued);
    }
}

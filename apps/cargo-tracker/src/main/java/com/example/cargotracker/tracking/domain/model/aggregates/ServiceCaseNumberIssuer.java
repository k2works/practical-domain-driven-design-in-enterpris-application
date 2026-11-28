package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;

/**
 * 有人案件の受付番号の採番（送信ポート。Bolt 40-6）。その年の次の受付番号を振る。呼び手のトランザクションの中で採番するので、起票が失敗すれば番号も戻る（欠番が出ない）。
 */
public interface ServiceCaseNumberIssuer {

    /** その年の次の受付番号を振る。 */
    CaseNumber next(int year);
}

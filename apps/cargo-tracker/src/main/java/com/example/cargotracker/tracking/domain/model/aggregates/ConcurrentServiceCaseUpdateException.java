package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;

/**
 * 有人案件を読み込んだ後に、ほかの更新が先に保存されていた（楽観ロックの競合。Bolt 40-6）。利用者には開き直して再操作してもらう。
 */
public class ConcurrentServiceCaseUpdateException extends RuntimeException {

    public ConcurrentServiceCaseUpdateException(CaseNumber number, long expectedAggregateVersion) {
        super("有人案件 " + number.value() + " は読み込んだ後に更新されました（期待した集約の版: " + expectedAggregateVersion + "）");
    }
}

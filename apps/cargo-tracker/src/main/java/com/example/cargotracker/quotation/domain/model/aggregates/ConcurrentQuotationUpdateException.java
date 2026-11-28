package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;

/**
 * 見積りを読み込んだ後に、ほかの更新が先に保存されていた（楽観ロックの競合。ARCH-HO-01）。
 */
public class ConcurrentQuotationUpdateException extends RuntimeException {

    public ConcurrentQuotationUpdateException(QuotationId id, long expectedAggregateVersion) {
        super("見積り " + id.value() + " は読み込んだ後に更新されました（期待した集約の版: " + expectedAggregateVersion + "）");
    }
}

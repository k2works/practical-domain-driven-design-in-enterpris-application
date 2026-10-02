package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;

/**
 * 輸送要求を読み込んだ後に、ほかの更新が先に保存されていた（楽観ロックの競合。ARCH-HO-01）。
 * 利用者には「他の利用者が先に更新しました」と示し、開き直して再操作してもらう。
 */
public class ConcurrentTransportRequestUpdateException extends RuntimeException {

    public ConcurrentTransportRequestUpdateException(TransportRequestId id, long expectedAggregateVersion) {
        super("輸送要求 " + id.value() + " は読み込んだ後に更新されました（期待した集約の版: " + expectedAggregateVersion + "）");
    }
}

package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

/**
 * 追跡記録を読み込んだ後に、ほかの更新が先に保存されていた（楽観ロックの競合。ARCH-HO-01。Bolt 26b）。
 * 同じ出典の実績の同時の登録（一意制約の違反）もこれにする。利用者には開き直して再操作してもらう。
 */
public class ConcurrentTrackingRecordUpdateException extends RuntimeException {

    public ConcurrentTrackingRecordUpdateException(TrackingNumber trackingNumber, long expectedAggregateVersion) {
        super("追跡記録 " + trackingNumber.value() + " は読み込んだ後に更新されました（期待した集約の版: " + expectedAggregateVersion + "）");
    }
}

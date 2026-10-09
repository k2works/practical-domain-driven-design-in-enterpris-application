package com.example.cargotracker.tracking.domain.model.aggregates;

import java.util.Optional;
import java.util.UUID;

/**
 * 追跡記録のリポジトリ（送信ポート）。
 */
public interface TrackingRecordRepository {

    /**
     * 追跡を開始した追跡記録を予定区間とあわせて追加する。同じ予約の追跡記録がすでにあれば、予約 ID の一意制約の違反で失敗する
     * （T-INV-11。同時の配信の負けた側は、例外のままトランザクションを戻し、再配信に任せる）。
     */
    void save(TrackingRecord trackingRecord);

    /** 予約 ID で追跡記録を取得する（DE-07 の再配信の冪等。T-INV-11）。 */
    Optional<TrackingRecord> findByBookingId(UUID bookingId);
}

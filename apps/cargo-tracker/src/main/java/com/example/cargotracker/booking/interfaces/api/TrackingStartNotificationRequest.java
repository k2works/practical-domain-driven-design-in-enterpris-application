package com.example.cargotracker.booking.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 追跡の開始の結果の通知の内容（予約の公開 API。Bolt 25）。
 *
 * @param bookingId 予約 ID（冪等のキー）
 * @param trackingNumber 追跡番号の表記（ログに使う。予約は予約 ID だけで判定する）
 * @param startedAt 追跡を開始した時刻（ログに使う。予約サガの完了の時刻は予約が通知を受けた時刻）
 */
public record TrackingStartNotificationRequest(UUID bookingId, String trackingNumber, UtcInstant startedAt) {

    public TrackingStartNotificationRequest {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}

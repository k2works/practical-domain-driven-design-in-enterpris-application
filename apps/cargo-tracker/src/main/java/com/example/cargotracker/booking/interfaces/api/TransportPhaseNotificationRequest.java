package com.example.cargotracker.booking.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 輸送段階の通知の内容（予約の公開 API。Bolt 39g2）。
 *
 * @param bookingId 予約 ID（冪等のキー）
 * @param trackingNumber 追跡番号の表記（ログに使う。予約は予約 ID だけで判定する）
 * @param milestoneNo 追跡の実績番号（ログに使う）
 * @param occurredAt 集荷・引渡しの発生時刻（ログに使う）
 * @param recordedBy 集荷・引渡しを登録または採用した追跡管理者の利用者 ID（予約の更新者に書く）
 */
public record TransportPhaseNotificationRequest(
        UUID bookingId, String trackingNumber, int milestoneNo, UtcInstant occurredAt, UUID recordedBy) {

    public TransportPhaseNotificationRequest {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(recordedBy, "recordedBy");
    }
}

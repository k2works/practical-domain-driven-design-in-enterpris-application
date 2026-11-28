package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import com.example.cargotracker.booking.interfaces.api.TrackingStartNotification;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationRequest;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import java.util.UUID;

/**
 * 腐敗防止層。予約の公開 API（追跡の開始の結果の通知）を呼び、受領の型をこの層の外に出さない（ADR-014・015。Bolt 25）。
 */
public class BookingTrackingStarts {

    private final TrackingStartNotification trackingStartNotification;

    public BookingTrackingStarts(TrackingStartNotification trackingStartNotification) {
        this.trackingStartNotification = trackingStartNotification;
    }

    /**
     * 追跡を開始したことを予約に通知する。予約 ID で冪等で、完了済みへの再通知は何もしない。
     *
     * @param bookingId 予約 ID
     * @param trackingNumber 追跡番号の表記
     * @param startedAt 追跡を開始した時刻
     * @return 予約が完了にしなかった理由。完了にした・完了済みなら空
     */
    public Optional<String> notifyStarted(UUID bookingId, String trackingNumber, UtcInstant startedAt) {
        TrackingStartNotificationReceipt receipt = trackingStartNotification.notifyStarted(
                new TrackingStartNotificationRequest(bookingId, trackingNumber, startedAt));
        return switch (receipt) {
            case TrackingStartNotificationReceipt.Completed _, TrackingStartNotificationReceipt.AlreadyCompleted _ ->
                Optional.empty();
            case TrackingStartNotificationReceipt.NotCompleted notCompleted -> Optional.of(notCompleted.reason());
        };
    }
}

package com.example.cargotracker.tracking.application.internal.outboundservices.acl;

import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotification;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationRequest;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;
import java.util.UUID;

/**
 * 腐敗防止層。予約の公開 API（輸送段階の通知）を呼び、受領の型をこの層の外に出さない（ADR-014。Bolt 39g2。前例 {@link BookingTrackingStarts}）。
 */
public class BookingTransportPhases {

    private final TransportPhaseNotification transportPhaseNotification;

    public BookingTransportPhases(TransportPhaseNotification transportPhaseNotification) {
        this.transportPhaseNotification = transportPhaseNotification;
    }

    /**
     * 集荷を予約に伝える。予約 ID で冪等で、記録済みへの再通知は何もしない。
     *
     * @param bookingId 予約 ID
     * @param trackingNumber 追跡番号の表記
     * @param milestoneNo 集荷の実績番号
     * @param occurredAt 集荷の発生時刻
     * @param recordedBy 集荷を登録・採用した追跡管理者
     * @return 予約が記録しなかった理由。記録した・記録済みなら空
     */
    public Optional<String> notifyPickedUp(
            UUID bookingId, String trackingNumber, int milestoneNo, UtcInstant occurredAt, UUID recordedBy) {
        return reasonOf(transportPhaseNotification.notifyPickedUp(
                new TransportPhaseNotificationRequest(bookingId, trackingNumber, milestoneNo, occurredAt, recordedBy)));
    }

    /**
     * 引渡しを予約に伝える。予約 ID で冪等で、記録済みへの再通知は何もしない。
     *
     * @param bookingId 予約 ID
     * @param trackingNumber 追跡番号の表記
     * @param milestoneNo 引渡しの実績番号
     * @param occurredAt 引渡しの発生時刻
     * @param recordedBy 引渡しを登録・採用した追跡管理者
     * @return 予約が記録しなかった理由。記録した・記録済みなら空
     */
    public Optional<String> notifyDelivered(
            UUID bookingId, String trackingNumber, int milestoneNo, UtcInstant occurredAt, UUID recordedBy) {
        return reasonOf(transportPhaseNotification.notifyDelivered(
                new TransportPhaseNotificationRequest(bookingId, trackingNumber, milestoneNo, occurredAt, recordedBy)));
    }

    private static Optional<String> reasonOf(TransportPhaseNotificationReceipt receipt) {
        return switch (receipt) {
            case TransportPhaseNotificationReceipt.Recorded _, TransportPhaseNotificationReceipt.AlreadyRecorded _ ->
                Optional.empty();
            case TransportPhaseNotificationReceipt.NotRecorded notRecorded -> Optional.of(notRecorded.reason());
        };
    }
}

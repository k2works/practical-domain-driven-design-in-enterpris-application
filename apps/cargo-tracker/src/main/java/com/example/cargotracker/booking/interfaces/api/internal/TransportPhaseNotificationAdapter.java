package com.example.cargotracker.booking.interfaces.api.internal;

import com.example.cargotracker.booking.application.internal.commandservices.TransportPhaseCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.TransportPhaseOutcome;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotification;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationRequest;
import org.springframework.stereotype.Component;

/**
 * 予約の公開 API（輸送段階の通知）のインバウンドアダプター（ADR-014。Bolt 39g2）。入力ポートに委ね、結果を公開 API の受領の型に変える。予約の型
 * （予約 ID・予約の結果）は公開 API の外に出さない（前例 {@link TrackingStartNotificationAdapter}）。
 */
@Component
public class TransportPhaseNotificationAdapter implements TransportPhaseNotification {

    private final TransportPhaseCommandService commandService;

    public TransportPhaseNotificationAdapter(TransportPhaseCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public TransportPhaseNotificationReceipt notifyPickedUp(TransportPhaseNotificationRequest request) {
        return receipt(commandService.recordPickup(
                new BookingId(request.bookingId()),
                request.recordedBy(),
                request.trackingNumber(),
                request.milestoneNo()));
    }

    @Override
    public TransportPhaseNotificationReceipt notifyDelivered(TransportPhaseNotificationRequest request) {
        return receipt(commandService.recordDelivery(
                new BookingId(request.bookingId()),
                request.recordedBy(),
                request.trackingNumber(),
                request.milestoneNo()));
    }

    private static TransportPhaseNotificationReceipt receipt(TransportPhaseOutcome outcome) {
        return switch (outcome) {
            case RECORDED -> new TransportPhaseNotificationReceipt.Recorded();
            case ALREADY_RECORDED -> new TransportPhaseNotificationReceipt.AlreadyRecorded();
            case BOOKING_NOT_FOUND ->
                new TransportPhaseNotificationReceipt.NotRecorded(
                        TransportPhaseNotificationReceipt.NotRecorded.BOOKING_NOT_FOUND);
            case BOOKING_CANCELLED ->
                new TransportPhaseNotificationReceipt.NotRecorded(
                        TransportPhaseNotificationReceipt.NotRecorded.BOOKING_CANCELLED);
            case BOOKING_UNDER_REQUEST ->
                new TransportPhaseNotificationReceipt.NotRecorded(
                        TransportPhaseNotificationReceipt.NotRecorded.BOOKING_UNDER_REQUEST);
        };
    }
}

package com.example.cargotracker.booking.interfaces.api.internal;

import com.example.cargotracker.booking.application.internal.commandservices.BookingSagaCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.TrackingStartOutcome;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotification;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TrackingStartNotificationRequest;
import org.springframework.stereotype.Component;

/**
 * 予約の公開 API（追跡の開始の結果）のインバウンドアダプター（ADR-015。Bolt 25）。予約サガの入力ポートに委ね、結果を公開 API の受領の型に
 * 変える。予約の型（予約 ID・予約サガの結果）は公開 API の外に出さない。
 */
@Component
public class TrackingStartNotificationAdapter implements TrackingStartNotification {

    private final BookingSagaCommandService sagaCommandService;

    public TrackingStartNotificationAdapter(BookingSagaCommandService sagaCommandService) {
        this.sagaCommandService = sagaCommandService;
    }

    @Override
    public TrackingStartNotificationReceipt notifyStarted(TrackingStartNotificationRequest request) {
        TrackingStartOutcome outcome =
                sagaCommandService.completeTrackingStart(new BookingId(request.bookingId()), request.startedAt());
        return switch (outcome) {
            case COMPLETED -> new TrackingStartNotificationReceipt.Completed();
            case ALREADY_COMPLETED -> new TrackingStartNotificationReceipt.AlreadyCompleted();
            case SAGA_NOT_FOUND ->
                new TrackingStartNotificationReceipt.NotCompleted(
                        TrackingStartNotificationReceipt.NotCompleted.SAGA_NOT_FOUND);
        };
    }
}

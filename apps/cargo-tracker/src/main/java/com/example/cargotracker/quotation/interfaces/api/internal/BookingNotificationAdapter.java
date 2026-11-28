package com.example.cargotracker.quotation.interfaces.api.internal;

import com.example.cargotracker.quotation.application.internal.commandservices.BookingNotificationService;
import com.example.cargotracker.quotation.interfaces.api.BookingNotification;
import com.example.cargotracker.quotation.interfaces.api.BookingNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.BookingNotificationRequest;
import org.springframework.stereotype.Component;

/**
 * 見積りの公開 API（予約確定済みの通知）のインバウンドアダプター（ADR-014。Bolt 23 の実装を 2026-10-09 に interfaces.api へ移した）。
 * 入力ポートに委ね、結果を公開 API の受領の型に変える。理由は見積りのドメインの値の名前ではなく公開 API の定数で返す。
 */
@Component
public class BookingNotificationAdapter implements BookingNotification {

    private final BookingNotificationService service;

    public BookingNotificationAdapter(BookingNotificationService service) {
        this.service = service;
    }

    @Override
    public BookingNotificationReceipt notifyBooked(BookingNotificationRequest request) {
        return switch (service.notifyBooked(
                request.transportRequestId(), request.quotationId(), request.transportRequestVersionNo())) {
            case ADVANCED -> new BookingNotificationReceipt.Booked();
            case ALREADY_REACHED -> new BookingNotificationReceipt.AlreadyBooked();
            case NOT_ADVANCED ->
                new BookingNotificationReceipt.NotBooked(BookingNotificationReceipt.NotBooked.NOT_READY_TO_BOOK);
            case NOT_FOUND ->
                new BookingNotificationReceipt.NotBooked(
                        BookingNotificationReceipt.NotBooked.TRANSPORT_REQUEST_NOT_FOUND);
        };
    }
}

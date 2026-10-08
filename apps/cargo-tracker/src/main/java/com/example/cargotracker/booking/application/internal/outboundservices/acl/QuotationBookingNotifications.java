package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.quotation.api.BookingNotification;
import com.example.cargotracker.quotation.api.BookingNotificationReceipt;
import com.example.cargotracker.quotation.api.BookingNotificationRequest;
import java.util.UUID;

/**
 * 見積りの公開 API（予約確定済みの通知）を呼ぶ腐敗防止層（ADR-014。Bolt 23）。見積りの型は、この部品の外に出さない。
 */
public class QuotationBookingNotifications {

    private final BookingNotification notification;

    public QuotationBookingNotifications(BookingNotification notification) {
        this.notification = notification;
    }

    /**
     * 輸送要求を予約確定済みにするよう見積りに通知する。
     *
     * @return 業務の理由で進まなかったなら、その理由の名前。進んだ・すでに進んでいたなら空文字
     */
    public String notifyBooked(
            UUID transportRequestId, int transportRequestVersionNo, UUID quotationId, UUID bookingId) {
        return switch (notification.notifyBooked(new BookingNotificationRequest(
                transportRequestId, transportRequestVersionNo, quotationId, bookingId))) {
            case BookingNotificationReceipt.Booked _, BookingNotificationReceipt.AlreadyBooked _ -> "";
            case BookingNotificationReceipt.NotBooked notBooked -> notBooked.reason();
        };
    }
}

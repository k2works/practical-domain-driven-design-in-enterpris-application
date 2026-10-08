package com.example.cargotracker.booking.application.internal.eventhandlers;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookingNotifications;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-07 本予約を確定した を購読し、見積りの公開 API で輸送要求を予約確定済みにする（ADR-014。Bolt 23）。
 * 本予約の保存のコミットの後に、非同期で、新しいトランザクションの中で処理する。見積りの操作は冪等なので再配信をそのまま渡す。
 * 業務の理由で進まなかったときは、結果整合が崩れた手がかりとして警告のログを残して終える（予約の確定は戻さない）。
 * 技術の失敗は例外になり、発行の記録を未完了に残して再配信に任せる。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code BookingConfiguration} が担う。
 */
@Service
public class BookingConfirmedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BookingConfirmedEventHandler.class);

    private final QuotationBookingNotifications notifications;

    public BookingConfirmedEventHandler(QuotationBookingNotifications notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(BookingConfirmed event) {
        String rejected = notifications.notifyBooked(
                event.transportRequestId(), event.transportRequestVersionNo(), event.quotationId(), event.bookingId());
        if (!rejected.isEmpty()) {
            LOG.warn(
                    "DE-07 で輸送要求を予約確定済みにしなかった: 予約 {}（{}）、輸送要求 {} の版 {}、見積り {}、理由 {}",
                    event.bookingId(),
                    event.trackingNumber(),
                    event.transportRequestId(),
                    event.transportRequestVersionNo(),
                    event.quotationId(),
                    rejected);
        }
    }
}

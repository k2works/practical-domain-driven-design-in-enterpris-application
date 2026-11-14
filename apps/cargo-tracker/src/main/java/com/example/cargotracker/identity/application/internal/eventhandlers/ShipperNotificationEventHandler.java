package com.example.cargotracker.identity.application.internal.eventhandlers;

import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.identity.application.internal.commandservices.ShipperNotifications;
import com.example.cargotracker.quotation.domain.events.QuotationExpiring;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 業務のイベントを受けて荷主担当者へ通知する（US-22 AC1。Bolt 43-1。ADR-017 の補足。台帳 D-166）。見積りの提示（DE-03）、経路の割当て（DE-21。荷主の承認が
 * 必要になった）、本予約の確定、追跡が確認中になった（DE-18）を、コミットの後に非同期で、新しいトランザクションの中で受け（ADR-003）、その企業の利用中の
 * 荷主担当者全員へ件名と画面への導線だけのメールを送る（{@link ShipperNotifications}）。荷主企業を持たないイベント（荷主企業の ID を足す前の発行）では
 * 送らず警告に残す。契機の ID は種類と事象の組から決まる名前ベースの UUID で、再配信でも同じ値になり（通知の UK で 2 通送らない。N-INV-02）、同じ
 * 見積りの提示と経路の割当てのように ID を共有する別の契機とは混ざらない。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class ShipperNotificationEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ShipperNotificationEventHandler.class);

    private final ShipperNotifications notifications;

    public ShipperNotificationEventHandler(ShipperNotifications notifications) {
        this.notifications = notifications;
    }

    /** DE-03 を受けて、見積りが提示されたと通知する。 */
    @ApplicationModuleListener
    public void on(QuotationPresented event) {
        notifyCompany(
                event.shipperCompanyId(),
                ShipperNotificationKind.QUOTATION_PRESENTED,
                eventId(
                        ShipperNotificationKind.QUOTATION_PRESENTED,
                        event.quotationId().toString()),
                event.transportRequestNumber(),
                requestPath(event.transportRequestNumber()));
    }

    /** 見積りの有効期限の 24 時間前の検出（Bolt 43-2）を受けて、期限が近いと通知する。契機の ID は見積りと有効期限の組から決める。 */
    @ApplicationModuleListener
    public void on(QuotationExpiring event) {
        notifyCompany(
                event.shipperCompanyId(),
                ShipperNotificationKind.QUOTATION_EXPIRING,
                eventId(
                        ShipperNotificationKind.QUOTATION_EXPIRING,
                        event.quotationId() + ":" + event.expiresAt().instant()),
                event.transportRequestNumber(),
                requestPath(event.transportRequestNumber()));
    }

    /** DE-21 を受けて、経路が承認され荷主の承認が必要になったと通知する。 */
    @ApplicationModuleListener
    public void on(QuotationRouteAssigned event) {
        notifyCompany(
                event.shipperCompanyId(),
                ShipperNotificationKind.ROUTE_APPROVED,
                eventId(
                        ShipperNotificationKind.ROUTE_APPROVED,
                        event.quotationId() + ":" + event.routingCaseNumber() + ":" + event.routeVersionNo()),
                event.transportRequestNumber(),
                requestPath(event.transportRequestNumber()));
    }

    /** 本予約の確定を受けて、予約が確定したと通知する。 */
    @ApplicationModuleListener
    public void on(BookingConfirmed event) {
        notifyCompany(
                event.shipperCompanyId() == null
                        ? null
                        : event.shipperCompanyId().value(),
                ShipperNotificationKind.BOOKING_CONFIRMED,
                eventId(ShipperNotificationKind.BOOKING_CONFIRMED, event.bookingId() + ":" + event.bookingVersionNo()),
                event.transportRequestNumber(),
                "/customer/bookings");
    }

    /** DE-18 を受けて、追跡が確認中になったと通知する。 */
    @ApplicationModuleListener
    public void on(TrackingPlacedUnderReview event) {
        notifyCompany(
                event.shipperCompanyId(),
                ShipperNotificationKind.TRACKING_UNDER_REVIEW,
                eventId(
                        ShipperNotificationKind.TRACKING_UNDER_REVIEW,
                        event.trackingNumber() + ":" + event.aggregateVersion()),
                event.trackingNumber(),
                path("/customer/tracking-records/", event.trackingNumber()));
    }

    private void notifyCompany(
            UUID company, ShipperNotificationKind kind, UUID eventId, String targetRef, String path) {
        if (company == null || targetRef == null) {
            LOG.warn("荷主への通知を送れない（荷主企業か対象がイベントにない）: 種類 {}、契機 {}", kind, eventId);
            return;
        }
        notifications.notifyCompany(new CompanyId(company), kind, eventId, targetRef, path);
    }

    /** 見積依頼の詳細への導線（見積りの提示・経路の承認・期限が近い）。 */
    private static String requestPath(String transportRequestNumber) {
        return path("/customer/transport-requests/", transportRequestNumber);
    }

    private static String path(String prefix, String ref) {
        return ref == null ? prefix : prefix + ref;
    }

    /** 種類と事象の組から決まる契機の ID（再配信でも同じ値）。 */
    private static UUID eventId(ShipperNotificationKind kind, String occurrence) {
        return UUID.nameUUIDFromBytes((kind + ":" + occurrence).getBytes(StandardCharsets.UTF_8));
    }
}

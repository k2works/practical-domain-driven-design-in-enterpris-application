package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.aggregates.ShipperNotificationMailTemplate;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import java.util.Locale;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 荷主への業務の通知の文面（US-22。Bolt 43）。Thymeleaf のテキストのテンプレート（{@code classpath:mail/shipper-<種類>.txt}）で本文を組み立てる
 * （エンジンは {@link MailTextTemplates}）。本文は件名の趣旨の 1 文と画面への導線だけ（AC3、N-INV-01）。
 */
public final class ThymeleafShipperNotificationMailTemplate implements ShipperNotificationMailTemplate {

    private final SpringTemplateEngine engine = MailTextTemplates.engine();

    @Override
    public OutgoingMail compose(String to, NotificationKind kind, String targetRef, String link, String locale) {
        Context context = new Context(Locale.forLanguageTag(locale));
        context.setVariable("targetRef", targetRef);
        context.setVariable("link", link);
        return new OutgoingMail(to, subject(kind), engine.process(templateName(kind), context));
    }

    static String subject(NotificationKind kind) {
        return switch (kind) {
            case QUOTATION_PRESENTED -> "【cargo-tracker】見積りが提示されました";
            case ROUTE_APPROVED -> "【cargo-tracker】経路が承認されました。ご承認をお願いします";
            case QUOTATION_EXPIRING -> "【cargo-tracker】見積りの有効期限が近づいています";
            case BOOKING_CONFIRMED -> "【cargo-tracker】本予約が確定しました";
            case TRACKING_UNDER_REVIEW -> "【cargo-tracker】貨物の追跡を確認しています";
            default -> throw new IllegalArgumentException("荷主への業務の通知の種類ではない: " + kind);
        };
    }

    private static String templateName(NotificationKind kind) {
        return switch (kind) {
            case QUOTATION_PRESENTED -> "shipper-quotation-presented";
            case ROUTE_APPROVED -> "shipper-route-approved";
            case QUOTATION_EXPIRING -> "shipper-quotation-expiring";
            case BOOKING_CONFIRMED -> "shipper-booking-confirmed";
            case TRACKING_UNDER_REVIEW -> "shipper-tracking-under-review";
            default -> throw new IllegalArgumentException("荷主への業務の通知の種類ではない: " + kind);
        };
    }
}

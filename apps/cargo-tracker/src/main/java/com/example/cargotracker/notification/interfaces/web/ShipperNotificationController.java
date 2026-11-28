package com.example.cargotracker.notification.interfaces.web;

import com.example.cargotracker.notification.application.internal.queryservices.NotificationQueryService;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * C-16 通知一覧（荷主担当者。US-22 AC4。W9 の Bolt 43-3）。本人に宛てた通知（メールと同じ内容）と送信の結果を、新しい順に示す。送信に失敗した通知は
 * 失敗と宛先を示し、技術的な理由は示さない（調査用。AC4）。画面には、通知の種類の文と対象の参照、対象の画面への導線を出す。URL の規則
 * （{@code /customer/**} は荷主担当者だけ）がほかの役割を止める。
 */
@Controller
@RequestMapping("/customer/notifications")
public class ShipperNotificationController {

    private final NotificationQueryService queryService;

    public ShipperNotificationController(NotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public String list(AuthenticatedActor actor, Model model) {
        model.addAttribute("notifications", rows(queryService.list(actor)));
        return "notification/customer/list";
    }

    /** 一覧の 1 行（画面に出す文字）。 */
    record Row(
            String title, String targetRef, String link, String result, String at, String recipient, boolean failed) {}

    private static List<Row> rows(List<Notification> notifications) {
        return notifications.stream().map(ShipperNotificationController::row).toList();
    }

    private static Row row(Notification notification) {
        boolean failed = notification.status() == NotificationStatus.FAILED;
        String result =
                switch (notification.status()) {
                    case SENT -> "送信済み";
                    case FAILED -> "送信に失敗しました";
                    case PENDING -> "送信待ち";
                };
        var at = notification.sentAt().orElse(notification.createdAt());
        return new Row(
                title(notification),
                notification.targetRef(),
                link(notification),
                result,
                DateTimeDisplay.customer(at.instant()),
                notification.email(),
                failed);
    }

    private static String title(Notification notification) {
        return switch (notification.kind()) {
            case QUOTATION_PRESENTED -> "見積りが提示されました";
            case ROUTE_APPROVED -> "経路が承認されました（荷主のご承認が必要です）";
            case QUOTATION_EXPIRING -> "見積りの有効期限が近づいています";
            case BOOKING_CONFIRMED -> "本予約が確定しました";
            case TRACKING_UNDER_REVIEW -> "貨物の追跡を確認しています";
            default -> notification.kind().name();
        };
    }

    /** 対象の画面への導線（通知のメールと同じ行き先）。 */
    private static String link(Notification notification) {
        return switch (notification.kind()) {
            case QUOTATION_PRESENTED, ROUTE_APPROVED, QUOTATION_EXPIRING ->
                "/customer/transport-requests/" + notification.targetRef();
            case BOOKING_CONFIRMED -> "/customer/bookings";
            case TRACKING_UNDER_REVIEW -> "/customer/tracking-records/" + notification.targetRef();
            default -> "/customer/notifications";
        };
    }
}

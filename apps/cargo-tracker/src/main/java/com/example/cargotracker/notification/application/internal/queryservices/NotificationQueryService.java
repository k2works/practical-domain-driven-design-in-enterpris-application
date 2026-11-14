package com.example.cargotracker.notification.application.internal.queryservices;

import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 荷主の通知一覧の照会（C-16。US-22 AC4。W9 の Bolt 43-3）。荷主担当者本人に宛てた通知を、新しい順に上限まで読む。一覧に出すのは荷主への業務の通知
 * （見積りの提示・経路の承認・期限が近い・本予約の確定・追跡が確認中）だけで、再設定や招待のメールは出さない。メールの本文は記録していないので、種類と対象の
 * 参照から画面が再現する（N-INV-01）。荷主担当者でなければ空（URL の規則が先に止めるので、通常は届かない）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code NotificationConfiguration} が担う。
 */
@Service
public class NotificationQueryService {

    /** 一覧に出す種類（荷主への業務の通知）。 */
    static final Set<NotificationKind> KINDS = Set.copyOf(EnumSet.of(
            NotificationKind.QUOTATION_PRESENTED,
            NotificationKind.ROUTE_APPROVED,
            NotificationKind.QUOTATION_EXPIRING,
            NotificationKind.BOOKING_CONFIRMED,
            NotificationKind.TRACKING_UNDER_REVIEW));

    /** 一覧の件数の上限。 */
    static final int LIMIT = 100;

    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** 本人の通知を新しい順に返す。 */
    @Transactional(readOnly = true)
    public List<Notification> list(AuthenticatedActor actor) {
        if (!actor.hasRole(Role.SHIPPER)) {
            return List.of();
        }
        return notifications.findRecentByRecipient(actor.userId().value(), KINDS, LIMIT);
    }
}

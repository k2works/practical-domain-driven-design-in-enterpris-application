package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.application.internal.outboundservices.acl.NotificationShipperMails;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 荷主への業務の通知の宛先の解決と送信（US-22。Bolt 43。台帳 D-166。仮）。業務のイベント（見積りの提示・経路の承認・本予約の確定・追跡が確認中）を
 * 購読する identity のハンドラーが使う。**その企業の利用中の荷主担当者全員**へ、件名と画面への導線だけのメールを送る（停止中の利用者と荷主担当者
 * でない利用者には送らない。個人の配信設定は第 2 段階の UC-13）。送れなかった宛先があっても残りへ送り、失敗は例外にせず警告に残して、
 * 失敗した宛先の数を返す（AC4 の記録は通知の側が持つ）。ログに出すのは企業・契機・種類だけで、メールアドレスは出さない。
 */
public class ShipperNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(ShipperNotifications.class);

    private final UserRepository users;
    private final NotificationShipperMails mails;
    private final String publicBaseUrl;

    public ShipperNotifications(UserRepository users, NotificationShipperMails mails, String publicBaseUrl) {
        this.users = users;
        this.mails = mails;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * その企業の利用中の荷主担当者全員へ通知を送る。
     *
     * @param eventId 契機（イベント）の ID（同じ契機と宛先で冪等）
     * @param targetRef 対象の参照（業務番号など）
     * @param path 画面のパス（先頭が {@code /}。公開の base URL につなげて導線にする）
     * @return 送れなかった宛先の数
     */
    public int notifyCompany(
            CompanyId company, ShipperNotificationKind kind, UUID eventId, String targetRef, String path) {
        List<UserSummary> recipients = users.findSummariesIn(Set.of(company)).stream()
                .filter(user ->
                        user.status() == UserStatus.ACTIVE && user.roles().contains(Role.SHIPPER))
                .toList();
        if (recipients.isEmpty()) {
            LOG.warn("荷主への通知の宛先がいない: 企業 {}、種類 {}、契機 {}", company.value(), kind, eventId);
            return 0;
        }
        String link = publicBaseUrl + path;
        int failures = 0;
        for (UserSummary recipient : recipients) {
            Optional<String> failure = mails.send(eventId, recipient.id(), recipient.email(), kind, targetRef, link);
            if (failure.isPresent()) {
                failures++;
                LOG.warn(
                        "荷主への通知を送れなかった: 企業 {}、種類 {}、契機 {}、利用者 {}、理由 {}",
                        company.value(),
                        kind,
                        eventId,
                        recipient.id().value(),
                        failure.get());
            }
        }
        return failures;
    }
}

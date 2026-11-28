package com.example.cargotracker.identity.application.internal.outboundservices.acl;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.notification.interfaces.api.ShipperNotificationMail;
import com.example.cargotracker.notification.interfaces.api.ShipperNotificationMailReceipt;
import com.example.cargotracker.notification.interfaces.api.ShipperNotificationMailRequest;
import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import com.example.cargotracker.shared.domain.UserId;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知の荷主への業務の通知。腐敗防止層で、通知の公開 API（{@link ShipperNotificationMail}）を呼び、結果の型をこの層の外に出さない（ADR-017。
 * US-22。Bolt 43）。宛先は identity が渡す（通知は identity に依存しない）。
 */
public class NotificationShipperMails {

    /** 文面の言語（今は日本語だけ。UI 設計）。 */
    static final String LOCALE = "ja";

    private final ShipperNotificationMail shipperNotificationMail;

    public NotificationShipperMails(ShipperNotificationMail shipperNotificationMail) {
        this.shipperNotificationMail = shipperNotificationMail;
    }

    /**
     * 荷主担当者へ業務の通知を送る。契機の ID と宛先で冪等。
     *
     * @param eventId 契機（イベント）の ID
     * @param userId 宛先の利用者
     * @param email 宛先のメールアドレス
     * @param kind 通知の種類
     * @param targetRef 対象の参照（業務番号など）
     * @param link 画面への導線の URL
     * @return 送れなかった理由。送った・すでに送ったなら空
     */
    public Optional<String> send(
            UUID eventId,
            UserId userId,
            EmailAddress email,
            ShipperNotificationKind kind,
            String targetRef,
            String link) {
        ShipperNotificationMailReceipt receipt = shipperNotificationMail.send(new ShipperNotificationMailRequest(
                eventId, userId.value(), email.value(), kind, targetRef, link, LOCALE));
        return switch (receipt) {
            case ShipperNotificationMailReceipt.Sent _, ShipperNotificationMailReceipt.AlreadySent _ ->
                Optional.empty();
            case ShipperNotificationMailReceipt.Failed failed -> Optional.of(failed.reason());
        };
    }
}

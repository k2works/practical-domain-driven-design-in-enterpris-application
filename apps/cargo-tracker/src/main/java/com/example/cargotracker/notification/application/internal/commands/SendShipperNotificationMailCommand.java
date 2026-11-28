package com.example.cargotracker.notification.application.internal.commands;

import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.shared.annotation.ddd.Command;
import java.util.Objects;
import java.util.UUID;

/**
 * 荷主への業務の通知を送るコマンド（ADR-017。US-22。Bolt 43）。公開 API の要求を写したもの。
 *
 * @param eventId 契機（イベント）の ID（通知の契機）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param kind 通知の種類
 * @param targetRef 対象の参照（業務番号など）
 * @param link 画面への導線の URL
 * @param locale 文面の言語
 */
@Command
public record SendShipperNotificationMailCommand(
        UUID eventId,
        UUID recipientUserId,
        String email,
        NotificationKind kind,
        String targetRef,
        String link,
        String locale) {

    public SendShipperNotificationMailCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetRef, "targetRef");
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(locale, "locale");
    }

    /** メールアドレスを文字列に出さない。 */
    @Override
    public String toString() {
        return "SendShipperNotificationMailCommand[eventId=" + eventId + ", recipientUserId=" + recipientUserId
                + ", kind=" + kind + ", targetRef=" + targetRef + "]";
    }
}

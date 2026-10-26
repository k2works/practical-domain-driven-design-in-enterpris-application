package com.example.cargotracker.notification.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 利用者の招待のメールを送るコマンド（ADR-017。Bolt 38b2b）。公開 API の要求を写したもの。
 *
 * @param registrationId 利用者の登録の ID（通知の契機）
 * @param tokenId 招待のトークンの ID（通知の対象）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param invitationUrl 初回の設定のリンク（本文にだけ使い、記録しない）
 * @param expiresAt リンクの期限
 * @param locale 文面の言語
 */
@Command
public record SendUserInvitationMailCommand(
        UUID registrationId,
        UUID tokenId,
        UUID recipientUserId,
        String email,
        String invitationUrl,
        UtcInstant expiresAt,
        String locale) {

    public SendUserInvitationMailCommand {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(invitationUrl, "invitationUrl");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** 初回の設定のリンクを文字列に出さない。 */
    @Override
    public String toString() {
        return "SendUserInvitationMailCommand[registrationId=" + registrationId + ", tokenId=" + tokenId + "]";
    }
}

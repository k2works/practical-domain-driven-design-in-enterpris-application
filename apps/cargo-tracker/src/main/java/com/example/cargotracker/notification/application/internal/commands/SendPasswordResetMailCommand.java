package com.example.cargotracker.notification.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * password の再設定のメールを送るコマンド（ADR-017。Bolt 33）。公開 API の要求を写したもの。
 *
 * @param requestId 再設定の依頼の ID（通知の契機）
 * @param tokenId 再設定のトークンの ID（通知の対象）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param resetUrl 再設定のリンク（本文にだけ使い、記録しない）
 * @param expiresAt リンクの期限
 * @param locale 文面の言語
 */
@Command
public record SendPasswordResetMailCommand(
        UUID requestId,
        UUID tokenId,
        UUID recipientUserId,
        String email,
        String resetUrl,
        UtcInstant expiresAt,
        String locale) {

    public SendPasswordResetMailCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(resetUrl, "resetUrl");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** 再設定のリンクを文字列に出さない。 */
    @Override
    public String toString() {
        return "SendPasswordResetMailCommand[requestId=" + requestId + ", tokenId=" + tokenId + "]";
    }
}

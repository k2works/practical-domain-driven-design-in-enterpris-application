package com.example.cargotracker.notification.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理者による TOTP の登録の解除のお知らせを送るコマンド（ADR-017。Bolt 38d）。公開 API の要求を写したもの。
 *
 * @param auditRecordId 解除の監査記録の ID（通知の契機と対象）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param resetAt 解除の時刻
 * @param locale 文面の言語
 */
@Command
public record SendTotpResetMailCommand(
        UUID auditRecordId, UUID recipientUserId, String email, UtcInstant resetAt, String locale) {

    public SendTotpResetMailCommand {
        Objects.requireNonNull(auditRecordId, "auditRecordId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(resetAt, "resetAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** メールアドレスを文字列に出さない。 */
    @Override
    public String toString() {
        return "SendTotpResetMailCommand[auditRecordId=" + auditRecordId + ", recipientUserId=" + recipientUserId + "]";
    }
}

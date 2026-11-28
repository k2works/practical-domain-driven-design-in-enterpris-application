package com.example.cargotracker.notification.application.internal.commands;

import com.example.cargotracker.shared.annotation.ddd.Command;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 認証アプリの登録か再登録のお知らせを送るコマンド（ADR-017。Bolt 38d）。公開 API の要求を写したもの。
 *
 * @param auditRecordId 登録か再登録の監査記録の ID（通知の契機と対象）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param enrolledAt 登録か再登録の時刻
 * @param reEnrolled 回復コードの経路の再登録なら true
 * @param locale 文面の言語
 */
@Command
public record SendTotpEnrollmentMailCommand(
        UUID auditRecordId,
        UUID recipientUserId,
        String email,
        UtcInstant enrolledAt,
        boolean reEnrolled,
        String locale) {

    public SendTotpEnrollmentMailCommand {
        Objects.requireNonNull(auditRecordId, "auditRecordId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(enrolledAt, "enrolledAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** メールアドレスを文字列に出さない。 */
    @Override
    public String toString() {
        return "SendTotpEnrollmentMailCommand[auditRecordId=" + auditRecordId + ", recipientUserId=" + recipientUserId
                + ", reEnrolled=" + reEnrolled + "]";
    }
}

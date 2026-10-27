package com.example.cargotracker.notification.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理者による TOTP の登録の解除のお知らせの要求（通知の公開 API。ADR-017。Bolt 38d）。
 *
 * @param auditRecordId 解除の監査記録の ID（通知の {@code event_id} と {@code target_ref}。同じ解除の 2 回目の要求を見分ける）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param resetAt 解除の時刻（監査の時刻。本文に示す）
 * @param locale 文面の言語（BCP 47。今は {@code ja} だけ）
 */
public record TotpResetMailRequest(
        UUID auditRecordId, UUID recipientUserId, String email, UtcInstant resetAt, String locale) {

    public TotpResetMailRequest {
        Objects.requireNonNull(auditRecordId, "auditRecordId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(resetAt, "resetAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** メールアドレスを文字列に出さない（ログや例外の文に載せない）。 */
    @Override
    public String toString() {
        return "TotpResetMailRequest[auditRecordId=" + auditRecordId + ", recipientUserId=" + recipientUserId
                + ", resetAt=" + resetAt + ", locale=" + locale + "]";
    }
}

package com.example.cargotracker.notification.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 認証アプリの登録と再登録のお知らせの要求（通知の公開 API。ADR-017。Bolt 38d）。
 *
 * @param auditRecordId 登録か再登録の監査記録の ID（通知の {@code event_id} と {@code target_ref}）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param enrolledAt 登録か再登録の時刻（監査の時刻。本文に示す）
 * @param reEnrolled 回復コードの経路の再登録なら true（種類 {@code TOTP_RE_ENROLLED} と再登録の文面）、A-07 の登録なら false
 * @param locale 文面の言語（BCP 47。今は {@code ja} だけ）
 */
public record TotpEnrollmentMailRequest(
        UUID auditRecordId,
        UUID recipientUserId,
        String email,
        UtcInstant enrolledAt,
        boolean reEnrolled,
        String locale) {

    public TotpEnrollmentMailRequest {
        Objects.requireNonNull(auditRecordId, "auditRecordId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(enrolledAt, "enrolledAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** メールアドレスを文字列に出さない（ログや例外の文に載せない）。 */
    @Override
    public String toString() {
        return "TotpEnrollmentMailRequest[auditRecordId=" + auditRecordId + ", recipientUserId=" + recipientUserId
                + ", enrolledAt=" + enrolledAt + ", reEnrolled=" + reEnrolled + ", locale=" + locale + "]";
    }
}

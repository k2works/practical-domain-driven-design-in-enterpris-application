package com.example.cargotracker.notification.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 再設定のメールの要求（通知の公開 API。ADR-017。Bolt 33）。
 *
 * @param requestId 再設定の依頼の ID（通知の {@code event_id}。同じ依頼の 2 回目の要求を見分ける）
 * @param tokenId 再設定のトークンの ID（通知の {@code target_ref}。トークンの値もハッシュも渡さない）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param resetUrl 再設定のリンク（本文にだけ使い、記録しない）
 * @param expiresAt リンクの期限
 * @param locale 文面の言語（BCP 47。今は {@code ja} だけ）
 */
public record PasswordResetMailRequest(
        UUID requestId,
        UUID tokenId,
        UUID recipientUserId,
        String email,
        String resetUrl,
        UtcInstant expiresAt,
        String locale) {

    public PasswordResetMailRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(resetUrl, "resetUrl");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** 再設定のリンクを文字列に出さない（ログや例外の文に載せない）。 */
    @Override
    public String toString() {
        return "PasswordResetMailRequest[requestId=" + requestId + ", tokenId=" + tokenId + ", recipientUserId="
                + recipientUserId + ", expiresAt=" + expiresAt + ", locale=" + locale + "]";
    }
}

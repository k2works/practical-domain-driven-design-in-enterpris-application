package com.example.cargotracker.notification.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 利用者の招待のメールの要求（通知の公開 API。ADR-017。Bolt 38b2b）。
 *
 * @param registrationId 利用者の登録の ID（通知の {@code event_id}。同じ登録の 2 回目の要求を見分ける）
 * @param tokenId 招待のトークンの ID（通知の {@code target_ref}。トークンの値もハッシュも渡さない）
 * @param recipientUserId 宛先の利用者の ID
 * @param email 宛先のメールアドレス
 * @param invitationUrl 初回の設定のリンク（本文にだけ使い、記録しない。最後の段がトークン）
 * @param expiresAt リンクの期限
 * @param locale 文面の言語（BCP 47。今は {@code ja} だけ）
 */
public record UserInvitationMailRequest(
        UUID registrationId,
        UUID tokenId,
        UUID recipientUserId,
        String email,
        String invitationUrl,
        UtcInstant expiresAt,
        String locale) {

    public UserInvitationMailRequest {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(invitationUrl, "invitationUrl");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(locale, "locale");
    }

    /** 初回の設定のリンクとメールアドレスを文字列に出さない（ログや例外の文に載せない）。 */
    @Override
    public String toString() {
        return "UserInvitationMailRequest[registrationId=" + registrationId + ", tokenId=" + tokenId
                + ", recipientUserId=" + recipientUserId + ", expiresAt=" + expiresAt + ", locale=" + locale + "]";
    }
}

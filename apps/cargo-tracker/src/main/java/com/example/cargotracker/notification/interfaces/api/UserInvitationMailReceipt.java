package com.example.cargotracker.notification.interfaces.api;

import java.util.Objects;

/**
 * 利用者の招待のメールの結果（通知の公開 API。ADR-017。Bolt 38b2b）。再設定のメールの {@link PasswordResetMailReceipt} と同じ形。
 */
public sealed interface UserInvitationMailReceipt {

    /** 送った。 */
    record Sent() implements UserInvitationMailReceipt {}

    /** 同じ登録の ID と宛先の通知がすでにある（再配信・同時の要求。送らない）。 */
    record AlreadySent() implements UserInvitationMailReceipt {}

    /**
     * 送れなかった（送信の失敗を記録した）。
     *
     * @param reason 失敗の理由（調査用。利用者には示さない）
     */
    record Failed(String reason) implements UserInvitationMailReceipt {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

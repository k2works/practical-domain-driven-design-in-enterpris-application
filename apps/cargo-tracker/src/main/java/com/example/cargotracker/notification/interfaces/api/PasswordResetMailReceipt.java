package com.example.cargotracker.notification.interfaces.api;

import java.util.Objects;

/**
 * 再設定のメールの結果（通知の公開 API。ADR-017。Bolt 33）。予約の公開 API の {@code TrackingStartNotificationReceipt} と同じ形。
 */
public sealed interface PasswordResetMailReceipt {

    /** 送った。 */
    record Sent() implements PasswordResetMailReceipt {}

    /** 同じ依頼の ID と宛先の通知がすでにある（再配信・同時の要求。送らない）。 */
    record AlreadySent() implements PasswordResetMailReceipt {}

    /**
     * 送れなかった（送信の失敗を記録した）。
     *
     * @param reason 失敗の理由（調査用。利用者には示さない）
     */
    record Failed(String reason) implements PasswordResetMailReceipt {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

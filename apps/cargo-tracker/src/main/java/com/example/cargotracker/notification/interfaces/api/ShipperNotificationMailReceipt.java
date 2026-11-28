package com.example.cargotracker.notification.interfaces.api;

import java.util.Objects;

/**
 * 荷主への業務の通知の結果（通知の公開 API。ADR-017。Bolt 43）。ほかのメールの結果と同じ形。
 */
public sealed interface ShipperNotificationMailReceipt {

    /** 送った。 */
    record Sent() implements ShipperNotificationMailReceipt {}

    /** 同じ契機の ID と宛先の通知がすでにある（再配信・同時の要求。送らない）。 */
    record AlreadySent() implements ShipperNotificationMailReceipt {}

    /**
     * 送れなかった（送信の失敗を記録した）。
     *
     * @param reason 失敗の理由（調査用。利用者には示さない。メールアドレスを含まない）
     */
    record Failed(String reason) implements ShipperNotificationMailReceipt {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

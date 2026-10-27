package com.example.cargotracker.notification.interfaces.api;

import java.util.Objects;

/**
 * 管理者による TOTP の登録の解除のお知らせの結果（通知の公開 API。ADR-017。Bolt 38d）。再設定のメールの {@link PasswordResetMailReceipt} と
 * 同じ形。
 */
public sealed interface TotpResetMailReceipt {

    /** 送った。 */
    record Sent() implements TotpResetMailReceipt {}

    /** 同じ監査記録の ID と宛先の通知がすでにある（再配信・同時の要求。送らない）。 */
    record AlreadySent() implements TotpResetMailReceipt {}

    /**
     * 送れなかった（送信の失敗を記録した）。
     *
     * @param reason 失敗の理由（調査用。利用者には示さない。メールアドレスを含まない）
     */
    record Failed(String reason) implements TotpResetMailReceipt {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

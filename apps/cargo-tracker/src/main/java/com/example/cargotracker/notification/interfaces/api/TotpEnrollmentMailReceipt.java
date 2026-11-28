package com.example.cargotracker.notification.interfaces.api;

import java.util.Objects;

/**
 * 認証アプリの登録と再登録のお知らせの結果（通知の公開 API。ADR-017。Bolt 38d）。再設定のメールの {@link PasswordResetMailReceipt} と
 * 同じ形。
 */
public sealed interface TotpEnrollmentMailReceipt {

    /** 送った。 */
    record Sent() implements TotpEnrollmentMailReceipt {}

    /** 同じ監査記録の ID と宛先の通知がすでにある（再配信・同時の要求。送らない）。 */
    record AlreadySent() implements TotpEnrollmentMailReceipt {}

    /**
     * 送れなかった（送信の失敗を記録した）。
     *
     * @param reason 失敗の理由（調査用。利用者には示さない。メールアドレスを含まない）
     */
    record Failed(String reason) implements TotpEnrollmentMailReceipt {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

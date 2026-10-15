package com.example.cargotracker.notification.application.internal.commandservices;

import java.util.Objects;

/**
 * 再設定のメールの送信の結果（{@link PasswordResetMailService}）。公開 API のアダプターが公開 API の結果の型に変える。
 */
public sealed interface PasswordResetMailOutcome {

    /** 送った。 */
    record Sent() implements PasswordResetMailOutcome {}

    /** 同じ契機と宛先の通知がすでにあり、送らなかった。 */
    record AlreadySent() implements PasswordResetMailOutcome {}

    /**
     * 送れなかった。
     *
     * @param reason 失敗の理由（リンクを含まない）
     */
    record Failed(String reason) implements PasswordResetMailOutcome {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

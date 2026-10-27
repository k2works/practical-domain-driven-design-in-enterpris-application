package com.example.cargotracker.notification.application.internal.commandservices;

import java.util.Objects;

/**
 * メールの送信の結果（{@link MailDelivery}。Bolt 33 の再設定のメールの結果を、Bolt 38b2b で種類によらない名前にした）。公開 API の
 * アダプターが公開 API の結果の型に変える。
 */
public sealed interface MailDeliveryOutcome {

    /** 送った。 */
    record Sent() implements MailDeliveryOutcome {}

    /** 同じ契機と宛先の通知がすでにあり、送らなかった。 */
    record AlreadySent() implements MailDeliveryOutcome {}

    /**
     * 送れなかった。
     *
     * @param reason 失敗の理由（リンクを含まない）
     */
    record Failed(String reason) implements MailDeliveryOutcome {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

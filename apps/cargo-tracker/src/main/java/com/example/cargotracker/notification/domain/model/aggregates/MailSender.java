package com.example.cargotracker.notification.domain.model.aggregates;

/**
 * メールの送信。宛先・件名・本文のメール 1 通を送るポート（ADR-017。Bolt 33）。実装は {@code notification.infrastructure.mail} の SMTP と、
 * dev のログに出す送信で、ドメインはメールの技術を知らない（回復コードのハッシュなどの技術のポートと同じ置き場所）。
 */
@FunctionalInterface
public interface MailSender {

    /**
     * メールを送る。
     *
     * @throws RuntimeException 送れなかったとき（呼ぶ側が失敗として記録する）
     */
    void send(OutgoingMail mail);

    /**
     * {@link #send} が投げた例外が、時間をおけば成功しうる一時の失敗か。分類できない失敗は {@code false} で、再送しない
     * （W10-1 の Bolt 61）。
     */
    default boolean isTransientFailure(RuntimeException failure) {
        return false;
    }
}

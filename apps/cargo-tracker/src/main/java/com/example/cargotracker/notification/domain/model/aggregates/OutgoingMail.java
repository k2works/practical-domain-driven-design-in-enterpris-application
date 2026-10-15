package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

/**
 * 送るメール。1 通の宛先・件名・本文（テキスト）。本文にはリンクが入るので、文字列に出さない。
 *
 * @param to 宛先のメールアドレス
 * @param subject 件名
 * @param body 本文（テキスト）
 */
@ValueObject
public record OutgoingMail(String to, String subject, String body) {

    public OutgoingMail {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String toString() {
        return "OutgoingMail[subject=" + subject + "]";
    }
}

package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP によるメールの送信（ADR-017。Bolt 33）。Spring Boot の {@link JavaMailSender}（{@code spring.mail.*}）で送る。ステージング・本番は
 * SES の SMTP（W10）、統合テストは Mailpit。SMTP の設定がない環境（dev 以外のテストなど）では送らずに失敗にする（呼ぶ側が記録する）。
 */
public final class SmtpMailSender implements MailSender {

    private final ObjectProvider<JavaMailSender> javaMailSender;
    private final String from;

    public SmtpMailSender(ObjectProvider<JavaMailSender> javaMailSender, String from) {
        this.javaMailSender = javaMailSender;
        this.from = Objects.requireNonNull(from, "from");
    }

    @Override
    public void send(OutgoingMail mail) {
        JavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP の設定（spring.mail.host）がない");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.body());
        sender.send(message);
    }
}

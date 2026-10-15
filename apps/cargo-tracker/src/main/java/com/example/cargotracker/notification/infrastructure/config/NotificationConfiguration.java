package com.example.cargotracker.notification.infrastructure.config;

import com.example.cargotracker.notification.application.internal.commandservices.PasswordResetMailService;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.PasswordResetMailTemplate;
import com.example.cargotracker.notification.infrastructure.mail.LoggingMailSender;
import com.example.cargotracker.notification.infrastructure.mail.NotificationMailGuard;
import com.example.cargotracker.notification.infrastructure.mail.SmtpMailSender;
import com.example.cargotracker.notification.infrastructure.mail.ThymeleafPasswordResetMailTemplate;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通知コンテキストの組み立て（ADR-017。Bolt 33）。送信のサービスに、リポジトリとメールの送信と文面のテンプレートをつなぐ。メールの送信は、
 * dev ならログに出す送信、それ以外は SMTP（守りは {@link NotificationMailGuard}）。
 */
@Configuration(proxyBeanMethods = false)
public class NotificationConfiguration {

    /** 送信元のメールアドレスの設定の名前。 */
    static final String MAIL_FROM = "cargotracker.mail.from";

    @Bean
    MailSender notificationMailSender(Environment environment, ObjectProvider<JavaMailSender> javaMailSender) {
        NotificationMailGuard.check(environment);
        if (NotificationMailGuard.logsMail(environment)) {
            return new LoggingMailSender();
        }
        return new SmtpMailSender(javaMailSender, environment.getRequiredProperty(MAIL_FROM));
    }

    @Bean
    PasswordResetMailTemplate passwordResetMailTemplate() {
        return new ThymeleafPasswordResetMailTemplate();
    }

    @Bean
    PasswordResetMailService passwordResetMailService(
            NotificationRepository notifications,
            MailSender notificationMailSender,
            PasswordResetMailTemplate passwordResetMailTemplate,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        return new PasswordResetMailService(
                notifications, notificationMailSender, passwordResetMailTemplate, clock, transactionTemplate);
    }
}

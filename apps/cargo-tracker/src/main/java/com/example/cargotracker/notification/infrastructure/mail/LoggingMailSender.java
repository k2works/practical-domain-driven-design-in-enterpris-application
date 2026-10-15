package com.example.cargotracker.notification.infrastructure.mail;

import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * dev のメールの送信。メールを送らず、宛先・件名・本文をログに出す（ADR-017。Bolt 33）。本文にはリンクが入るので、dev（ローカルとデモ環境）
 * でだけ組み立て、dev の外では起動を止める（{@link NotificationMailGuard}）。
 */
public final class LoggingMailSender implements MailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(OutgoingMail mail) {
        LOG.info(
                "dev のためメールを送らずにログに出す（ADR-017）%n宛先: {}%n件名: {}%n{}".replace("%n", System.lineSeparator()),
                mail.to(),
                mail.subject(),
                mail.body());
    }
}

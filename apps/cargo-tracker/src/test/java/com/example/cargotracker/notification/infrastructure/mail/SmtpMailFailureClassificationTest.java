package com.example.cargotracker.notification.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

/** SMTP の失敗のうち、接続と通信の失敗だけを一時の失敗にする（Bolt 61。分類できない失敗は再送しない）。 */
class SmtpMailFailureClassificationTest {

    @SuppressWarnings("unchecked")
    private final SmtpMailSender sender = new SmtpMailSender(mock(ObjectProvider.class), "no-reply@example.com");

    @Test
    void 接続できない失敗は一時の失敗() {
        var failure = new MailSendException(
                "Mail server connection failed",
                new MessagingException("Couldn't connect", new ConnectException("refused")));

        assertThat(sender.isTransientFailure(failure)).isTrue();
    }

    @Test
    void メッセージごとの失敗に通信の時間切れや名前解決の失敗があれば一時の失敗() {
        assertThat(sender.isTransientFailure(new MailSendException(
                        Map.of("message", new MessagingException("read", new SocketTimeoutException("timeout"))))))
                .isTrue();
        assertThat(sender.isTransientFailure(new MailSendException(
                        Map.of("message", new MessagingException("dns", new UnknownHostException("smtp"))))))
                .isTrue();
    }

    @Test
    void 宛先の不備と認証の失敗と設定の不足と原因のない失敗は一時の失敗にしない() {
        assertThat(sender.isTransientFailure(
                        new MailSendException(Map.of("message", new AddressException("bad address")))))
                .isFalse();
        assertThat(sender.isTransientFailure(new MailAuthenticationException("535")))
                .isFalse();
        assertThat(sender.isTransientFailure(new IllegalStateException("SMTP の設定がない")))
                .isFalse();
        assertThat(sender.isTransientFailure(new MailSendException("failed"))).isFalse();
    }
}

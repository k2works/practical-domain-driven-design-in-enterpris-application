package com.example.cargotracker.notification.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;

/** 通知の公開 API から SMTP（Mailpit）まで（ADR-017、N-INV-01・02。Bolt 33）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PasswordResetMailIntegrationTest {

    @Autowired
    PasswordResetMail passwordResetMail;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    GenericContainer<?> mailpitContainer;

    private static PasswordResetMailRequest request(String email) {
        return new PasswordResetMailRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                "https://cargo.example/password-reset/abc?x=1&y=2",
                new UtcInstant(Instant.parse("2026-10-15T01:30:00Z")),
                "ja");
    }

    private static String uniqueAddress() {
        return "reset-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void 再設定のメールを宛先へ送り送信済みを記録する() {
        String address = uniqueAddress();
        PasswordResetMailRequest request = request(address);

        PasswordResetMailReceipt receipt = passwordResetMail.send(request);

        assertThat(receipt).isEqualTo(new PasswordResetMailReceipt.Sent());
        List<ReceivedMail> mails =
                new MailpitClient(mailpitContainer).awaitMessagesTo(address, 1, Duration.ofSeconds(5));
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.from()).isEqualTo("no-reply@cargo-tracker.example");
            assertThat(mail.to()).containsExactly(address);
            assertThat(mail.subject()).isEqualTo("【cargo-tracker】password の再設定のご案内");
            assertThat(mail.text())
                    .contains("https://cargo.example/password-reset/abc?x=1&y=2")
                    .contains("2026-10-15 10:30 Asia/Tokyo（UTC+09:00）")
                    .doesNotContain("&amp;");
        });
        assertThat(notifications
                        .findByEventIdAndRecipient(request.requestId(), request.recipientUserId())
                        .orElseThrow()
                        .status())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void 同じ依頼の2回目は送らない() {
        String address = uniqueAddress();
        PasswordResetMailRequest request = request(address);
        passwordResetMail.send(request);
        MailpitClient mailpit = new MailpitClient(mailpitContainer);
        assertThat(mailpit.awaitMessagesTo(address, 1, Duration.ofSeconds(5))).hasSize(1);

        PasswordResetMailReceipt receipt = passwordResetMail.send(request);

        assertThat(receipt).isEqualTo(new PasswordResetMailReceipt.AlreadySent());
        assertThat(mailpit.messagesTo(address)).hasSize(1);
    }
}

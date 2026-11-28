package com.example.cargotracker.notification.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.MailpitConfiguration;
import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
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

/** 通知の公開 API（管理者による TOTP の登録の解除のお知らせ）から SMTP（Mailpit）まで（ADR-017、N-INV-01・02。Bolt 38d の行 15）。 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MailpitConfiguration.class})
class TotpResetMailIntegrationTest {

    @Autowired
    TotpResetMail totpResetMail;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    GenericContainer<?> mailpitContainer;

    private static TotpResetMailRequest request(String email) {
        return new TotpResetMailRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                new UtcInstant(Instant.parse("2026-11-09T01:30:00Z")),
                "ja");
    }

    private static String uniqueAddress() {
        return "totp-reset-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void 解除のお知らせを宛先へ送り送信済みを記録する() {
        String address = uniqueAddress();
        TotpResetMailRequest request = request(address);

        assertThat(totpResetMail.send(request)).isEqualTo(new TotpResetMailReceipt.Sent());

        List<ReceivedMail> mails =
                new MailpitClient(mailpitContainer).awaitMessagesTo(address, 1, Duration.ofSeconds(5));
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.to()).containsExactly(address);
            assertThat(mail.subject()).isEqualTo("【cargo-tracker】認証アプリの登録の解除のご案内");
            assertThat(mail.text()).contains("システム管理者が 2026-11-09 10:30 Asia/Tokyo（UTC+09:00） に解除しました。");
        });
        Notification recorded = notifications
                .findByEventIdAndRecipient(request.auditRecordId(), request.recipientUserId())
                .orElseThrow();
        assertThat(recorded.kind()).isEqualTo(NotificationKind.TOTP_RESET_BY_ADMIN);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(recorded.targetRef()).isEqualTo(request.auditRecordId().toString());
    }

    @Test
    void 同じ解除の2回目は送らない() {
        String address = uniqueAddress();
        TotpResetMailRequest request = request(address);
        totpResetMail.send(request);
        MailpitClient mailpit = new MailpitClient(mailpitContainer);
        assertThat(mailpit.awaitMessagesTo(address, 1, Duration.ofSeconds(5))).hasSize(1);

        assertThat(totpResetMail.send(request)).isEqualTo(new TotpResetMailReceipt.AlreadySent());
        assertThat(mailpit.messagesTo(address)).hasSize(1);
    }
}

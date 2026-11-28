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

/** 通知の公開 API（認証アプリの登録と再登録のお知らせ）から SMTP（Mailpit）まで（ADR-017、N-INV-01・02。Bolt 38d の行 15）。 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MailpitConfiguration.class})
class TotpEnrollmentMailIntegrationTest {

    @Autowired
    TotpEnrollmentMail totpEnrollmentMail;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    GenericContainer<?> mailpitContainer;

    private static TotpEnrollmentMailRequest request(String email, boolean reEnrolled) {
        return new TotpEnrollmentMailRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                new UtcInstant(Instant.parse("2026-11-09T01:30:00Z")),
                reEnrolled,
                "ja");
    }

    private static String uniqueAddress() {
        return "totp-enrollment-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void 登録のお知らせを宛先へ送り送信済みを記録する() {
        String address = uniqueAddress();
        TotpEnrollmentMailRequest request = request(address, false);

        assertThat(totpEnrollmentMail.send(request)).isEqualTo(new TotpEnrollmentMailReceipt.Sent());

        List<ReceivedMail> mails =
                new MailpitClient(mailpitContainer).awaitMessagesTo(address, 1, Duration.ofSeconds(5));
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("【cargo-tracker】認証アプリの登録のご案内");
            assertThat(mail.text()).contains("認証アプリが、2026-11-09 10:30 Asia/Tokyo（UTC+09:00） に登録されました。");
        });
        Notification recorded = notifications
                .findByEventIdAndRecipient(request.auditRecordId(), request.recipientUserId())
                .orElseThrow();
        assertThat(recorded.kind()).isEqualTo(NotificationKind.TOTP_ENROLLED);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void 再登録のお知らせを送り2回目は送らない() {
        String address = uniqueAddress();
        TotpEnrollmentMailRequest request = request(address, true);

        assertThat(totpEnrollmentMail.send(request)).isEqualTo(new TotpEnrollmentMailReceipt.Sent());
        MailpitClient mailpit = new MailpitClient(mailpitContainer);
        assertThat(mailpit.awaitMessagesTo(address, 1, Duration.ofSeconds(5)))
                .singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("【cargo-tracker】認証アプリの再登録のご案内"));

        assertThat(totpEnrollmentMail.send(request)).isEqualTo(new TotpEnrollmentMailReceipt.AlreadySent());
        assertThat(mailpit.messagesTo(address)).hasSize(1);
        assertThat(notifications
                        .findByEventIdAndRecipient(request.auditRecordId(), request.recipientUserId())
                        .orElseThrow()
                        .kind())
                .isEqualTo(NotificationKind.TOTP_RE_ENROLLED);
    }
}

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

/** 通知の公開 API（利用者の招待のメール）から SMTP（Mailpit）まで（ADR-017、N-INV-01・02。Bolt 38b2b）。 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MailpitConfiguration.class})
class UserInvitationMailIntegrationTest {

    @Autowired
    UserInvitationMail userInvitationMail;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    GenericContainer<?> mailpitContainer;

    private static UserInvitationMailRequest request(String email) {
        return new UserInvitationMailRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                "https://cargo.example/password-reset/invitation?x=1&y=2",
                new UtcInstant(Instant.parse("2026-10-27T01:30:00Z")),
                "ja");
    }

    private static String uniqueAddress() {
        return "invitation-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void 利用者の招待のメールを宛先へ送り送信済みを記録する() {
        // 行 1
        String address = uniqueAddress();
        UserInvitationMailRequest request = request(address);

        assertThat(userInvitationMail.send(request)).isEqualTo(new UserInvitationMailReceipt.Sent());

        List<ReceivedMail> mails =
                new MailpitClient(mailpitContainer).awaitMessagesTo(address, 1, Duration.ofSeconds(5));
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.to()).containsExactly(address);
            assertThat(mail.subject()).isEqualTo("【cargo-tracker】利用者の登録のご案内");
            assertThat(mail.text())
                    .contains("https://cargo.example/password-reset/invitation?x=1&y=2")
                    .contains("2026-10-27 10:30 Asia/Tokyo（UTC+09:00）")
                    .doesNotContain("&amp;");
        });
        Notification recorded = notifications
                .findByEventIdAndRecipient(request.registrationId(), request.recipientUserId())
                .orElseThrow();
        assertThat(recorded.kind()).isEqualTo(NotificationKind.USER_INVITATION);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(recorded.targetRef()).isEqualTo(request.tokenId().toString());
    }

    @Test
    void 同じ登録の2回目は送らない() {
        // 行 2
        String address = uniqueAddress();
        UserInvitationMailRequest request = request(address);
        userInvitationMail.send(request);
        MailpitClient mailpit = new MailpitClient(mailpitContainer);
        assertThat(mailpit.awaitMessagesTo(address, 1, Duration.ofSeconds(5))).hasSize(1);

        assertThat(userInvitationMail.send(request)).isEqualTo(new UserInvitationMailReceipt.AlreadySent());
        assertThat(mailpit.messagesTo(address)).hasSize(1);
    }
}

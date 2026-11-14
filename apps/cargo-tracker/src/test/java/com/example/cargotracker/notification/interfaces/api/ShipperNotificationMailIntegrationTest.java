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
import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;

/**
 * 通知の公開 API（荷主への業務の通知）から SMTP（Mailpit）まで（US-22 AC3・AC4、N-INV-01・02。Bolt 43）。本文は件名と画面への導線だけで、料金・契約条件・
 * 書類・他の予約の情報を含まない。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MailpitConfiguration.class})
class ShipperNotificationMailIntegrationTest {

    private static final String LINK = "https://tracker.example.com/customer/transport-requests/TR-2026-0001";

    @Autowired
    ShipperNotificationMail shipperNotificationMail;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    GenericContainer<?> mailpitContainer;

    private static ShipperNotificationMailRequest request(String email, ShipperNotificationKind kind) {
        return new ShipperNotificationMailRequest(
                UUID.randomUUID(), UUID.randomUUID(), email, kind, "TR-2026-0001", LINK, "ja");
    }

    private static String uniqueAddress() {
        return "shipper-notification-" + UUID.randomUUID() + "@example.com";
    }

    @ParameterizedTest
    @EnumSource(ShipperNotificationKind.class)
    void 種類ごとに件名と画面への導線だけを送り送信済みを記録する(ShipperNotificationKind kind) {
        String address = uniqueAddress();
        ShipperNotificationMailRequest request = request(address, kind);

        assertThat(shipperNotificationMail.send(request)).isEqualTo(new ShipperNotificationMailReceipt.Sent());

        List<ReceivedMail> mails =
                new MailpitClient(mailpitContainer).awaitMessagesTo(address, 1, Duration.ofSeconds(5));
        assertThat(mails).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).startsWith("【cargo-tracker】");
            assertThat(mail.text()).contains(LINK);
            // 料金・契約条件・書類・他の予約の情報を含まない（AC3、N-INV-01）
            assertThat(mail.text()).doesNotContain("円").doesNotContain("料金").doesNotContain("契約");
        });
        Notification recorded = notifications
                .findByEventIdAndRecipient(request.eventId(), request.recipientUserId())
                .orElseThrow();
        assertThat(recorded.kind()).isEqualTo(NotificationKind.valueOf(kind.name()));
        assertThat(recorded.targetRef()).isEqualTo("TR-2026-0001");
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void 同じ契機と宛先の2回目は送らない() {
        String address = uniqueAddress();
        ShipperNotificationMailRequest request = request(address, ShipperNotificationKind.QUOTATION_PRESENTED);

        assertThat(shipperNotificationMail.send(request)).isEqualTo(new ShipperNotificationMailReceipt.Sent());
        assertThat(shipperNotificationMail.send(request)).isEqualTo(new ShipperNotificationMailReceipt.AlreadySent());

        assertThat(new MailpitClient(mailpitContainer).messagesTo(address)).hasSize(1);
    }
}

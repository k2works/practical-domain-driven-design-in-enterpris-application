package com.example.cargotracker.notification.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.notification.application.internal.commands.SendShipperNotificationMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.aggregates.ShipperNotificationMailTemplate;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 失敗した荷主への業務の通知の再送のサービス（W10-1 の Bolt 61）。 */
class ShipperNotificationRetryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
    private static final String LINK = "https://example.com/customer/bookings/BK-1";

    /** 失敗の一時・恒久を切り替えられる送信の偽物。 */
    private static final class FlakySender implements MailSender {
        private final List<OutgoingMail> sent = new ArrayList<>();
        private RuntimeException failure;
        private boolean transientFailure;

        @Override
        public void send(OutgoingMail mail) {
            if (failure != null) {
                throw failure;
            }
            sent.add(mail);
        }

        @Override
        public boolean isTransientFailure(RuntimeException e) {
            return transientFailure;
        }
    }

    private final NotificationTestDoubles.InMemoryNotifications notifications =
            new NotificationTestDoubles.InMemoryNotifications();
    private final FlakySender sender = new FlakySender();
    private final ShipperNotificationMailTemplate template =
            (to, kind, targetRef, link, locale) -> new OutgoingMail(to, kind.name() + " " + targetRef, link);

    private ShipperNotificationRetryService retryServiceAt(Instant now) {
        return new ShipperNotificationRetryService(
                notifications,
                sender,
                template,
                Clock.fixed(now, ZoneOffset.UTC),
                new NotificationTestDoubles.ImmediateTransactions());
    }

    private SendShipperNotificationMailCommand command() {
        return new SendShipperNotificationMailCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "shipper@example.com",
                NotificationKind.BOOKING_CONFIRMED,
                "BK-1",
                LINK,
                "ja");
    }

    private Notification firstSendFails(boolean transientFailure) {
        SendShipperNotificationMailCommand command = command();
        sender.failure = new IllegalStateException("送れない shipper@example.com");
        sender.transientFailure = transientFailure;
        new ShipperNotificationMailService(
                        notifications,
                        sender,
                        template,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        new NotificationTestDoubles.ImmediateTransactions())
                .send(command);
        return notifications.find(command.eventId(), command.recipientUserId()).orElseThrow();
    }

    private Notification reload(Notification notification) {
        return notifications
                .find(notification.eventId(), notification.recipientUserId())
                .orElseThrow();
    }

    @Test
    void 一時の失敗の通知は次の実行で同じ宛先と内容で送り直して送信済みになる() {
        Notification failed = firstSendFails(true);
        sender.failure = null;

        int attempted = retryServiceAt(NOW.plus(Duration.ofMinutes(10))).retryDue();

        assertThat(attempted).isEqualTo(1);
        assertThat(sender.sent)
                .containsExactly(new OutgoingMail("shipper@example.com", "BOOKING_CONFIRMED BK-1", LINK));
        assertThat(reload(failed).status()).isEqualTo(NotificationStatus.SENT);
        assertThat(reload(failed).retryCount()).isEqualTo(1);
    }

    @Test
    void 一時でない失敗の通知は送り直さない() {
        Notification failed = firstSendFails(false);
        sender.failure = null;

        assertThat(retryServiceAt(NOW.plus(Duration.ofMinutes(10))).retryDue()).isZero();

        assertThat(sender.sent).isEmpty();
        assertThat(reload(failed).status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void 失敗し続ける通知は3回で打ち切り理由に宛先を残さない() {
        Notification failed = firstSendFails(true);
        ShipperNotificationRetryService service = retryServiceAt(NOW.plus(Duration.ofMinutes(10)));

        List<Integer> attempts =
                List.of(service.retryDue(), service.retryDue(), service.retryDue(), service.retryDue());

        assertThat(attempts).containsExactly(1, 1, 1, 0);
        assertThat(reload(failed)).satisfies(last -> {
            assertThat(last.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(last.retryCount()).isEqualTo(3);
            assertThat(last.retryable()).isFalse();
            assertThat(last.failureReason().orElseThrow()).doesNotContain("shipper@example.com");
        });
    }

    @Test
    void ほかの実行が先に引き取った通知は送らない() {
        firstSendFails(true);
        sender.failure = null;
        notifications.rejectClaim = true;

        assertThat(retryServiceAt(NOW.plus(Duration.ofMinutes(10))).retryDue()).isZero();

        assertThat(sender.sent).isEmpty();
    }

    @Test
    void 作成から24時間以上たった通知は送り直さない() {
        firstSendFails(true);
        sender.failure = null;

        assertThat(retryServiceAt(NOW.plus(Duration.ofHours(24))).retryDue()).isZero();
    }
}

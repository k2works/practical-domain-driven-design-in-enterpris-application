package com.example.cargotracker.notification.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** メールの送り方（記録・冪等・失敗・伏せ字。Bolt 38d でリンクのないメールに広げた）。 */
class MailDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:30:00Z");
    private static final String ADDRESS = "hanako@example.com";

    private final NotificationTestDoubles.InMemoryNotifications notifications =
            new NotificationTestDoubles.InMemoryNotifications();
    private final NotificationTestDoubles.RecordingSender sender = new NotificationTestDoubles.RecordingSender();
    private final MailDelivery delivery = new MailDelivery(
            notifications,
            sender,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new NotificationTestDoubles.ImmediateTransactions());

    private static Notification pending() {
        return Notification.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ADDRESS,
                NotificationKind.TOTP_RESET_BY_ADMIN,
                UUID.randomUUID().toString(),
                "ja",
                new UtcInstant(NOW));
    }

    private Notification recorded(Notification pending) {
        return notifications.find(pending.eventId(), pending.recipientUserId()).orElseThrow();
    }

    @Test
    void リンクのないメールを送り送信済みを記録する() {
        // 行 13
        Notification pending = pending();

        assertThat(delivery.deliver(pending, () -> new OutgoingMail(ADDRESS, "件名", "本文"), List.of()))
                .isEqualTo(new MailDeliveryOutcome.Sent());

        assertThat(sender.sent)
                .singleElement()
                .satisfies(mail -> assertThat(mail.to()).isEqualTo(ADDRESS));
        Notification recorded = recorded(pending);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(recorded.sentAt()).contains(new UtcInstant(NOW));
    }

    @Test
    void 同じ契機と宛先の2回目は送らない() {
        // 行 6・13（N-INV-02）
        Notification pending = pending();
        delivery.deliver(pending, () -> new OutgoingMail(ADDRESS, "件名", "本文"), List.of());

        assertThat(delivery.deliver(pending, () -> new OutgoingMail(ADDRESS, "件名", "本文"), List.of()))
                .isEqualTo(new MailDeliveryOutcome.AlreadySent());
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void リンクのないメールを送れなければメールアドレスを伏せて失敗を記録し例外にしない() {
        // 行 7・13（変異 V7。アドレスは伏せる文字列を渡さなくても、メールアドレスの形の文字列として伏せる）
        Notification pending = pending();
        sender.failure = new IllegalStateException("550 宛先 " + ADDRESS + " を受け付けない");

        assertThat(delivery.deliver(pending, () -> new OutgoingMail(ADDRESS, "件名", "本文"), List.of()))
                .isEqualTo(new MailDeliveryOutcome.Failed("IllegalStateException: 550 宛先 [メールアドレス] を受け付けない"));

        Notification recorded = recorded(pending);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(recorded.failureReason()).hasValue("IllegalStateException: 550 宛先 [メールアドレス] を受け付けない");
    }

    @Test
    void 文面を組み立てられなければ送らずに失敗を記録する() {
        // 行 7・13
        Notification pending = pending();

        MailDeliveryOutcome outcome = delivery.deliver(
                pending,
                () -> {
                    throw new IllegalStateException("テンプレートを読めない");
                },
                List.of());

        assertThat(outcome).isEqualTo(new MailDeliveryOutcome.Failed("IllegalStateException: テンプレートを読めない"));
        assertThat(sender.sent).isEmpty();
        assertThat(recorded(pending).status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void リンクのあるメールはリンクとその最後の段とメールアドレスを伏せる() {
        // 行 13（再設定と招待は今のまま）
        Notification pending = pending();
        String link = "https://cargo.example/password-reset/the-token";
        sender.failure = new IllegalStateException("550 <" + ADDRESS + ">: " + link + " token the-token");
        List<MailDelivery.Mask> masks = MailDelivery.linkMasks(link);

        assertThat(delivery.deliver(pending, () -> new OutgoingMail(ADDRESS, "件名", "本文 " + link), masks))
                .isEqualTo(new MailDeliveryOutcome.Failed("IllegalStateException: 550 <[メールアドレス]>: [リンク] token [リンク]"));
    }

    @Test
    void 空の文字列は伏せる文字列にできない() {
        // 行 13（空を伏せると、理由の文の 1 字ごとに置き換えの文字列が入る）
        assertThatThrownBy(() -> new MailDelivery.Mask("", "[リンク]")).isInstanceOf(IllegalArgumentException.class);
    }
}

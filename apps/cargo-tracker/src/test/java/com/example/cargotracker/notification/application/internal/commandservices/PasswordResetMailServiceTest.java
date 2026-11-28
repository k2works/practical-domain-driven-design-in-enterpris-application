package com.example.cargotracker.notification.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.notification.application.internal.commands.SendPasswordResetMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 再設定のメールの送信のサービス（ADR-017。Bolt 33）。 */
class PasswordResetMailServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T01:00:00Z");

    private final NotificationTestDoubles.InMemoryNotifications notifications =
            new NotificationTestDoubles.InMemoryNotifications();
    private final NotificationTestDoubles.RecordingSender sender = new NotificationTestDoubles.RecordingSender();
    private final PasswordResetMailService service = new PasswordResetMailService(
            notifications,
            sender,
            (to, resetUrl, expiresAt, locale) -> new OutgoingMail(to, "件名", "本文 " + resetUrl),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new NotificationTestDoubles.ImmediateTransactions());

    private static SendPasswordResetMailCommand command(UUID requestId) {
        return new SendPasswordResetMailCommand(
                requestId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "shipper@example.com",
                "https://cargo.example/password-reset/secret-token",
                new UtcInstant(NOW.plusSeconds(1800)),
                "ja");
    }

    @Test
    void 送れたら送信済みの通知を残し本文とリンクは残さない() {
        SendPasswordResetMailCommand command = command(UUID.randomUUID());

        MailDeliveryOutcome outcome = service.send(command);

        assertThat(outcome).isEqualTo(new MailDeliveryOutcome.Sent());
        assertThat(sender.sent).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo("shipper@example.com");
            assertThat(mail.body()).contains(command.resetUrl());
        });
        Notification recorded = notifications
                .find(command.requestId(), command.recipientUserId())
                .orElseThrow();
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(recorded.sentAt()).contains(new UtcInstant(NOW));
        assertThat(recorded.kind()).isEqualTo(NotificationKind.PASSWORD_RESET);
        assertThat(recorded.targetRef()).isEqualTo(command.tokenId().toString());
    }

    @Test
    void 同じ依頼の2回目は送らずにすでに送ったと返す() {
        SendPasswordResetMailCommand first = command(UUID.randomUUID());
        service.send(first);
        assertThat(sender.sent).hasSize(1);

        MailDeliveryOutcome outcome = service.send(first);

        assertThat(outcome).isEqualTo(new MailDeliveryOutcome.AlreadySent());
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void 送信に失敗したら例外にせず失敗の理由を残す() {
        sender.failure = new IllegalStateException("接続できない");
        SendPasswordResetMailCommand command = command(UUID.randomUUID());

        MailDeliveryOutcome outcome = service.send(command);

        assertThat(outcome).isInstanceOf(MailDeliveryOutcome.Failed.class);
        Notification recorded = notifications
                .find(command.requestId(), command.recipientUserId())
                .orElseThrow();
        assertThat(recorded.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(recorded.failureReason())
                .hasValueSatisfying(reason ->
                        assertThat(reason).contains("IllegalStateException").contains("接続できない"));
    }

    @Test
    void 失敗の理由にリンクを残さない() {
        SendPasswordResetMailCommand command = command(UUID.randomUUID());
        sender.failure = new IllegalStateException("送れない: " + command.resetUrl());

        service.send(command);

        assertThat(notifications
                        .find(command.requestId(), command.recipientUserId())
                        .orElseThrow()
                        .failureReason())
                .hasValueSatisfying(reason ->
                        assertThat(reason).doesNotContain("secret-token").contains("[リンク]"));
    }

    @Test
    void 文面を組み立てられなくても例外にせず失敗の理由を残す() {
        // 行 4（Bolt 38b2b。文面は記録の後に送信と同じ try の中で作る）
        PasswordResetMailService failing = new PasswordResetMailService(
                notifications,
                sender,
                (to, resetUrl, expiresAt, locale) -> {
                    throw new IllegalStateException("文面の誤り " + resetUrl);
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                new NotificationTestDoubles.ImmediateTransactions());
        SendPasswordResetMailCommand command = command(UUID.randomUUID());

        assertThat(failing.send(command)).isInstanceOf(MailDeliveryOutcome.Failed.class);
        assertThat(sender.sent).isEmpty();
        assertThat(notifications
                        .find(command.requestId(), command.recipientUserId())
                        .orElseThrow()
                        .failureReason())
                .hasValueSatisfying(
                        reason -> assertThat(reason).contains("文面の誤り").doesNotContain("secret-token"));
    }

    @Test
    void 失敗の理由にトークンとメールアドレスを残さない() {
        SendPasswordResetMailCommand command = command(UUID.randomUUID());
        sender.failure = new IllegalStateException(
                "550 <shipper@example.com>: Recipient rejected; token secret-token; to=other.user+tag@example.co.jp");

        service.send(command);

        assertThat(notifications
                        .find(command.requestId(), command.recipientUserId())
                        .orElseThrow()
                        .failureReason())
                .hasValueSatisfying(reason -> assertThat(reason)
                        .startsWith("IllegalStateException: 550")
                        .doesNotContain("secret-token")
                        .doesNotContain("shipper@example.com")
                        .doesNotContain("other.user+tag@example.co.jp")
                        .contains("[メールアドレス]"));
    }

    @Test
    void 送信の結果を書けなくても例外にしない() {
        SendPasswordResetMailCommand command = command(UUID.randomUUID());
        notifications.rejectOutcome = true;

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.Sent());
        assertThat(sender.sent).hasSize(1);
    }
}

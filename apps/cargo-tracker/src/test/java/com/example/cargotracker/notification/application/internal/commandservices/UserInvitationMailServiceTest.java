package com.example.cargotracker.notification.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.notification.application.internal.commands.SendUserInvitationMailCommand;
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

/** 利用者の招待のメールの送信のサービス（ADR-017。Bolt 38b2b）。 */
class UserInvitationMailServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T01:00:00Z");

    private final NotificationTestDoubles.InMemoryNotifications notifications =
            new NotificationTestDoubles.InMemoryNotifications();
    private final NotificationTestDoubles.RecordingSender sender = new NotificationTestDoubles.RecordingSender();
    private final UserInvitationMailService service = new UserInvitationMailService(
            notifications,
            sender,
            (to, invitationUrl, expiresAt, locale) -> new OutgoingMail(to, "件名", "本文 " + invitationUrl),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new NotificationTestDoubles.ImmediateTransactions());

    private static SendUserInvitationMailCommand command() {
        return new SendUserInvitationMailCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hanako@example.com",
                "https://cargo.example/password-reset/invitation-token",
                new UtcInstant(NOW.plusSeconds(86_400)),
                "ja");
    }

    private Notification recorded(SendUserInvitationMailCommand command) {
        return notifications
                .find(command.registrationId(), command.recipientUserId())
                .orElseThrow();
    }

    @Test
    void 送れたら利用者の招待として送信済みの通知を残す() {
        // 行 1 の記録
        SendUserInvitationMailCommand command = command();

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.Sent());

        assertThat(sender.sent)
                .singleElement()
                .satisfies(mail -> assertThat(mail.body()).contains(command.invitationUrl()));
        Notification recorded = recorded(command);
        assertThat(recorded.kind()).isEqualTo(NotificationKind.USER_INVITATION);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(recorded.targetRef()).isEqualTo(command.tokenId().toString());
    }

    @Test
    void 同じ登録の2回目は送らない() {
        // 行 2
        SendUserInvitationMailCommand command = command();
        service.send(command);

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.AlreadySent());
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void 文面を組み立てられなくても例外にせず送らずに失敗を残す() {
        // 行 3（文面の組み立ての失敗。変異 V7）
        SendUserInvitationMailCommand command = command();
        UserInvitationMailService failing = new UserInvitationMailService(
                notifications,
                sender,
                (to, invitationUrl, expiresAt, locale) -> {
                    throw new IllegalStateException("テンプレートを読めない: " + invitationUrl);
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                new NotificationTestDoubles.ImmediateTransactions());

        assertThat(failing.send(command)).isInstanceOf(MailDeliveryOutcome.Failed.class);

        assertThat(sender.sent).isEmpty();
        Notification recorded = recorded(command);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(recorded.failureReason()).contains("IllegalStateException: テンプレートを読めない: [リンク]");
    }

    @Test
    void 送れなかったらリンクとトークンとメールアドレスを伏せて失敗を残す() {
        // 行 3
        SendUserInvitationMailCommand command = command();
        sender.failure = new IllegalStateException(
                "550 <hanako@example.com>: " + command.invitationUrl() + " token invitation-token");

        assertThat(service.send(command)).isInstanceOf(MailDeliveryOutcome.Failed.class);

        Notification recorded = recorded(command);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.FAILED);
        // リンクはトークンの手前の段も残さず、全体を「[リンク]」にする（変異 V3a）
        assertThat(recorded.failureReason()).contains("IllegalStateException: 550 <[メールアドレス]>: [リンク] token [リンク]");
    }
}

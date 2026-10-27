package com.example.cargotracker.notification.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.notification.application.internal.commands.SendTotpResetMailCommand;
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

/** 管理者による TOTP の登録の解除のお知らせの送信のサービス（ADR-017。Bolt 38d）。 */
class TotpResetMailServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:35:00Z");
    private static final UtcInstant RESET_AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));

    private final NotificationTestDoubles.InMemoryNotifications notifications =
            new NotificationTestDoubles.InMemoryNotifications();
    private final NotificationTestDoubles.RecordingSender sender = new NotificationTestDoubles.RecordingSender();
    private final TotpResetMailService service = new TotpResetMailService(
            notifications,
            sender,
            (to, resetAt, locale) -> new OutgoingMail(to, "件名", "本文 " + resetAt.instant()),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new NotificationTestDoubles.ImmediateTransactions());

    private static SendTotpResetMailCommand command() {
        return new SendTotpResetMailCommand(UUID.randomUUID(), UUID.randomUUID(), "hanako@example.com", RESET_AT, "ja");
    }

    private Notification recorded(SendTotpResetMailCommand command) {
        return notifications
                .find(command.auditRecordId(), command.recipientUserId())
                .orElseThrow();
    }

    @Test
    void 送れたら監査記録のIDを契機と対象にして送信済みの通知を残し解除の日時を本文に渡す() {
        // 行 1・14 の記録（本文の日時は解除の時刻で、送った時刻ではない）
        SendTotpResetMailCommand command = command();

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.Sent());

        assertThat(sender.sent)
                .singleElement()
                .satisfies(mail -> assertThat(mail.body()).isEqualTo("本文 " + RESET_AT.instant()));
        Notification recorded = recorded(command);
        assertThat(recorded.kind()).isEqualTo(NotificationKind.TOTP_RESET_BY_ADMIN);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(recorded.eventId()).isEqualTo(command.auditRecordId());
        assertThat(recorded.targetRef()).isEqualTo(command.auditRecordId().toString());
    }

    @Test
    void 同じ解除の2回目は送らない() {
        // 行 6
        SendTotpResetMailCommand command = command();
        service.send(command);

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.AlreadySent());
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void 送れなかったらメールアドレスを伏せて失敗を残す() {
        // 行 7
        SendTotpResetMailCommand command = command();
        sender.failure = new IllegalStateException("550 <hanako@example.com> を受け付けない");

        assertThat(service.send(command))
                .isEqualTo(new MailDeliveryOutcome.Failed("IllegalStateException: 550 <[メールアドレス]> を受け付けない"));

        Notification recorded = recorded(command);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(recorded.failureReason()).hasValue("IllegalStateException: 550 <[メールアドレス]> を受け付けない");
    }
}

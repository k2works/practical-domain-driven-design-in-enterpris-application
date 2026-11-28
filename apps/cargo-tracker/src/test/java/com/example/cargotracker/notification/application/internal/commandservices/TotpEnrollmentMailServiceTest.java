package com.example.cargotracker.notification.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.notification.application.internal.commands.SendTotpEnrollmentMailCommand;
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

/** 認証アプリの登録と再登録のお知らせの送信のサービス（ADR-017。Bolt 38d）。 */
class TotpEnrollmentMailServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T01:35:00Z");
    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-11-09T01:30:00Z"));

    private final NotificationTestDoubles.InMemoryNotifications notifications =
            new NotificationTestDoubles.InMemoryNotifications();
    private final NotificationTestDoubles.RecordingSender sender = new NotificationTestDoubles.RecordingSender();
    private final TotpEnrollmentMailService service = new TotpEnrollmentMailService(
            notifications,
            sender,
            (to, at, reEnrolled, locale) -> new OutgoingMail(to, reEnrolled ? "再登録" : "登録", "本文 " + at.instant()),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new NotificationTestDoubles.ImmediateTransactions());

    private static SendTotpEnrollmentMailCommand command(boolean reEnrolled) {
        return new SendTotpEnrollmentMailCommand(
                UUID.randomUUID(), UUID.randomUUID(), "hanako@example.com", AT, reEnrolled, "ja");
    }

    private Notification recorded(SendTotpEnrollmentMailCommand command) {
        return notifications
                .find(command.auditRecordId(), command.recipientUserId())
                .orElseThrow();
    }

    @Test
    void 登録のお知らせは登録の種類と監査記録のIDで送信済みを残し登録の日時を本文に渡す() {
        // 行 3・14
        SendTotpEnrollmentMailCommand command = command(false);

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.Sent());

        assertThat(sender.sent).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("登録");
            assertThat(mail.body()).isEqualTo("本文 " + AT.instant());
        });
        Notification recorded = recorded(command);
        assertThat(recorded.kind()).isEqualTo(NotificationKind.TOTP_ENROLLED);
        assertThat(recorded.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(recorded.eventId()).isEqualTo(command.auditRecordId());
        assertThat(recorded.targetRef()).isEqualTo(command.auditRecordId().toString());
    }

    @Test
    void 再登録のお知らせは再登録の種類と雛形で送る() {
        // 行 2
        SendTotpEnrollmentMailCommand command = command(true);

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.Sent());

        assertThat(sender.sent)
                .singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("再登録"));
        assertThat(recorded(command).kind()).isEqualTo(NotificationKind.TOTP_RE_ENROLLED);
    }

    @Test
    void 同じ登録の2回目は送らない() {
        // 行 6
        SendTotpEnrollmentMailCommand command = command(false);
        service.send(command);

        assertThat(service.send(command)).isEqualTo(new MailDeliveryOutcome.AlreadySent());
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void 送れなかったらメールアドレスを伏せて失敗を残す() {
        // 行 7
        SendTotpEnrollmentMailCommand command = command(true);
        sender.failure = new IllegalStateException("550 <hanako@example.com> を受け付けない");

        assertThat(service.send(command))
                .isEqualTo(new MailDeliveryOutcome.Failed("IllegalStateException: 550 <[メールアドレス]> を受け付けない"));
        assertThat(recorded(command).status()).isEqualTo(NotificationStatus.FAILED);
    }
}

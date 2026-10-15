package com.example.cargotracker.notification.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.notification.application.internal.commands.SendPasswordResetMailCommand;
import com.example.cargotracker.notification.domain.model.aggregates.MailSender;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.aggregates.OutgoingMail;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 再設定のメールの送信のサービス（ADR-017。Bolt 33）。 */
class PasswordResetMailServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T01:00:00Z");

    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final RecordingSender sender = new RecordingSender();
    private final PasswordResetMailService service = new PasswordResetMailService(
            notifications,
            sender,
            (to, resetUrl, expiresAt, locale) -> new OutgoingMail(to, "件名", "本文 " + resetUrl),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new ImmediateTransactions());

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

        PasswordResetMailOutcome outcome = service.send(command);

        assertThat(outcome).isEqualTo(new PasswordResetMailOutcome.Sent());
        assertThat(sender.sent).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo("shipper@example.com");
            assertThat(mail.body()).contains(command.resetUrl());
        });
        Notification recorded = notifications.find(command).orElseThrow();
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

        PasswordResetMailOutcome outcome = service.send(first);

        assertThat(outcome).isEqualTo(new PasswordResetMailOutcome.AlreadySent());
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void 送信に失敗したら例外にせず失敗の理由を残す() {
        sender.failure = new IllegalStateException("接続できない");
        SendPasswordResetMailCommand command = command(UUID.randomUUID());

        PasswordResetMailOutcome outcome = service.send(command);

        assertThat(outcome).isInstanceOf(PasswordResetMailOutcome.Failed.class);
        Notification recorded = notifications.find(command).orElseThrow();
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

        assertThat(notifications.find(command).orElseThrow().failureReason())
                .hasValueSatisfying(reason -> assertThat(reason).doesNotContain("secret-token"));
    }

    @Test
    void 失敗の理由にトークンとメールアドレスを残さない() {
        SendPasswordResetMailCommand command = command(UUID.randomUUID());
        sender.failure = new IllegalStateException(
                "550 <shipper@example.com>: Recipient rejected; token secret-token; to=other.user+tag@example.co.jp");

        service.send(command);

        assertThat(notifications.find(command).orElseThrow().failureReason())
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

        assertThat(service.send(command)).isEqualTo(new PasswordResetMailOutcome.Sent());
        assertThat(sender.sent).hasSize(1);
    }

    private static final class RecordingSender implements MailSender {
        private final List<OutgoingMail> sent = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void send(OutgoingMail mail) {
            if (failure != null) {
                throw failure;
            }
            sent.add(mail);
        }
    }

    private static final class InMemoryNotifications implements NotificationRepository {
        private final Map<String, Notification> rows = new HashMap<>();
        private boolean rejectOutcome;

        private static String key(UUID eventId, UUID recipient) {
            return eventId + "/" + recipient;
        }

        Optional<Notification> find(SendPasswordResetMailCommand command) {
            return findByEventIdAndRecipient(command.requestId(), command.recipientUserId());
        }

        @Override
        public boolean addIfAbsent(Notification notification) {
            return rows.putIfAbsent(key(notification.eventId(), notification.recipientUserId()), notification) == null;
        }

        @Override
        public boolean recordOutcome(Notification notification) {
            String key = key(notification.eventId(), notification.recipientUserId());
            Notification current = rows.get(key);
            if (rejectOutcome || current == null || current.status() != NotificationStatus.PENDING) {
                return false;
            }
            rows.put(key, notification);
            return true;
        }

        @Override
        public Optional<Notification> findByEventIdAndRecipient(UUID eventId, UUID recipientUserId) {
            return Optional.ofNullable(rows.get(key(eventId, recipientUserId)));
        }
    }

    /** トランザクションを開かずにそのまま呼ぶ（単体テスト用）。 */
    private static final class ImmediateTransactions extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}

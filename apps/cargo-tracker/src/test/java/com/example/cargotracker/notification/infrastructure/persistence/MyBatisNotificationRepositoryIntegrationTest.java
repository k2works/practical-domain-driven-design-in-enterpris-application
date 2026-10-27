package com.example.cargotracker.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 通知のリポジトリと notification スキーマ（PostgreSQL。N-INV-02。Bolt 33）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisNotificationRepositoryIntegrationTest {

    private static final UtcInstant CREATED_AT = new UtcInstant(Instant.parse("2026-10-15T01:00:00.123456Z"));
    private static final UtcInstant SENT_AT = new UtcInstant(Instant.parse("2026-10-15T01:00:02.654321Z"));

    @Autowired
    MyBatisNotificationRepository repository;

    private static Notification pending(UUID eventId, UUID recipient) {
        return Notification.pending(
                eventId,
                recipient,
                "shipper@example.com",
                NotificationKind.PASSWORD_RESET,
                UUID.randomUUID().toString(),
                "ja",
                CREATED_AT);
    }

    @Test
    void 記録した通知を契機と宛先で読み出す() {
        Notification notification = pending(UUID.randomUUID(), UUID.randomUUID());

        assertThat(repository.addIfAbsent(notification)).isTrue();

        Notification found = repository
                .findByEventIdAndRecipient(notification.eventId(), notification.recipientUserId())
                .orElseThrow();
        assertThat(found.id()).isEqualTo(notification.id());
        assertThat(found.email()).isEqualTo("shipper@example.com");
        assertThat(found.kind()).isEqualTo(NotificationKind.PASSWORD_RESET);
        assertThat(found.targetRef()).isEqualTo(notification.targetRef());
        assertThat(found.locale()).isEqualTo("ja");
        assertThat(found.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(found.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 利用者の招待の通知を記録して読み出す() {
        // 行 6（Bolt 38b2b。CHECK が USER_INVITATION を通す）
        Notification notification = Notification.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hanako@example.com",
                NotificationKind.USER_INVITATION,
                UUID.randomUUID().toString(),
                "ja",
                CREATED_AT);

        assertThat(repository.addIfAbsent(notification)).isTrue();

        assertThat(repository
                        .findByEventIdAndRecipient(notification.eventId(), notification.recipientUserId())
                        .orElseThrow()
                        .kind())
                .isEqualTo(NotificationKind.USER_INVITATION);
    }

    @Test
    void 全部の種類の通知をCHECKが通して記録できる() {
        // Bolt 38d の行 12（enum と ck_notification_kind のずれを見張る。T-214）
        for (NotificationKind kind : NotificationKind.values()) {
            Notification notification = Notification.pending(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "hanako@example.com",
                    kind,
                    UUID.randomUUID().toString(),
                    "ja",
                    CREATED_AT);

            assertThat(repository.addIfAbsent(notification)).as(kind.name()).isTrue();
            assertThat(repository
                            .findByEventIdAndRecipient(notification.eventId(), notification.recipientUserId())
                            .orElseThrow()
                            .kind())
                    .isEqualTo(kind);
        }
    }

    @Test
    void 同じ契機と宛先の2つ目は記録しない() {
        UUID eventId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        assertThat(repository.addIfAbsent(pending(eventId, recipient))).isTrue();

        assertThat(repository.addIfAbsent(pending(eventId, recipient))).isFalse();
        assertThat(repository.addIfAbsent(pending(eventId, UUID.randomUUID())))
                .as("宛先が違えば記録する")
                .isTrue();
    }

    @Test
    void 送信待ちの通知にだけ送信の結果を書く() {
        Notification notification = pending(UUID.randomUUID(), UUID.randomUUID());
        repository.addIfAbsent(notification);

        assertThat(repository.recordOutcome(notification.sent(SENT_AT))).isTrue();
        assertThat(repository.recordOutcome(notification.failed("後から届いた失敗")))
                .as("送信済みの通知は変えない")
                .isFalse();

        Notification found = repository
                .findByEventIdAndRecipient(notification.eventId(), notification.recipientUserId())
                .orElseThrow();
        assertThat(found.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.sentAt()).contains(SENT_AT);
        assertThat(found.failureReason()).isEmpty();
    }

    @Test
    void 失敗の理由を書く() {
        Notification notification = pending(UUID.randomUUID(), UUID.randomUUID());
        repository.addIfAbsent(notification);

        repository.recordOutcome(notification.failed("MailSendException: 接続できない"));

        assertThat(repository
                        .findByEventIdAndRecipient(notification.eventId(), notification.recipientUserId())
                        .orElseThrow()
                        .failureReason())
                .contains("MailSendException: 接続できない");
    }
}

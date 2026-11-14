package com.example.cargotracker.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    JdbcTemplate jdbc;

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

    // --- Bolt 43-3: 宛先の利用者の通知の一覧 ---

    private static Notification of(UUID recipient, NotificationKind kind, String ref, String createdAt) {
        return Notification.pending(
                UUID.randomUUID(),
                recipient,
                "shipper@example.com",
                kind,
                ref,
                "ja",
                new UtcInstant(Instant.parse(createdAt)));
    }

    @Test
    void 宛先の利用者の通知を新しい順に種類で絞って上限まで返す() {
        UUID recipient = UUID.randomUUID();
        Notification older =
                of(recipient, NotificationKind.QUOTATION_PRESENTED, "TR-2026-0001", "2026-11-16T01:00:00Z");
        Notification newer = of(recipient, NotificationKind.BOOKING_CONFIRMED, "TR-2026-0002", "2026-11-16T02:00:00Z");
        Notification reset = of(recipient, NotificationKind.PASSWORD_RESET, "token", "2026-11-16T03:00:00Z");
        Notification other =
                of(UUID.randomUUID(), NotificationKind.BOOKING_CONFIRMED, "TR-2026-0003", "2026-11-16T04:00:00Z");
        List.of(older, newer, reset, other).forEach(repository::addIfAbsent);
        Set<NotificationKind> business =
                EnumSet.of(NotificationKind.QUOTATION_PRESENTED, NotificationKind.BOOKING_CONFIRMED);

        assertThat(repository.findRecentByRecipient(recipient, business, 10))
                .extracting(Notification::id)
                .containsExactly(newer.id(), older.id());
        assertThat(repository.findRecentByRecipient(recipient, business, 1))
                .extracting(Notification::id)
                .containsExactly(newer.id());
        assertThat(repository.findRecentByRecipient(recipient, Set.of(), 10)).isEmpty();
    }

    @Test
    void 宛先の索引がある() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'notification' AND indexname = 'ix_notification_recipient'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}

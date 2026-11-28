package com.example.cargotracker.notification.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 通知の状態の遷移（ADR-017。Bolt 33）。 */
class NotificationTest {

    private static final UtcInstant CREATED_AT = new UtcInstant(Instant.parse("2026-10-15T01:00:00Z"));
    private static final UtcInstant SENT_AT = new UtcInstant(Instant.parse("2026-10-15T01:00:02Z"));

    private static Notification pending() {
        return Notification.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "shipper@example.com",
                NotificationKind.PASSWORD_RESET,
                UUID.randomUUID().toString(),
                "ja",
                CREATED_AT);
    }

    @Test
    void 記録した通知は送信待ちで送信時刻も失敗の理由も持たない() {
        Notification notification = pending();

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.sentAt()).isEmpty();
        assertThat(notification.failureReason()).isEmpty();
    }

    @Test
    void 送信待ちの通知は送信済みになり送信時刻を持つ() {
        Notification sent = pending().sent(SENT_AT);

        assertThat(sent.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.sentAt()).contains(SENT_AT);
        assertThat(sent.failureReason()).isEmpty();
    }

    @Test
    void 送信待ちの通知は失敗になり理由を持つ() {
        Notification failed = pending().failed("MailSendException: 接続できない");

        assertThat(failed.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.failureReason()).contains("MailSendException: 接続できない");
        assertThat(failed.sentAt()).isEmpty();
    }

    @Test
    void 失敗の理由は列の長さに切り詰める() {
        String exact = "x".repeat(Notification.FAILURE_REASON_MAX_LENGTH);

        assertThat(pending().failed(exact).failureReason()).contains(exact);
        assertThat(pending().failed(exact + "y").failureReason()).contains(exact);
    }

    @Test
    void 送信済みと失敗の通知はもう送信の結果を変えられない() {
        Notification sent = pending().sent(SENT_AT);
        Notification failed = pending().failed("理由");

        assertThatThrownBy(() -> sent.failed("理由")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.sent(SENT_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 状態と送信時刻と失敗の理由の組が合わない通知は組み立てられない() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        assertThatThrownBy(() -> Notification.reconstitute(
                        id,
                        eventId,
                        recipient,
                        "a@example.com",
                        NotificationKind.PASSWORD_RESET,
                        "ref",
                        "ja",
                        NotificationStatus.SENT,
                        null,
                        null,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.reconstitute(
                        id,
                        eventId,
                        recipient,
                        "a@example.com",
                        NotificationKind.PASSWORD_RESET,
                        "ref",
                        "ja",
                        NotificationStatus.FAILED,
                        null,
                        null,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 文字列にメールアドレスを出さない() {
        assertThat(pending().toString()).doesNotContain("shipper@example.com");
    }
}

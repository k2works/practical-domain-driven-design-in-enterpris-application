package com.example.cargotracker.notification.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.ShipperNotificationKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 失敗した荷主への業務の通知の再送（ADR-021 の C-3。W10-1 の Bolt 61。CP-5）。 */
class NotificationRetryTest {

    private static final Instant CREATED = Instant.parse("2026-11-27T00:00:00Z");
    private static final String LINK = "https://example.com/customer/bookings/BK-1";

    private static UtcInstant after(Duration duration) {
        return new UtcInstant(CREATED.plus(duration));
    }

    private static Notification resendable() {
        return Notification.pendingResendable(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "shipper@example.com",
                NotificationKind.BOOKING_CONFIRMED,
                "BK-1",
                LINK,
                "ja",
                new UtcInstant(CREATED));
    }

    @Test
    void 再送する種類は荷主への業務の通知の5つだけ() {
        assertThat(Arrays.stream(NotificationKind.values())
                        .filter(NotificationKind::isResendable)
                        .map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(ShipperNotificationKind.values())
                        .map(Enum::name)
                        .toList());
    }

    @ParameterizedTest
    @EnumSource(
            value = NotificationKind.class,
            names = {"PASSWORD_RESET", "USER_INVITATION", "TOTP_RESET_BY_ADMIN", "TOTP_ENROLLED", "TOTP_RE_ENROLLED"})
    void 再送しない種類はリンクを持てない(NotificationKind kind) {
        UtcInstant at = new UtcInstant(CREATED);

        assertThatThrownBy(() -> Notification.pendingResendable(
                        UUID.randomUUID(), UUID.randomUUID(), "a@example.com", kind, "ref", LINK, "ja", at))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 一時の失敗は再送できて再送すると同じ通知が送信待ちに戻り回数が増える() {
        Notification failed = resendable().failed("接続できない", true);

        Notification retrying = failed.retry(after(Duration.ofMinutes(10))).orElseThrow();

        assertThat(failed.retryable()).isTrue();
        assertThat(retrying.id()).isEqualTo(failed.id());
        assertThat(retrying.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(retrying.retryCount()).isEqualTo(1);
        assertThat(retrying.retryable()).isFalse();
        assertThat(retrying.failureReason()).isEmpty();
        assertThat(retrying.link()).hasValue(LINK);
    }

    @Test
    void 一時でない失敗は再送しない() {
        Notification failed = resendable().failed("宛先の不備", false);

        assertThat(failed.retryable()).isFalse();
        assertThat(failed.retry(after(Duration.ofMinutes(10)))).isEmpty();
    }

    @Test
    void リンクのない通知は一時の失敗でも再送しない() {
        Notification failed = Notification.pending(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "a@example.com",
                        NotificationKind.PASSWORD_RESET,
                        "ref",
                        "ja",
                        new UtcInstant(CREATED))
                .failed("接続できない", true);

        assertThat(failed.retryable()).isFalse();
    }

    @Test
    void 再送は3回までで3回目の失敗は再送できない() {
        Notification notification = resendable().failed("接続できない", true);
        for (int count = 1; count <= 3; count++) {
            Notification retrying =
                    notification.retry(after(Duration.ofMinutes(10))).orElseThrow();
            assertThat(retrying.retryCount()).isEqualTo(count);
            notification = retrying.failed("接続できない", true);
        }

        assertThat(notification.retryable()).isFalse();
        assertThat(notification.retry(after(Duration.ofHours(1)))).isEmpty();
    }

    @Test
    void 作成から24時間以上たった通知は再送しない() {
        Notification failed = resendable().failed("接続できない", true);

        assertThat(failed.retry(after(Duration.ofHours(24).minusSeconds(1)))).isPresent();
        assertThat(failed.retry(after(Duration.ofHours(24)))).isEmpty();
    }

    @Test
    void 文字列表現にリンクと宛先を出さない() {
        assertThat(resendable().toString()).doesNotContain(LINK).doesNotContain("shipper@example.com");
    }
}

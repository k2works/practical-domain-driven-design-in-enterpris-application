package com.example.cargotracker.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 再送の列と引き取り（Bolt 61）。H2 と PostgreSQL の両方で確かめる（ADR-007）。 */
@Transactional
abstract class NotificationRetryRepositoryContract {

    /** 共有の DB のほかの行と重ならないよう、遠い未来の時刻で確かめる。 */
    private static final UtcInstant CREATED_AT = new UtcInstant(Instant.parse("2999-01-01T00:00:00Z"));

    private static final String LINK = "https://example.com/customer/bookings/BK-1";

    @Autowired
    NotificationRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private static Notification resendable() {
        return Notification.pendingResendable(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "shipper@example.com",
                NotificationKind.BOOKING_CONFIRMED,
                "BK-1",
                LINK,
                "ja",
                CREATED_AT);
    }

    private Notification stored(Notification notification) {
        return repository
                .findByEventIdAndRecipient(notification.eventId(), notification.recipientUserId())
                .orElseThrow();
    }

    private Notification addFailed(boolean transientFailure) {
        Notification pending = resendable();
        repository.addIfAbsent(pending);
        Notification failed = pending.failed("接続できない", transientFailure);
        assertThat(repository.recordOutcome(failed)).isTrue();
        return failed;
    }

    private java.util.List<UUID> candidatesAfter(Instant createdAfter) {
        return repository.findRetryCandidates(new UtcInstant(createdAfter), 100).stream()
                .map(Notification::id)
                .toList();
    }

    @Test
    void リンクと再送の列を往復する() {
        Notification failed = addFailed(true);

        assertThat(stored(failed)).satisfies(found -> {
            assertThat(found.link()).hasValue(LINK);
            assertThat(found.retryCount()).isZero();
            assertThat(found.retryable()).isTrue();
        });
    }

    @Test
    void 再送の候補は再送できる失敗で指定の時刻より後に作られた通知だけ() {
        Notification retryable = addFailed(true);
        Notification permanent = addFailed(false);
        Instant justBefore = CREATED_AT.instant().minusSeconds(1);

        assertThat(candidatesAfter(justBefore)).contains(retryable.id()).doesNotContain(permanent.id());
        assertThat(candidatesAfter(CREATED_AT.instant())).doesNotContain(retryable.id());
    }

    @Test
    void 引き取りは1回だけ成功し送信待ちになって回数が増える() {
        Notification failed = addFailed(true);
        Notification retrying = failed.retry(new UtcInstant(CREATED_AT.instant().plus(Duration.ofMinutes(10))))
                .orElseThrow();

        assertThat(repository.claimRetry(retrying)).isTrue();
        assertThat(repository.claimRetry(retrying)).isFalse();

        assertThat(stored(failed)).satisfies(found -> {
            assertThat(found.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(found.retryCount()).isEqualTo(1);
            assertThat(found.retryable()).isFalse();
            assertThat(found.failureReason()).isEmpty();
        });
    }

    @Test
    void 再送できない失敗の行は引き取れない() {
        Notification pending = resendable();
        repository.addIfAbsent(pending);
        assertThat(repository.recordOutcome(pending.failed("宛先の不備", false))).isTrue();
        Notification forced = pending.failed("接続できない", true)
                .retry(new UtcInstant(CREATED_AT.instant().plus(Duration.ofMinutes(10))))
                .orElseThrow();

        assertThat(repository.claimRetry(forced)).isFalse();
        assertThat(stored(pending).status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void リンクは再送しない種類の行には書けない() {
        Notification pending = resendable();
        repository.addIfAbsent(pending);
        UUID id = pending.id();

        assertThatThrownBy(() ->
                        jdbc.update("UPDATE notification.notification SET kind = 'PASSWORD_RESET' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

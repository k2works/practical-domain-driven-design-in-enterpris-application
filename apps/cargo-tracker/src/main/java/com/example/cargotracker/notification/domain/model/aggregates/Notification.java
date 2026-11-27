package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知。利用者へ送るメール 1 通の宛先・種類・対象・言語・状態・送信の結果の記録（ADR-017。Bolt 33）。本文は持たない（N-INV-01）。リンクは、失敗を再送する荷主への業務の
 * 通知だけが持つ（Bolt 61。トークンを含むリンクは持たない）。同じ契機（{@code eventId}）と宛先に 1 つだけ（N-INV-02。表の一意制約が正）。送信待ちから送信済みか失敗に変わり、再送できる失敗だけが
 * 送信待ちに戻る。
 * 状態の更新は送信待ちを条件にした更新で行い、版の列を持たない（データモデルの例外）。
 */
@AggregateRoot
public final class Notification {

    /** 失敗の理由の長さの上限（表の列の長さ）。 */
    public static final int FAILURE_REASON_MAX_LENGTH = 1000;

    /** 再送の回数の上限（ADR-021）。 */
    public static final int MAX_RETRIES = 3;

    /** 作成からこの時間がたった通知は再送しない（失敗の後に利用停止になった人へ届き続けないための上限。CP-5）。 */
    public static final Duration RETRY_WINDOW = Duration.ofHours(24);

    private final UUID id;
    private final UUID eventId;
    private final UUID recipientUserId;
    private final String email;
    private final NotificationKind kind;
    private final String targetRef;
    private final String locale;
    private final NotificationStatus status;
    private final UtcInstant sentAt;
    private final String failureReason;
    private final UtcInstant createdAt;
    private final String link;
    private final int retryCount;
    private final boolean retryable;

    @SuppressWarnings("java:S107") // 表の列をそろえて受ける（reconstitute と同じ並び）
    private Notification(
            UUID id,
            UUID eventId,
            UUID recipientUserId,
            String email,
            NotificationKind kind,
            String targetRef,
            String locale,
            NotificationStatus status,
            UtcInstant sentAt,
            String failureReason,
            UtcInstant createdAt,
            RetryState retry) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.recipientUserId = Objects.requireNonNull(recipientUserId, "recipientUserId");
        this.email = requireText(email, "email");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.targetRef = requireText(targetRef, "targetRef");
        this.locale = requireText(locale, "locale");
        this.status = Objects.requireNonNull(status, "status");
        this.sentAt = sentAt;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.link = retry.link();
        this.retryCount = retry.retryCount();
        this.retryable = retry.retryable();
        requireValidRetryState();
        if ((status == NotificationStatus.SENT) != (sentAt != null)) {
            throw new IllegalArgumentException("送信時刻は送信済みの通知だけが持つ");
        }
        if ((status == NotificationStatus.FAILED) != (failureReason != null)) {
            throw new IllegalArgumentException("失敗の理由は失敗した通知だけが持つ");
        }
    }

    private void requireValidRetryState() {
        if (link != null && !kind.isResendable()) {
            throw new IllegalArgumentException("リンクを保存できない種類の通知: " + kind);
        }
        if (retryCount < 0 || retryCount > MAX_RETRIES) {
            throw new IllegalArgumentException("再送の回数が範囲の外: " + retryCount);
        }
        if (retryable && (status != NotificationStatus.FAILED || link == null || retryCount >= MAX_RETRIES)) {
            throw new IllegalArgumentException("再送できるのは、リンクがあり上限に達していない失敗した通知だけ");
        }
    }

    /** 送る前の通知を記録する。 */
    public static Notification pending(
            UUID eventId,
            UUID recipientUserId,
            String email,
            NotificationKind kind,
            String targetRef,
            String locale,
            UtcInstant createdAt) {
        return new Notification(
                UUID.randomUUID(),
                eventId,
                recipientUserId,
                email,
                kind,
                targetRef,
                locale,
                NotificationStatus.PENDING,
                null,
                null,
                createdAt,
                RetryState.NONE);
    }

    /** 失敗したら再送する通知の送信待ち。リンクを保存できるのは {@link NotificationKind#isResendable()} の種類だけ。 */
    @SuppressWarnings("java:S107") // 表の列をそろえて受ける
    public static Notification pendingResendable(
            UUID eventId,
            UUID recipientUserId,
            String email,
            NotificationKind kind,
            String targetRef,
            String link,
            String locale,
            UtcInstant createdAt) {
        return new Notification(
                UUID.randomUUID(),
                eventId,
                recipientUserId,
                email,
                kind,
                targetRef,
                locale,
                NotificationStatus.PENDING,
                null,
                null,
                createdAt,
                new RetryState(requireText(link, "link"), 0, false));
    }

    /** 保存した再送の状態を戻す（{@link #reconstitute} の後に使う）。 */
    public Notification withRetryState(String storedLink, int storedRetryCount, boolean storedRetryable) {
        return new Notification(
                id,
                eventId,
                recipientUserId,
                email,
                kind,
                targetRef,
                locale,
                status,
                sentAt,
                failureReason,
                createdAt,
                new RetryState(storedLink, storedRetryCount, storedRetryable));
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    @SuppressWarnings("java:S107") // 表の列をそろえて受ける
    public static Notification reconstitute(
            UUID id,
            UUID eventId,
            UUID recipientUserId,
            String email,
            NotificationKind kind,
            String targetRef,
            String locale,
            NotificationStatus status,
            UtcInstant sentAt,
            String failureReason,
            UtcInstant createdAt) {
        return new Notification(
                id,
                eventId,
                recipientUserId,
                email,
                kind,
                targetRef,
                locale,
                status,
                sentAt,
                failureReason,
                createdAt,
                RetryState.NONE);
    }

    /** 送ったことを記録した通知を返す。送信待ちのときだけ。 */
    public Notification sent(UtcInstant at) {
        requirePending();
        return new Notification(
                id,
                eventId,
                recipientUserId,
                email,
                kind,
                targetRef,
                locale,
                NotificationStatus.SENT,
                Objects.requireNonNull(at, "at"),
                null,
                createdAt,
                new RetryState(link, retryCount, false));
    }

    /** 送れなかったことを記録した、再送しない通知を返す。送信待ちのときだけ。理由は列の長さに切り詰める。 */
    public Notification failed(String reason) {
        return failed(reason, false);
    }

    /**
     * 失敗にする。再送できるのは、一時の失敗で、リンクがあり、再送の回数が上限に達していないときだけ。
     *
     * @param transientFailure 時間をおけば成功しうる失敗か
     */
    public Notification failed(String reason, boolean transientFailure) {
        requirePending();
        String text = requireText(reason, "reason");
        // 列の長さは文字数なので、サロゲートペアの途中で切らないよう code point で数える
        String truncated = text.codePointCount(0, text.length()) > FAILURE_REASON_MAX_LENGTH
                ? text.substring(0, text.offsetByCodePoints(0, FAILURE_REASON_MAX_LENGTH))
                : text;
        return new Notification(
                id,
                eventId,
                recipientUserId,
                email,
                kind,
                targetRef,
                locale,
                NotificationStatus.FAILED,
                null,
                truncated,
                createdAt,
                new RetryState(link, retryCount, transientFailure && link != null && retryCount < MAX_RETRIES));
    }

    /**
     * 再送のために送信待ちに戻した通知。再送できない失敗、または作成から {@link #RETRY_WINDOW} がたっていれば空。同じ行を使うので、
     * 契機と宛先に 1 通（N-INV-02）は変わらない。
     */
    public Optional<Notification> retry(UtcInstant now) {
        if (!retryable || !now.instant().isBefore(createdAt.instant().plus(RETRY_WINDOW))) {
            return Optional.empty();
        }
        return Optional.of(new Notification(
                id,
                eventId,
                recipientUserId,
                email,
                kind,
                targetRef,
                locale,
                NotificationStatus.PENDING,
                null,
                null,
                createdAt,
                new RetryState(link, retryCount + 1, false)));
    }

    private void requirePending() {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException("送信待ちの通知だけが送信の結果を持てる: " + status);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " がない");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID recipientUserId() {
        return recipientUserId;
    }

    public String email() {
        return email;
    }

    public NotificationKind kind() {
        return kind;
    }

    public String targetRef() {
        return targetRef;
    }

    public String locale() {
        return locale;
    }

    public NotificationStatus status() {
        return status;
    }

    public Optional<UtcInstant> sentAt() {
        return Optional.ofNullable(sentAt);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public UtcInstant createdAt() {
        return createdAt;
    }

    public Optional<String> link() {
        return Optional.ofNullable(link);
    }

    public int retryCount() {
        return retryCount;
    }

    public boolean retryable() {
        return retryable;
    }

    /** メールアドレスとリンクを文字列に出さない。 */
    @Override
    public String toString() {
        return "Notification[id=" + id + ", eventId=" + eventId + ", kind=" + kind + ", status=" + status + "]";
    }

    private record RetryState(String link, int retryCount, boolean retryable) {
        private static final RetryState NONE = new RetryState(null, 0, false);
    }
}

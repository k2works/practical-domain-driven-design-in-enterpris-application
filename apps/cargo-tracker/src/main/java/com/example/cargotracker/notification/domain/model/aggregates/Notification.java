package com.example.cargotracker.notification.domain.model.aggregates;

import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知。利用者へ送るメール 1 通の宛先・種類・対象・言語・状態・送信の結果の記録（ADR-017。Bolt 33）。本文とリンクは持たない
 * （N-INV-01）。同じ契機（{@code eventId}）と宛先に 1 つだけ（N-INV-02。表の一意制約が正）。送信待ちからだけ、送信済みか失敗に変わる。
 * 状態の更新は送信待ちを条件にした更新で行い、版の列を持たない（データモデルの例外）。
 */
@AggregateRoot
public final class Notification {

    /** 失敗の理由の長さの上限（表の列の長さ）。 */
    public static final int FAILURE_REASON_MAX_LENGTH = 1000;

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
            UtcInstant createdAt) {
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
        if ((status == NotificationStatus.SENT) != (sentAt != null)) {
            throw new IllegalArgumentException("送信時刻は送信済みの通知だけが持つ");
        }
        if ((status == NotificationStatus.FAILED) != (failureReason != null)) {
            throw new IllegalArgumentException("失敗の理由は失敗した通知だけが持つ");
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
                createdAt);
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
                id, eventId, recipientUserId, email, kind, targetRef, locale, status, sentAt, failureReason, createdAt);
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
                createdAt);
    }

    /** 送れなかったことを記録した通知を返す。送信待ちのときだけ。理由は列の長さに切り詰める。 */
    public Notification failed(String reason) {
        requirePending();
        String text = requireText(reason, "reason");
        String truncated =
                text.length() > FAILURE_REASON_MAX_LENGTH ? text.substring(0, FAILURE_REASON_MAX_LENGTH) : text;
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
                createdAt);
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

    /** メールアドレスを文字列に出さない。 */
    @Override
    public String toString() {
        return "Notification[id=" + id + ", eventId=" + eventId + ", kind=" + kind + ", status=" + status + "]";
    }
}

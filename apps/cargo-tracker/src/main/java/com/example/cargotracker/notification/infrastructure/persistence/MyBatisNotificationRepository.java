package com.example.cargotracker.notification.infrastructure.persistence;

import com.example.cargotracker.notification.domain.model.aggregates.Notification;
import com.example.cargotracker.notification.domain.model.aggregates.NotificationRepository;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationKind;
import com.example.cargotracker.notification.domain.model.valueobjects.NotificationStatus;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 通知のリポジトリの MyBatis 実装（Bolt 33）。
 */
@Repository
public class MyBatisNotificationRepository implements NotificationRepository {

    private final NotificationMapper mapper;

    public MyBatisNotificationRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean addIfAbsent(Notification notification) {
        return mapper.insertNotificationIfAbsent(toRow(notification)) == 1;
    }

    @Override
    public boolean recordOutcome(Notification notification) {
        if (notification.status() == NotificationStatus.PENDING) {
            throw new IllegalArgumentException("送信の結果のない通知は書かない");
        }
        return mapper.updateOutcomeIfPending(toRow(notification)) == 1;
    }

    @Override
    public java.util.List<Notification> findRecentByRecipient(
            UUID recipientUserId, java.util.Set<NotificationKind> kinds, int limit) {
        if (kinds.isEmpty()) {
            return java.util.List.of();
        }
        return mapper
                .selectRecentByRecipient(
                        recipientUserId, kinds.stream().map(Enum::name).toList(), limit)
                .stream()
                .map(MyBatisNotificationRepository::toDomain)
                .toList();
    }

    @Override
    public Optional<Notification> findByEventIdAndRecipient(UUID eventId, UUID recipientUserId) {
        return mapper.selectByEventIdAndRecipient(eventId, recipientUserId)
                .map(MyBatisNotificationRepository::toDomain);
    }

    @Override
    public java.util.List<Notification> findRetryCandidates(UtcInstant createdAfter, int limit) {
        return mapper.selectRetryCandidates(toOffset(createdAfter), limit).stream()
                .map(MyBatisNotificationRepository::toDomain)
                .toList();
    }

    @Override
    public boolean claimRetry(Notification retrying) {
        return mapper.updateToPendingForRetry(toRow(retrying)) == 1;
    }

    private static NotificationRow toRow(Notification notification) {
        return new NotificationRow(
                notification.id(),
                notification.eventId(),
                notification.recipientUserId(),
                notification.email(),
                notification.kind().name(),
                notification.targetRef(),
                notification.locale(),
                notification.status().name(),
                notification
                        .sentAt()
                        .map(MyBatisNotificationRepository::toOffset)
                        .orElse(null),
                notification.failureReason().orElse(null),
                toOffset(notification.createdAt()),
                notification.link().orElse(null),
                notification.retryCount(),
                // 再送の可否は失敗した行だけが持つ（表の CHECK）
                notification.status() == NotificationStatus.FAILED ? notification.retryable() : null);
    }

    private static Notification toDomain(NotificationRow row) {
        Notification notification = Notification.reconstitute(
                row.id(),
                row.eventId(),
                row.recipientUserId(),
                row.email(),
                NotificationKind.valueOf(row.kind()),
                row.targetRef(),
                row.locale(),
                NotificationStatus.valueOf(row.status()),
                row.sentAt() == null ? null : new UtcInstant(row.sentAt().toInstant()),
                row.failureReason(),
                new UtcInstant(row.createdAt().toInstant()));
        return notification.withRetryState(row.link(), row.retryCount(), Boolean.TRUE.equals(row.retryable()));
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }
}

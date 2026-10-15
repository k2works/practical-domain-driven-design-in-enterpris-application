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
    public Optional<Notification> findByEventIdAndRecipient(UUID eventId, UUID recipientUserId) {
        return mapper.selectByEventIdAndRecipient(eventId, recipientUserId)
                .map(MyBatisNotificationRepository::toDomain);
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
                toOffset(notification.createdAt()));
    }

    private static Notification toDomain(NotificationRow row) {
        return Notification.reconstitute(
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
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }
}

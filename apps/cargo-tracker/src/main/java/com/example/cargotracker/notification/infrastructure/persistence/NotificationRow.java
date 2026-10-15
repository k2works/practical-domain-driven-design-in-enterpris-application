package com.example.cargotracker.notification.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/** notification.notification の 1 行。 */
public record NotificationRow(
        UUID id,
        UUID eventId,
        UUID recipientUserId,
        String email,
        String kind,
        String targetRef,
        String locale,
        String status,
        OffsetDateTime sentAt,
        String failureReason,
        OffsetDateTime createdAt) {}

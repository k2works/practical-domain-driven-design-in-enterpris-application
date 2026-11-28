package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 拒否した主要実績の表（`rejected_milestone`）の 1 行（Bolt 39g）。
 */
public record RejectedMilestoneRow(
        UUID id,
        String trackingNumber,
        String kind,
        String locationUnlocode,
        OffsetDateTime occurredAt,
        String sourceKind,
        String sourceRef,
        OffsetDateTime acquiredAt,
        UUID dataSourceId,
        String externalId,
        String reason,
        OffsetDateTime recordedAt) {}

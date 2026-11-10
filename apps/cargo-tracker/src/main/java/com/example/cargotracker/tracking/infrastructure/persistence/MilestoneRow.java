package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 主要実績の表（`milestone`）の 1 行（Bolt 26b。外部の識別の 3 列は Bolt 39g）。
 */
public record MilestoneRow(
        String trackingNumber,
        int milestoneNo,
        String kind,
        String locationUnlocode,
        OffsetDateTime occurredAt,
        String sourceKind,
        String sourceRef,
        OffsetDateTime acquiredAt,
        String state,
        UUID registeredBy,
        OffsetDateTime registeredAt,
        UUID dataSourceId,
        String externalId,
        OffsetDateTime publishedAt) {}

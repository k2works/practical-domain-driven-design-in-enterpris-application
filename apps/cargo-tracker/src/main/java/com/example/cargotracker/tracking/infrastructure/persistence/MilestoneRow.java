package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 主要実績の表（`milestone`）の 1 行（Bolt 26b）。
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
        OffsetDateTime registeredAt) {}

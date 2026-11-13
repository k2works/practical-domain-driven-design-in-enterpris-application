package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 有人案件の表（`tracking.service_case`）の 1 行（Bolt 40-6）。
 */
@SuppressWarnings("java:S107") // 表の列をそのまま持つ
public record ServiceCaseRow(
        String caseNumber,
        String kind,
        UUID shipperCompanyId,
        UUID bookingId,
        String trackingNumber,
        String cause,
        String priority,
        String department,
        UUID assigneeId,
        OffsetDateTime receivedAt,
        OffsetDateTime receiveDueAt,
        OffsetDateTime answerDueAt,
        OffsetDateTime nextUpdateAt,
        String oldVersionRef,
        String newVersionRef,
        String manualSourceRef,
        boolean reconciliationRequired,
        UUID reconciliationId,
        OffsetDateTime acknowledgedAt,
        String status,
        long version,
        OffsetDateTime createdAt,
        UUID createdBy,
        OffsetDateTime updatedAt,
        UUID updatedBy) {}

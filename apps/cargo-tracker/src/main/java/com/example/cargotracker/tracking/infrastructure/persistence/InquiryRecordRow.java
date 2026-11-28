package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 照会記録の表（`inquiry_record`）の 1 行（Bolt 40-15）。
 */
public record InquiryRecordRow(
        UUID id,
        String channel,
        UUID shipperCompanyId,
        String trackingNumber,
        String inquirerKind,
        UUID inquirerUserId,
        OffsetDateTime receivedAt,
        boolean escalated,
        String caseNumber,
        UUID recordedBy,
        OffsetDateTime createdAt) {}

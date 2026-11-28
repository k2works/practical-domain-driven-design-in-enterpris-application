package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * quotation.review_record の 1 行。
 */
public record ReviewRecordRow(
        UUID id,
        UUID transportRequestId,
        int versionNo,
        String decision,
        UUID reviewerId,
        String rationale,
        String missingItems,
        OffsetDateTime decidedAt) {}

package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 除外理由の表（`exclusion_reason`）の 1 行。
 */
public record ExclusionReasonRow(
        UUID routingCaseId,
        int routeVersionNo,
        int candidateNo,
        int reasonNo,
        String reasonCode,
        OffsetDateTime violatedAt,
        String threshold,
        String infoVersion) {}

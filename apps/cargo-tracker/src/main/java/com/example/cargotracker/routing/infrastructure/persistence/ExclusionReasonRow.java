package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 除外理由の表（`exclusion_reason`）の 1 行。閾値は区分ごとの 6 列で、区分が持つ列だけ値があり、ほかは null（CHECK `ck_exclusion_reason_threshold`。
 * Bolt 39i）。
 */
public record ExclusionReasonRow(
        UUID routingCaseId,
        int routeVersionNo,
        int candidateNo,
        int reasonNo,
        String reasonCode,
        OffsetDateTime violatedAt,
        OffsetDateTime thresholdDeadline,
        String thresholdPortUnlocode,
        String thresholdRequiredConnection,
        String thresholdVoyageNumber,
        String thresholdFreshnessTolerance,
        String thresholdCargoCategory,
        String infoVersion) {}

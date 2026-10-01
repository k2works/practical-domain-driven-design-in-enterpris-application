package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * KPI 計測記録の行。
 */
public record KpiObservationRow(UUID transportRequestId, UUID shipperCompanyId, OffsetDateTime submittedAt,
        boolean excluded) {
}

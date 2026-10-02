package com.example.cargotracker.identity.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * identity.kpi_observation の 1 行。計測からの除外の列（excluded）は DB の既定値に任せ、まだ読み書きしない。
 */
public record KpiObservationRow(
        UUID transportRequestId, String transportRequestNumber, UUID shipperCompanyId, OffsetDateTime submittedAt) {}

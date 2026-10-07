package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 経路設計案件の表（`routing_case`）の 1 行。
 */
public record RoutingCaseRow(
        UUID id,
        String caseNumber,
        UUID transportRequestId,
        String transportRequestNumber,
        int transportRequestVersionNo,
        UUID quotationId,
        String routePolicyVia,
        String originUnlocode,
        String destinationUnlocode,
        OffsetDateTime arrivalDeadline,
        String cargoCategory,
        OffsetDateTime requestedAt,
        OffsetDateTime quotationExpiresAt,
        UUID createdBy,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}

package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 経路版の表（`route_version`）の 1 行。
 */
public record RouteVersionRow(
        UUID routingCaseId,
        int routeVersionNo,
        String status,
        OffsetDateTime candidatesEvaluatedAt,
        OffsetDateTime createdAt) {}

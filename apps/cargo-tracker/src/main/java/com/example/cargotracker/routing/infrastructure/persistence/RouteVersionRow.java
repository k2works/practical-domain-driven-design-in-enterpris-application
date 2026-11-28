package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 経路版の表（`route_version`）の 1 行。再設計要の記録の 2 列はそろって NULL かそろって値を持つ（Bolt 37d）。
 */
public record RouteVersionRow(
        UUID routingCaseId,
        int routeVersionNo,
        String status,
        OffsetDateTime candidatesEvaluatedAt,
        int candidatesFound,
        Integer selectedCandidateNo,
        String rationale,
        UUID approvedBy,
        OffsetDateTime approvedAt,
        OffsetDateTime redesignRequiredAt,
        String redesignReasons,
        OffsetDateTime createdAt) {}

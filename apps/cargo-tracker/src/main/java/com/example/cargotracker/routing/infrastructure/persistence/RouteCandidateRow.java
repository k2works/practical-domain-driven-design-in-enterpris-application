package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 経路候補の表（`route_candidate`）の 1 行。
 */
public record RouteCandidateRow(
        UUID routingCaseId,
        int routeVersionNo,
        int candidateNo,
        boolean conforming,
        OffsetDateTime estimatedArrivalAt,
        Integer minConnectionSlackMinutes,
        OffsetDateTime evaluatedAt,
        OffsetDateTime oldestInfoAcquiredAt,
        boolean infoInsufficient) {}

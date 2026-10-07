package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 区間の表（`candidate_leg`）の 1 行。
 */
public record CandidateLegRow(
        UUID routingCaseId,
        int routeVersionNo,
        int candidateNo,
        int legNo,
        String voyageNumber,
        String loadUnlocode,
        String dischargeUnlocode,
        OffsetDateTime departureAt,
        OffsetDateTime arrivalAt,
        String infoVersion,
        OffsetDateTime infoAcquiredAt,
        boolean executed) {}

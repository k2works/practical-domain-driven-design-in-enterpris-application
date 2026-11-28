package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 割り当てた区間の表（`assigned_route_leg`）の 1 行（Bolt 20）。
 */
public record AssignedRouteLegRow(
        UUID quotationId,
        int legNo,
        String voyageNumber,
        String loadUnlocode,
        String dischargeUnlocode,
        OffsetDateTime departureAt,
        OffsetDateTime arrivalAt) {}

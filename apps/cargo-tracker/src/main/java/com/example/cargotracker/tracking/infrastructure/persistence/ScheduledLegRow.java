package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 予定区間の表（`scheduled_leg`）の 1 行。区間番号は予定の区間の列の順に 1 から振る。
 */
public record ScheduledLegRow(
        String trackingNumber,
        int legNo,
        String voyageNumber,
        String loadUnlocode,
        String dischargeUnlocode,
        OffsetDateTime departureAt,
        OffsetDateTime arrivalAt) {}

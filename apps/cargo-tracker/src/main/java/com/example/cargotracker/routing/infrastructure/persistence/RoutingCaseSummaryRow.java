package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 経路設計案件一覧（S-05）の 1 行（案件と経路版 1 の状態）。
 */
public record RoutingCaseSummaryRow(
        String caseNumber,
        String transportRequestNumber,
        int transportRequestVersionNo,
        String originUnlocode,
        String destinationUnlocode,
        OffsetDateTime arrivalDeadline,
        OffsetDateTime requestedAt,
        String status) {}

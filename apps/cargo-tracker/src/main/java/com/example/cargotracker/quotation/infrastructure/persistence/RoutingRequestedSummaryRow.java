package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 経路設計中の見積依頼の詳細設計依頼済みの見積りの 1 行（受付一覧 S-02。Bolt 12）。
 */
public record RoutingRequestedSummaryRow(
        String requestNumber,
        int quotationNo,
        String status,
        OffsetDateTime respondedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime routeConfirmedAt) {}

package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;

/** 荷主に見える最新の見積りの 1 行（{@code selectLatestVisibleForShipper}。C-02。D-85。Bolt 35d）。 */
public record ShipperLatestQuotationSummaryRow(
        String requestNumber,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime shipperApprovedAt,
        OffsetDateTime presentedAt,
        OffsetDateTime routeConfirmedAt) {}

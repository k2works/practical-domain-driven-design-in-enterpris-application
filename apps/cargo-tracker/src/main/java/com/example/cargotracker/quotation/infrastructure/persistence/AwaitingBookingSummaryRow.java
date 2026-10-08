package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;

/** 予約の確定待ちの見積りの 1 行（{@code selectAwaitingBooking}。S-02。Bolt 23b）。 */
public record AwaitingBookingSummaryRow(
        String requestNumber,
        int quotationNo,
        String routingCaseNumber,
        int routeVersionNo,
        OffsetDateTime shipperApprovedAt,
        OffsetDateTime expiresAt) {}

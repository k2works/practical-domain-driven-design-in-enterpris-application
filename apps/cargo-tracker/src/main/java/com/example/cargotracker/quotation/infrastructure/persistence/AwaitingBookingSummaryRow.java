package com.example.cargotracker.quotation.infrastructure.persistence;

import java.time.OffsetDateTime;

/** 予約の確定待ちの見積りの 1 行（{@code selectAwaitingBooking}。S-02。Bolt 23b。承認待ちの行は荷主の承認時刻が null。Bolt 35c）。 */
public record AwaitingBookingSummaryRow(
        String requestNumber,
        int quotationNo,
        String status,
        String routingCaseNumber,
        int routeVersionNo,
        OffsetDateTime shipperApprovedAt,
        OffsetDateTime expiresAt,
        boolean routeRedesignRequired,
        OffsetDateTime firstDepartureAt) {}

package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 予約版の表（`booking_version`、追記専用）の 1 行。
 */
public record BookingVersionRow(
        UUID bookingId,
        int bookingVersionNo,
        UUID transportRequestId,
        int transportRequestVersionNo,
        UUID quotationId,
        String routingCaseNumber,
        int routeVersionNo,
        UUID consigneeCompanyId,
        String cargoCategory,
        String cargoSummary,
        UUID shipperApproverId,
        UUID confirmedBy,
        OffsetDateTime committedAt) {}

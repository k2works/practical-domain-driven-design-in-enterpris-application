package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 貨物予約の表（`booking`）の 1 行。
 */
public record BookingRow(
        UUID id,
        String trackingNumber,
        String transportRequestNumber,
        UUID shipperCompanyId,
        String status,
        String transportPhase,
        int currentVersionNo,
        long version,
        OffsetDateTime createdAt,
        UUID createdBy,
        OffsetDateTime updatedAt,
        UUID updatedBy) {}

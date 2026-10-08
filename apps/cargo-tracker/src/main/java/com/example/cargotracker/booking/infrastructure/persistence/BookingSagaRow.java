package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 予約サガの表（`booking_saga`）の 1 行。
 */
public record BookingSagaRow(
        UUID id,
        UUID bookingId,
        String trackingNumber,
        String status,
        String currentStep,
        int attempts,
        OffsetDateTime startedAt,
        OffsetDateTime updatedAt,
        long version) {}

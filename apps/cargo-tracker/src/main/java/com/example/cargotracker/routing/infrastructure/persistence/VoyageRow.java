package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 航海の表（`voyage`）の 1 行。
 */
public record VoyageRow(String voyageNumber, String adoptedInfoVersion, OffsetDateTime acquiredAt) {}

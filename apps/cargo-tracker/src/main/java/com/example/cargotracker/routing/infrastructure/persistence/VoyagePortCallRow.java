package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 航海と寄港の JOIN の 1 行（寄港ごとに 1 行。1 文で読み、航海と寄港の組を 1 つのスナップショットで得る。Bolt 39e の決定 (10)）。
 */
public record VoyagePortCallRow(
        String voyageNumber,
        String adoptedInfoVersion,
        String sourceKind,
        String sourceRef,
        OffsetDateTime acquiredAt,
        OffsetDateTime publishedAt,
        long version,
        int callNo,
        String portUnlocode,
        OffsetDateTime arrivalAt,
        OffsetDateTime departureAt) {}

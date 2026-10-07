package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 寄港の表（`port_call`）の 1 行。
 */
public record PortCallRow(
        String voyageNumber, int callNo, String portUnlocode, OffsetDateTime arrivalAt, OffsetDateTime departureAt) {}

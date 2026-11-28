package com.example.cargotracker.routing.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 接続時間規則の表（`connection_rule`）の 1 行。
 */
public record ConnectionRuleRow(
        UUID id, String portUnlocode, int minConnectionMinutes, OffsetDateTime validFrom, OffsetDateTime validTo) {}

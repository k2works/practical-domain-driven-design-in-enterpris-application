package com.example.cargotracker.identity.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * KPI 基準値の表（`identity.kpi_baseline`）の 1 行（W9 の Bolt 44-2）。
 */
public record KpiBaselineRow(
        UUID id,
        String kpi,
        LocalDate periodFrom,
        LocalDate periodTo,
        BigDecimal valueP50,
        BigDecimal valueP90,
        String method,
        UUID registeredBy,
        OffsetDateTime registeredAt,
        UUID supersedesId) {}

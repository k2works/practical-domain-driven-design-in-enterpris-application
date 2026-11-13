package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 営業日カレンダーの表（`business_calendar`）の 1 行（Bolt 40-4）。
 */
public record BusinessDayRow(
        LocalDate calendarDate,
        boolean businessDay,
        LocalTime openTime,
        LocalTime closeTime,
        String timeZone,
        String note) {}

package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 予約の要約の 1 行（S-10 予約一覧。貨物予約と予約版 1 を結んだ照会の結果。Bolt 25b）。
 */
public record BookingSummaryRow(
        UUID id, String trackingNumber, String transportRequestNumber, int quotationNo, OffsetDateTime committedAt) {}

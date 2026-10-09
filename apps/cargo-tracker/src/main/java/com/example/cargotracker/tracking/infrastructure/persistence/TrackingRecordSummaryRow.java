package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;

/**
 * 追跡一覧（S-11）の 1 行。追跡記録の表（`tracking_record`）の、一覧に要る列だけ（Bolt 26）。当初の到着予定は表では NULL 可。
 *
 * @param trackingNumber 追跡番号
 * @param currentStatus 現在状態
 * @param originalEta 当初の到着予定
 * @param createdAt 作成時刻（追跡の開始時刻）
 */
public record TrackingRecordSummaryRow(
        String trackingNumber, String currentStatus, OffsetDateTime originalEta, OffsetDateTime createdAt) {}

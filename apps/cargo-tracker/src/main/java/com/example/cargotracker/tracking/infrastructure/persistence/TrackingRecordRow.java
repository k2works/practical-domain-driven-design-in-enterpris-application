package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 追跡記録の表（`tracking_record`）の 1 行。実績に関わる列（現在状態の根拠の実績番号・確認中の理由・手続き中の段階・最終取得時刻）は
 * 主要実績を作る Bolt 26 から使うので、この Bolt の行には持たない（表では NULL）。
 */
public record TrackingRecordRow(
        String trackingNumber,
        UUID bookingId,
        UUID shipperCompanyId,
        UUID consigneeCompanyId,
        String bookingStatus,
        String routingCaseNumber,
        Integer routeVersionNo,
        String currentStatus,
        OffsetDateTime originalEta,
        OffsetDateTime latestEta,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}

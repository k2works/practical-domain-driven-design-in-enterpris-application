package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 追跡記録の表（`tracking_record`）の 1 行。現在状態の根拠の実績番号は Bolt 26b から持つ（実績から導出していなければ NULL）。
 * 確認中の理由は Bolt 39gc から持つ（現在状態が確認中のときだけ値を持つ）。ほかの実績に関わる列（手続き中の段階・最終取得時刻）は使う Bolt まで
 * 行に持たない（表では NULL）。
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
        Integer statusBasisMilestoneNo,
        String underReviewReason,
        OffsetDateTime originalEta,
        OffsetDateTime latestEta,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}

package com.example.cargotracker.tracking.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 追跡記録の表（`tracking_record`）の 1 行。現在状態の根拠の実績番号は Bolt 26b から持つ（実績から導出していなければ NULL）。
 * 確認中の理由は Bolt 39gc から持つ（現在状態が確認中のときだけ値を持つ）。最終取得時刻は Bolt 39g3 から持つ（採用の実績がなければ NULL）。
 * 次の予定の実績の予定時刻は Bolt 39g4 から持つ（完了後と次の予定の実績がないときは NULL）。
 * 手続き中の段階は使う Bolt まで行に持たない（表では NULL）。
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
        OffsetDateTime lastAcquiredAt,
        OffsetDateTime nextExpectedAt,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}

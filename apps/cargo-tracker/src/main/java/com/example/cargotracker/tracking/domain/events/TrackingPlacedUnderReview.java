package com.example.cargotracker.tracking.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-18 追跡が確認中になった（US-12 AC3、T-INV-03。Bolt 39gc）。時系列に反する実績の登録・採用で追跡記録が確認中に入るときだけ、追跡記録の保存と
 * 同じトランザクションで発行する（すでに確認中なら出さない）。受け手（通知 {@code TRACKING_UNDER_REVIEW}）は W9。イベントはドメインの型を持たない。
 *
 * @param trackingNumber 追跡番号の表記
 * @param bookingId 予約 ID
 * @param underReviewReason 確認中の理由（{@code UnderReviewReason} の名前）
 * @param milestoneNo 確認中にした実績の実績番号
 * @param placedAt 確認中にした時刻（登録時刻か採用時刻）
 * @param aggregateVersion 発行元の集約の版（保存した後の版。B-INV-12）
 * @param shipperCompanyId 荷主企業 ID（荷主への通知の宛先を identity が引くため。W9 の Bolt 43-1 から。それ以前に発行したものは null）
 */
@DomainEvent
public record TrackingPlacedUnderReview(
        String trackingNumber,
        UUID bookingId,
        String underReviewReason,
        int milestoneNo,
        UtcInstant placedAt,
        long aggregateVersion,
        UUID shipperCompanyId) {

    /** 荷主企業を持たない形（それ以前に発行したものと、通知を使わない呼び出し）。 */
    public TrackingPlacedUnderReview(
            String trackingNumber,
            UUID bookingId,
            String underReviewReason,
            int milestoneNo,
            UtcInstant placedAt,
            long aggregateVersion) {
        this(trackingNumber, bookingId, underReviewReason, milestoneNo, placedAt, aggregateVersion, null);
    }
}

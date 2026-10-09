package com.example.cargotracker.tracking.domain.events;

import com.example.cargotracker.shared.annotation.ddd.DomainEvent;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.UUID;

/**
 * DE-22 追跡を開始した（ADR-015。Bolt 25）。追跡記録の保存と同じトランザクションで発行し、追跡の別の listener が受けて、予約の公開 API で
 * 予約サガを完了にする（2 つのコンテキストの集約を 1 つのトランザクションで更新しない。ADR-014）。業務キーは予約 ID。
 * イベントはドメインの型を持たない。
 *
 * @param trackingNumber 追跡番号の表記
 * @param bookingId 予約 ID
 * @param startedAt 開始時刻
 * @param aggregateVersion 発行元の集約の版（B-INV-12）
 */
@DomainEvent
public record TrackingStarted(String trackingNumber, UUID bookingId, UtcInstant startedAt, long aggregateVersion) {}

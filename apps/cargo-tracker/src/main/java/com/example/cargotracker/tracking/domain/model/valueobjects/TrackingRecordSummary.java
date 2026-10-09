package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 追跡記録の要約（S-11 追跡一覧の読み取りモデル。Bolt 26）。集約を組み立てずに、一覧に要る値だけを追跡記録の表から引く。前例
 * （{@code BookingSummary}・{@code RoutingCaseSummary} など）と同じく、ドメイン層の一覧の読み取りモデルで、自分の集約の表だけを使う。
 *
 * @param trackingNumber 追跡番号
 * @param currentStatus 現在状態
 * @param originalEta 当初の到着予定（確定した経路版。T-INV-10）
 * @param startedAt 追跡の開始時刻
 */
public record TrackingRecordSummary(
        TrackingNumber trackingNumber, TrackingStatus currentStatus, UtcInstant originalEta, UtcInstant startedAt) {

    public TrackingRecordSummary {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(originalEta, "originalEta");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}

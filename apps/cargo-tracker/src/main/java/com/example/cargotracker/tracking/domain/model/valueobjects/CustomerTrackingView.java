package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

/**
 * 荷主向けの追跡の照会結果。追跡記録を荷主の開示範囲に絞って示す値（ドメインモデルの「顧客向けに表示する(開示範囲) : 照会結果」の荷主の分。
 * BR-07、T-INV-08・09。Bolt 27）。現在状態・到着予定・予定区間・採用済みの主要実績だけを持ち、経路版（案件番号と版）などの社内の
 * 識別子を持たない。荷受人の開示範囲は荷受人の照会（US-10）で足す。
 *
 * @param trackingNumber 追跡番号
 * @param currentStatus 現在状態
 * @param originalEta 当初の到着予定
 * @param latestEta 最新の到着見込み
 * @param legs 予定区間（区間の順）
 * @param milestones 採用済みの主要実績（発生時刻の順）
 */
@ValueObject
public record CustomerTrackingView(
        TrackingNumber trackingNumber,
        TrackingStatus currentStatus,
        UtcInstant originalEta,
        UtcInstant latestEta,
        List<ScheduledLeg> legs,
        List<CustomerMilestone> milestones) {

    public CustomerTrackingView {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(originalEta, "originalEta");
        Objects.requireNonNull(latestEta, "latestEta");
        legs = List.copyOf(legs);
        milestones = List.copyOf(milestones);
    }
}

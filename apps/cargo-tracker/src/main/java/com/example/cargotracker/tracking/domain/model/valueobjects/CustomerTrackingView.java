package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 荷主向けの追跡の照会結果。追跡記録を荷主の開示範囲に絞って示す値（ドメインモデルの「顧客向けに表示する(開示範囲) : 照会結果」の荷主の分。
 * BR-07、T-INV-08・09。Bolt 27）。現在状態・到着予定・予定区間・採用済みの主要実績だけを持ち、経路版（案件番号と版）などの社内の
 * 識別子を持たない。荷受人の開示範囲は荷受人の照会（US-10）で足す。
 *
 * @param trackingNumber 追跡番号
 * @param currentStatus 現在状態
 * @param originalEta 当初の到着予定
 * @param latestEta 最新の到着見込み
 * @param lastAcquiredAt 最終取得時刻（採用の実績の出典の取得時刻と、受けた最後の区間の航海の知らせの取得時刻のうち最も新しいもの。なければ空。Bolt 39g3、
 *     航海の知らせは Bolt 39g4b。T-INV-09。荷主には予定区間（航海番号を含む）と航海の知らせで動いた最新の到着見込みをすでに示しているので、その情報の
 *     新しさを示しても開示の範囲は広がらない。他社の貨物の情報と出典の参照は示さない。【前の決定の変更】(23)）
 * @param legs 予定区間（区間の順）
 * @param milestones 採用済みの主要実績（発生時刻の順）
 * @param nextExpectedAt 次の予定の実績の予定時刻（荷主に示す「次回の更新」の目安。なければ空。US-09 AC2・AC3。W9 の Bolt 47）
 * @param freshnessExceeded 鮮度超過か（次の予定の実績の予定時刻に許容範囲を足した時刻以後。外部情報源が止まって更新が届かないときの荷主への
 *     「最新性を保証しない旨」の出し分け。読むときの判定で、集約は持たない。照会のサービスが入れる。US-09 AC3、FRS-02・03）
 */
@ValueObject
public record CustomerTrackingView(
        TrackingNumber trackingNumber,
        TrackingStatus currentStatus,
        UtcInstant originalEta,
        UtcInstant latestEta,
        Optional<UtcInstant> lastAcquiredAt,
        List<ScheduledLeg> legs,
        List<CustomerMilestone> milestones,
        Optional<UtcInstant> nextExpectedAt,
        boolean freshnessExceeded) {

    public CustomerTrackingView {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(originalEta, "originalEta");
        Objects.requireNonNull(latestEta, "latestEta");
        Objects.requireNonNull(lastAcquiredAt, "lastAcquiredAt");
        Objects.requireNonNull(nextExpectedAt, "nextExpectedAt");
        legs = List.copyOf(legs);
        milestones = List.copyOf(milestones);
    }

    /** 次の予定の実績の予定時刻と鮮度の判定を持たない形（それ以前の呼び出し）。 */
    public CustomerTrackingView(
            TrackingNumber trackingNumber,
            TrackingStatus currentStatus,
            UtcInstant originalEta,
            UtcInstant latestEta,
            Optional<UtcInstant> lastAcquiredAt,
            List<ScheduledLeg> legs,
            List<CustomerMilestone> milestones) {
        this(
                trackingNumber,
                currentStatus,
                originalEta,
                latestEta,
                lastAcquiredAt,
                legs,
                milestones,
                Optional.empty(),
                false);
    }

    /** 鮮度超過の判定を入れた同じ照会結果（照会のサービスが時計から判定する。集約は持たない）。 */
    public CustomerTrackingView withFreshnessExceeded(boolean exceeded) {
        return new CustomerTrackingView(
                trackingNumber,
                currentStatus,
                originalEta,
                latestEta,
                lastAcquiredAt,
                legs,
                milestones,
                nextExpectedAt,
                exceeded);
    }
}

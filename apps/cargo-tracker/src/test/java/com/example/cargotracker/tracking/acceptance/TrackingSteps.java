package com.example.cargotracker.tracking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import io.cucumber.java.ja.ならば;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

/** 追跡の開始の受入シナリオのステップ（ADR-015、T-INV-10〜12。Bolt 25）。 */
public class TrackingSteps {

    private final InMemoryTrackingRecordRepository trackingRecords;
    private final Clock clock;

    public TrackingSteps(InMemoryTrackingRecordRepository trackingRecords, Clock clock) {
        this.trackingRecords = trackingRecords;
        this.clock = clock;
    }

    /** Bolt 39g3 の行 21（T-INV-10）。保存した追跡記録を読み直して確かめる。 */
    @ならば("追跡の最新の到着見込みは {string} で、当初の到着予定は {string} のままである")
    public void 最新の到着見込みと当初の到着予定(String latestEta, String originalEta) {
        TrackingRecord trackingRecord = onlyTrackingRecord();
        assertThat(trackingRecord.latestEta()).isEqualTo(new UtcInstant(Instant.parse(latestEta)));
        assertThat(trackingRecord.originalEta()).isEqualTo(new UtcInstant(Instant.parse(originalEta)));
    }

    /** Bolt 39g3 の行 21（【業務の判断】(ap)）。手入力の出典の取得時刻は登録した時刻（現在時刻）。 */
    @ならば("追跡の最終取得時刻は現在時刻である")
    public void 最終取得時刻は現在時刻() {
        assertThat(onlyTrackingRecord().lastAcquiredAt()).contains(new UtcInstant(clock.instant()));
    }

    @ならば("追跡の最終取得時刻はまだない")
    public void 最終取得時刻はまだない() {
        assertThat(onlyTrackingRecord().lastAcquiredAt()).isEmpty();
    }

    private TrackingRecord onlyTrackingRecord() {
        assertThat(trackingRecords.all()).hasSize(1);
        return trackingRecords.all().getFirst();
    }

    @ならば("本予約の追跡記録が {int} 件でき、予定は経路版 {string} 版 {int} の区間 {string} である")
    public void 追跡記録ができる(int count, String routingCaseNumber, int routeVersionNo, String voyages) {
        assertThat(trackingRecords.all()).hasSize(count);
        TrackingRecord trackingRecord = trackingRecords.all().getFirst();
        assertThat(trackingRecord.schedule().routingCaseNumber()).isEqualTo(routingCaseNumber);
        assertThat(trackingRecord.schedule().routeVersionNo()).isEqualTo(routeVersionNo);
        assertThat(trackingRecord.schedule().legs())
                .extracting(ScheduledLeg::voyageNumber)
                .containsExactlyElementsOf(Arrays.asList(voyages.split(",")));
    }

    @ならば("追跡の現在状態は {string} で、当初の到着予定と最新の見込みは {string} である")
    public void 現在状態と到着予定(String status, String eta) {
        TrackingRecord trackingRecord = trackingRecords.all().getFirst();
        assertThat(trackingRecord.currentStatus()).isEqualTo(status(status));
        UtcInstant expected = new UtcInstant(Instant.parse(eta));
        assertThat(trackingRecord.originalEta()).isEqualTo(expected);
        assertThat(trackingRecord.latestEta()).isEqualTo(expected);
    }

    @ならば("本予約の追跡記録は {int} 件だけある")
    public void 追跡記録の件数(int count) {
        assertThat(trackingRecords.all()).hasSize(count);
    }

    private static TrackingStatus status(String name) {
        return switch (name) {
            case "集荷予定" -> TrackingStatus.PICKUP_SCHEDULED;
            default -> throw new IllegalArgumentException("未知の追跡状態: " + name);
        };
    }
}

package com.example.cargotracker.tracking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import io.cucumber.java.ja.ならば;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** 追跡の開始の受入シナリオのステップ（ADR-015、T-INV-10〜12。Bolt 25）。 */
public class TrackingSteps {

    private final InMemoryTrackingRecordRepository trackingRecords;
    private final Clock clock;
    private final TrackingRecordQueryService queryService;

    public TrackingSteps(
            InMemoryTrackingRecordRepository trackingRecords, Clock clock, TrackingRecordQueryService queryService) {
        this.trackingRecords = trackingRecords;
        this.clock = clock;
        this.queryService = queryService;
    }

    /** Bolt 39g4 の行 20。保存した追跡記録を読み直して確かめる。 */
    @ならば("追跡の次の予定の実績の予定時刻は {string} である")
    public void 次の予定の実績の予定時刻(String nextExpectedAt) {
        assertThat(onlyTrackingRecord().nextExpectedAt()).contains(new UtcInstant(Instant.parse(nextExpectedAt)));
    }

    /** Bolt 39g4 の行 20（FRS-02）。追跡記録の荷主企業を担当する追跡管理者が、現在時刻に追跡一覧（S-11）を開く。 */
    @ならば("追跡一覧で追跡番号 {string} は鮮度超過である")
    public void 鮮度超過である(String trackingNumber) {
        assertThat(overdueIn(trackingNumber)).isTrue();
    }

    @ならば("追跡一覧で追跡番号 {string} は鮮度超過でない")
    public void 鮮度超過でない(String trackingNumber) {
        assertThat(overdueIn(trackingNumber)).isFalse();
    }

    private boolean overdueIn(String trackingNumber) {
        TrackingRecord trackingRecord = onlyTrackingRecord();
        RecentTrackingRecords recent = queryService.recent(AuthenticatedActorFixture.trackingManagerHandling(
                new UserId(UUID.randomUUID()), trackingRecord.shipperCompanyId()));
        TrackingRecordSummary row = recent.rows().stream()
                .filter(summary -> summary.trackingNumber().equals(new TrackingNumber(trackingNumber)))
                .findFirst()
                .orElseThrow();
        return recent.freshnessExceeded(row);
    }

    /** Bolt 39g3 の行 21（T-INV-10）。保存した追跡記録を読み直して確かめる。 */
    @ならば("追跡の最新の到着見込みは {string} で、当初の到着予定は {string} のままである")
    public void 最新の到着見込みと当初の到着予定(String latestEta, String originalEta) {
        TrackingRecord trackingRecord = onlyTrackingRecord();
        assertThat(trackingRecord.latestEta()).isEqualTo(new UtcInstant(Instant.parse(latestEta)));
        assertThat(trackingRecord.originalEta()).isEqualTo(new UtcInstant(Instant.parse(originalEta)));
    }

    /** Bolt 39g4c の行 11（T-INV-10。【業務の判断】(aw)）。保存した追跡記録を読み直して、見込みと一緒に保存した根拠を確かめる。 */
    @ならば("追跡の見込みの根拠は当初の到着予定である")
    public void 見込みの根拠は当初の到着予定() {
        assertThat(onlyTrackingRecord().latestEstimateBasis())
                .contains(
                        com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis.originalEta());
    }

    @ならば("追跡の見込みの根拠は実績 {int} である")
    public void 見込みの根拠は実績(int milestoneNo) {
        assertThat(onlyTrackingRecord().latestEstimateBasis())
                .contains(com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis.milestone(
                        milestoneNo));
    }

    @ならば("追跡の見込みの根拠は航海の到着予定である")
    public void 見込みの根拠は航海の到着予定() {
        assertThat(onlyTrackingRecord().latestEstimateBasis())
                .contains(
                        com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis
                                .voyageArrival());
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

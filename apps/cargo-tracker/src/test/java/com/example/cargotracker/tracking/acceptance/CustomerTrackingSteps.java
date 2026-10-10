package com.example.cargotracker.tracking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerTrackingQueryService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingRecordSummary;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 荷主の追跡の照会の受入シナリオのステップ（US-09 AC1、BR-07。Bolt 27）。 */
public class CustomerTrackingSteps {

    /** 受入シナリオの荷主担当者の企業。 */
    static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002701"));

    /** 他社の荷主担当者の企業（前例の語と定数。BR-07）。 */
    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002702"));

    private final InMemoryTrackingRecordRepository trackingRecords;
    private final CustomerTrackingQueryService queryService;
    private Optional<CustomerTrackingView> found = Optional.empty();
    private List<TrackingRecordSummary> list = List.of();

    public CustomerTrackingSteps(
            InMemoryTrackingRecordRepository trackingRecords, CustomerTrackingQueryService queryService) {
        this.trackingRecords = trackingRecords;
        this.queryService = queryService;
    }

    @前提("荷主担当者の追跡番号 {string} の追跡を開始した追跡記録がある")
    public void 荷主担当者の追跡記録がある(String trackingNumber) {
        save(trackingNumber, SHIPPER);
    }

    @前提("他社の荷主担当者の追跡番号 {string} の追跡を開始した追跡記録がある")
    public void 他社の荷主担当者の追跡記録がある(String trackingNumber) {
        save(trackingNumber, OTHER_SHIPPER);
    }

    @もし("荷主担当者が追跡番号 {string} を照会する")
    public void 荷主担当者が照会する(String trackingNumber) {
        found = queryService.find(new TrackingNumber(trackingNumber), SHIPPER);
    }

    @もし("他社の荷主担当者が追跡番号 {string} を照会する")
    public void 他社の荷主担当者が照会する(String trackingNumber) {
        found = queryService.find(new TrackingNumber(trackingNumber), OTHER_SHIPPER);
    }

    @もし("荷主担当者が追跡の一覧を開く")
    public void 荷主担当者が一覧を開く() {
        list = queryService.recent(SHIPPER).rows();
    }

    @ならば("照会の結果の現在状態は {string} で、予定区間の航海は {string} である")
    public void 現在状態と予定区間(String status, String voyages) {
        CustomerTrackingView view = found.orElseThrow(() -> new AssertionError("照会の結果がない"));
        assertThat(view.currentStatus()).isEqualTo(status(status));
        assertThat(view.legs())
                .extracting(ScheduledLeg::voyageNumber)
                .containsExactlyElementsOf(Arrays.asList(voyages.split(",")));
    }

    @ならば("照会の結果の主要実績は {int} 件で、{string}・{string}・発生時刻 {string}・出典 {string} の参照 {string}・取得時刻 {string} である")
    @SuppressWarnings("java:S107") // シナリオの 1 文の値をそのまま受け取る
    public void 主要実績(
            int count,
            String kind,
            String location,
            String occurredAt,
            String sourceKind,
            String reference,
            String acquiredAt) {
        CustomerTrackingView view = found.orElseThrow(() -> new AssertionError("照会の結果がない"));
        assertThat(view.milestones()).hasSize(count);
        CustomerMilestone milestone = view.milestones().getFirst();
        assertThat(milestone.kind()).isEqualTo(kind(kind));
        assertThat(milestone.location()).isEqualTo(new Location(location));
        assertThat(milestone.occurredAt()).isEqualTo(at(occurredAt));
        assertThat(milestone.source().kind()).isEqualTo(sourceKind(sourceKind));
        assertThat(milestone.source().reference()).isEqualTo(reference);
        assertThat(milestone.source().acquiredAt()).isEqualTo(at(acquiredAt));
    }

    @ならば("追跡記録は見つからない")
    public void 見つからない() {
        assertThat(found).isEmpty();
    }

    @ならば("追跡の一覧の追跡番号は {string} だけである")
    public void 一覧の追跡番号(String trackingNumbers) {
        assertThat(list)
                .extracting(summary -> summary.trackingNumber().value())
                .containsExactlyElementsOf(Arrays.asList(trackingNumbers.split(",")));
    }

    private void save(String trackingNumber, CompanyId shipper) {
        trackingRecords.save(TrackingRecord.start(
                        new TrackingNumber(trackingNumber),
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002703")),
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord());
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private static TrackingStatus status(String name) {
        return switch (name) {
            case "集荷済み" -> TrackingStatus.PICKED_UP;
            default -> throw new IllegalArgumentException("未知の追跡状態: " + name);
        };
    }

    private static MilestoneKind kind(String name) {
        return switch (name) {
            case "集荷" -> MilestoneKind.PICKUP;
            default -> throw new IllegalArgumentException("未知の実績の種類: " + name);
        };
    }

    private static SourceKind sourceKind(String name) {
        return switch (name) {
            case "現場記録" -> SourceKind.FIELD_RECORD;
            default -> throw new IllegalArgumentException("未知の出典の種類: " + name);
        };
    }
}

package com.example.cargotracker.tracking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneRegistrationOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 主要実績の登録の受入シナリオのステップ（US-12 AC1・AC2。Bolt 26b）。 */
public class MilestoneSteps {

    /** 受入シナリオの追跡管理者。 */
    private static final UserId TRACKING_MANAGER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000026b01"));

    private final InMemoryTrackingRecordRepository trackingRecords;
    private final TrackingRecordCommandService commandService;
    private final Clock clock;
    private MilestoneRegistrationOutcome outcome;

    public MilestoneSteps(
            InMemoryTrackingRecordRepository trackingRecords,
            TrackingRecordCommandService commandService,
            Clock clock) {
        this.trackingRecords = trackingRecords;
        this.commandService = commandService;
        this.clock = clock;
    }

    @前提("追跡番号 {string} の追跡を開始した追跡記録がある")
    public void 追跡を開始した追跡記録がある(String trackingNumber) {
        trackingRecords.save(TrackingRecord.start(
                        new TrackingNumber(trackingNumber),
                        UUID.fromString("00000000-0000-0000-0000-000000026b02"),
                        new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000026b03")),
                        new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000026b04")),
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord());
    }

    @もし("追跡管理者が追跡番号 {string} に実績 {string} を場所 {string}・発生時刻 {string}・出典 {string} の参照 {string} で登録する")
    public void 実績を登録する(
            String trackingNumber,
            String kind,
            String location,
            String occurredAt,
            String sourceKind,
            String reference) {
        TrackingRecord trackingRecord = trackingRecords
                .findByTrackingNumber(new TrackingNumber(trackingNumber))
                .orElseThrow();
        outcome = commandService.registerMilestone(new RegisterMilestoneCommand(
                trackingRecord.trackingNumber(),
                trackingRecord.aggregateVersion(),
                kind(kind),
                new Location(location),
                at(occurredAt),
                new Source(sourceKind(sourceKind), reference, new UtcInstant(clock.instant())),
                TRACKING_MANAGER));
    }

    @ならば("実績 {int} が登録される")
    public void 実績が登録される(int milestoneNo) {
        assertThat(outcome)
                .isInstanceOfSatisfying(
                        MilestoneRegistrationOutcome.Registered.class,
                        registered -> assertThat(registered.milestoneNo()).isEqualTo(milestoneNo));
    }

    @ならば("登録済みの実績 {int} が返される")
    public void 登録済みの実績が返される(int milestoneNo) {
        assertThat(outcome).isEqualTo(new MilestoneRegistrationOutcome.AlreadyRegistered(milestoneNo));
    }

    @ならば("追跡記録の主要実績は {int} 件で、実績 {int} は {string}・{string}・発生時刻 {string}・出典 {string} の参照 {string}・状態 {string} である")
    @SuppressWarnings("java:S107") // シナリオの 1 文の値をそのまま受け取る
    public void 主要実績の内容(
            int count,
            int milestoneNo,
            String kind,
            String location,
            String occurredAt,
            String sourceKind,
            String reference,
            String state) {
        TrackingRecord trackingRecord = onlyTrackingRecord();
        assertThat(trackingRecord.milestones()).hasSize(count);
        Milestone milestone = trackingRecord.milestones().get(milestoneNo - 1);
        assertThat(milestone.milestoneNo()).isEqualTo(milestoneNo);
        assertThat(milestone.kind()).isEqualTo(kind(kind));
        assertThat(milestone.location()).isEqualTo(new Location(location));
        assertThat(milestone.occurredAt()).isEqualTo(at(occurredAt));
        assertThat(milestone.source().kind()).isEqualTo(sourceKind(sourceKind));
        assertThat(milestone.source().reference()).isEqualTo(reference);
        assertThat(milestone.source().acquiredAt()).isEqualTo(new UtcInstant(clock.instant()));
        assertThat(milestone.state()).isEqualTo(state(state));
        assertThat(milestone.registeredBy()).isEqualTo(TRACKING_MANAGER);
        assertThat(milestone.registeredAt()).isEqualTo(new UtcInstant(clock.instant()));
    }

    @ならば("追跡記録の主要実績は {int} 件だけある")
    public void 主要実績の件数(int count) {
        assertThat(onlyTrackingRecord().milestones()).hasSize(count);
    }

    @ならば("追跡の現在状態は {string} になる")
    public void 現在状態になる(String status) {
        assertThat(onlyTrackingRecord().currentStatus()).isEqualTo(status(status));
    }

    private TrackingRecord onlyTrackingRecord() {
        assertThat(trackingRecords.all()).hasSize(1);
        return trackingRecords.all().getFirst();
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private static MilestoneKind kind(String name) {
        return switch (name) {
            case "集荷" -> MilestoneKind.PICKUP;
            case "搬入" -> MilestoneKind.RECEIPT_AT_ORIGIN;
            case "出発" -> MilestoneKind.DEPARTURE;
            case "積替" -> MilestoneKind.TRANSSHIPMENT;
            case "到着" -> MilestoneKind.ARRIVAL;
            case "引渡し" -> MilestoneKind.DELIVERY;
            default -> throw new IllegalArgumentException("未知の実績の種類: " + name);
        };
    }

    private static SourceKind sourceKind(String name) {
        return switch (name) {
            case "外部原本" -> SourceKind.EXTERNAL_RECORD;
            case "現場記録" -> SourceKind.FIELD_RECORD;
            case "社内確認" -> SourceKind.INTERNAL_CHECK;
            case "手動入力" -> SourceKind.MANUAL_ENTRY;
            default -> throw new IllegalArgumentException("未知の出典の種類: " + name);
        };
    }

    private static MilestoneState state(String name) {
        return switch (name) {
            case "採用" -> MilestoneState.ADOPTED;
            default -> throw new IllegalArgumentException("未知の実績の状態: " + name);
        };
    }

    private static TrackingStatus status(String name) {
        return switch (name) {
            case "集荷済み" -> TrackingStatus.PICKED_UP;
            default -> throw new IllegalArgumentException("未知の追跡状態: " + name);
        };
    }
}

package com.example.cargotracker.tracking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.RegisterMilestoneCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneAdoptionOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.MilestoneRegistrationOutcome;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
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

    /** シナリオの追跡記録の荷主企業（追跡管理者はこの企業を担当する。Bolt 38g）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000026b03"));

    /** 受入シナリオの追跡管理者。 */
    private static final UserId TRACKING_MANAGER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000026b01"));

    private final InMemoryTrackingRecordRepository trackingRecords;
    private final TrackingRecordCommandService commandService;
    private final InMemoryRejectedMilestoneRepository rejectedMilestones;
    private final ExternalMilestoneArrival externalMilestoneArrival;
    private final Clock clock;
    private MilestoneRegistrationOutcome outcome;
    private MilestoneAdoptionOutcome adoptionOutcome;

    public MilestoneSteps(
            InMemoryTrackingRecordRepository trackingRecords,
            TrackingRecordCommandService commandService,
            InMemoryRejectedMilestoneRepository rejectedMilestones,
            ExternalMilestoneArrival externalMilestoneArrival,
            Clock clock) {
        this.trackingRecords = trackingRecords;
        this.commandService = commandService;
        this.rejectedMilestones = rejectedMilestones;
        this.externalMilestoneArrival = externalMilestoneArrival;
        this.clock = clock;
    }

    @前提("追跡番号 {string} の追跡を開始した追跡記録がある")
    public void 追跡を開始した追跡記録がある(String trackingNumber) {
        trackingRecords.save(TrackingRecord.start(
                        new TrackingNumber(trackingNumber),
                        UUID.fromString("00000000-0000-0000-0000-000000026b02"),
                        SHIPPER,
                        new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000026b04")),
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord());
    }

    /** Bolt 39g の行 46。予約状態の書き手（DE-08）は W11 なので、取消済みの追跡記録を直接作る（W7 の確認ポイント 9）。 */
    @前提("追跡番号 {string} の予約は取消済みである")
    public void 予約は取消済みである(String trackingNumber) {
        TrackingRecord started = trackingRecords
                .findByTrackingNumber(new TrackingNumber(trackingNumber))
                .orElseThrow();
        trackingRecords.clear();
        trackingRecords.save(TrackingRecord.reconstitute(
                started.trackingNumber(),
                started.bookingId(),
                started.shipperCompanyId(),
                started.consigneeCompanyId(),
                TrackedBookingStatus.CANCELLED,
                started.schedule(),
                started.currentStatus(),
                started.statusBasisMilestoneNo(),
                started.milestones(),
                started.originalEta(),
                started.latestEta(),
                started.startedAt(),
                started.aggregateVersion()));
    }

    @もし("追跡番号 {string} に外部原本の主要実績が届く")
    public void 外部原本の主要実績が届く(String trackingNumber) {
        UtcInstant now = new UtcInstant(clock.instant());
        externalMilestoneArrival.arrive(new ExternalMilestone(
                new TrackingNumber(trackingNumber),
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                at("2026-11-01T02:00:00Z"),
                new Source(SourceKind.EXTERNAL_RECORD, "00000000-0000-0000-0000-000000026b06", now),
                new ExternalOrigin(UUID.fromString("00000000-0000-0000-0000-000000026b05"), "MS-0001", now),
                new UserId(UUID.fromString("00000000-0000-0000-0000-000000026b07"))));
    }

    @ならば("拒否の記録が理由 {string} で {int} 件ある")
    public void 拒否の記録がある(String reason, int count) {
        assertThat(rejectedMilestones.all()).hasSize(count);
        assertThat(rejectedMilestones.all())
                .allSatisfy(rejected -> assertThat(rejected.reason()).isEqualTo(reason(reason)));
    }

    /** Bolt 39gb の行 63。同じ情報源・同じ外部 ID の主要実績の DE-13（受信記録 ID は公開した時刻ごとに違う）。発生時刻は現在時刻より前。 */
    @前提("追跡番号 {string} に場所 {string} の外部原本の主要実績が公開した時刻 {string} で届く")
    public void 公開した時刻の外部原本の主要実績が届く(String trackingNumber, String location, String publishedAt) {
        externalMilestoneArrival.arrive(new ExternalMilestone(
                new TrackingNumber(trackingNumber),
                MilestoneKind.DEPARTURE,
                new Location(location),
                at("2026-11-01T02:00:00Z"),
                new Source(SourceKind.EXTERNAL_RECORD, "received-" + publishedAt, at(publishedAt)),
                new ExternalOrigin(UUID.fromString("00000000-0000-0000-0000-000000039b05"), "MS-0001", at(publishedAt)),
                new UserId(UUID.fromString("00000000-0000-0000-0000-000000039b07"))));
    }

    /** Bolt 39gb の行 63。画面を開いたときの版は今の版。 */
    @もし("追跡管理者が追跡番号 {string} の実績 {int} を採用する")
    public void 実績を採用する(String trackingNumber, int milestoneNo) {
        TrackingRecord trackingRecord = trackingRecords
                .findByTrackingNumber(new TrackingNumber(trackingNumber))
                .orElseThrow();
        adoptionOutcome = commandService.adoptCandidate(
                trackingRecord.trackingNumber(),
                milestoneNo,
                trackingRecord.aggregateVersion(),
                AuthenticatedActorFixture.trackingManagerHandling(TRACKING_MANAGER, trackingRecord.shipperCompanyId()));
    }

    @ならば("実績 {int} が採用される")
    public void 実績が採用される(int milestoneNo) {
        assertThat(adoptionOutcome)
                .isInstanceOfSatisfying(
                        MilestoneAdoptionOutcome.Adopted.class,
                        adopted -> assertThat(adopted.milestoneNo()).isEqualTo(milestoneNo));
        assertThat(onlyTrackingRecord().milestones().get(milestoneNo - 1).adoptedBy())
                .isEqualTo(TRACKING_MANAGER);
    }

    @ならば("実績 {int} は候補でないので採用されない")
    public void 候補でないので採用されない(int milestoneNo) {
        assertThat(adoptionOutcome)
                .isEqualTo(new MilestoneAdoptionOutcome.Rejected(AdoptionRejectionReason.NOT_CANDIDATE));
        assertThat(onlyTrackingRecord().milestones().get(milestoneNo - 1).state())
                .isEqualTo(MilestoneState.DRAFT);
    }

    @ならば("採用済みの実績 {int} と同じ値なので採用は要らないと返される")
    public void 採用済みと同じ値(int adoptedMilestoneNo) {
        assertThat(adoptionOutcome).isEqualTo(new MilestoneAdoptionOutcome.SameAsAdopted(adoptedMilestoneNo));
    }

    @ならば("実績 {int} の状態は {string} である")
    public void 実績の状態(int milestoneNo, String state) {
        assertThat(onlyTrackingRecord().milestones().get(milestoneNo - 1).state())
                .isEqualTo(state(state));
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
        outcome = commandService.registerMilestone(
                new RegisterMilestoneCommand(
                        trackingRecord.trackingNumber(),
                        trackingRecord.aggregateVersion(),
                        kind(kind),
                        new Location(location),
                        at(occurredAt),
                        new Source(sourceKind(sourceKind), reference, new UtcInstant(clock.instant())),
                        TRACKING_MANAGER),
                // 追跡管理者は登録する追跡記録の荷主企業を担当する（荷主の照会のシナリオの追跡記録はほかの荷主企業。Bolt 38g）
                AuthenticatedActorFixture.trackingManagerHandling(TRACKING_MANAGER, trackingRecord.shipperCompanyId()));
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
            case "下書き" -> MilestoneState.DRAFT;
            case "保持のみ" -> MilestoneState.RETAINED_ONLY;
            default -> throw new IllegalArgumentException("未知の実績の状態: " + name);
        };
    }

    private static TrackingStatus status(String name) {
        return switch (name) {
            case "集荷済み" -> TrackingStatus.PICKED_UP;
            case "輸送中" -> TrackingStatus.IN_TRANSIT;
            case "引渡し済み" -> TrackingStatus.DELIVERED;
            default -> throw new IllegalArgumentException("未知の追跡状態: " + name);
        };
    }

    private static MilestoneRejectionReason reason(String name) {
        return switch (name) {
            case "取消済み" -> MilestoneRejectionReason.BOOKING_CANCELLED;
            case "知らない追跡番号" -> MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER;
            default -> throw new IllegalArgumentException("未知の拒否の理由: " + name);
        };
    }
}

package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.annotation.ddd.CoreConcept;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.rules.CurrentStatusDeriver;
import com.example.cargotracker.tracking.domain.model.valueobjects.DerivedStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * 追跡記録。1 つの追跡番号の予定と主要実績、現在状態を持つ（集約ルート。ADR-002・015。Bolt 25・26b）。予約 1 件に 1 件（T-INV-11）。
 * 本予約の確定（DE-07）を受けて、確定した経路版の区間を予定として採用して始める。現在状態は予定を採用した直後の集荷予定で始め（T-INV-08 の
 * 例外）、当初の到着予定と最新の見込みは最後の区間の到着予定で始める（T-INV-10）。
 *
 * <p>主要実績を登録すると、現在状態を採用済みの実績から導出し直す（T-INV-08。{@link CurrentStatusDeriver}）。追跡記録は不変で、登録は
 * 新しい追跡記録を返す。版はリポジトリが保存のときに増やす（楽観ロック）。
 */
@AggregateRoot
@CoreConcept
public final class TrackingRecord {

    private static final long INITIAL_AGGREGATE_VERSION = 0;
    private static final CurrentStatusDeriver DERIVER = new CurrentStatusDeriver();

    private final TrackingNumber trackingNumber;
    private final UUID bookingId;
    private final CompanyId shipperCompanyId;
    private final CompanyId consigneeCompanyId;
    private final TrackedBookingStatus bookingStatus;
    private final Schedule schedule;
    private final TrackingStatus currentStatus;
    private final OptionalInt statusBasisMilestoneNo;
    private final List<Milestone> milestones;
    private final UtcInstant originalEta;
    private final UtcInstant latestEta;
    private final UtcInstant startedAt;
    private final long aggregateVersion;

    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    private TrackingRecord(
            TrackingNumber trackingNumber,
            UUID bookingId,
            CompanyId shipperCompanyId,
            CompanyId consigneeCompanyId,
            TrackedBookingStatus bookingStatus,
            Schedule schedule,
            TrackingStatus currentStatus,
            OptionalInt statusBasisMilestoneNo,
            List<Milestone> milestones,
            UtcInstant originalEta,
            UtcInstant latestEta,
            UtcInstant startedAt,
            long aggregateVersion) {
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.consigneeCompanyId = Objects.requireNonNull(consigneeCompanyId, "consigneeCompanyId");
        this.bookingStatus = Objects.requireNonNull(bookingStatus, "bookingStatus");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.currentStatus = Objects.requireNonNull(currentStatus, "currentStatus");
        this.statusBasisMilestoneNo = Objects.requireNonNull(statusBasisMilestoneNo, "statusBasisMilestoneNo");
        this.milestones = List.copyOf(milestones);
        this.originalEta = Objects.requireNonNull(originalEta, "originalEta");
        this.latestEta = Objects.requireNonNull(latestEta, "latestEta");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.aggregateVersion = aggregateVersion;
        for (int i = 0; i < this.milestones.size(); i++) {
            if (this.milestones.get(i).milestoneNo() != i + 1) {
                throw new IllegalArgumentException("主要実績の実績番号は 1 からの連番です: " + trackingNumber.value());
            }
        }
    }

    /**
     * 予定を採用して追跡を始める（ADR-015）。
     *
     * @param trackingNumber 予約が発行した追跡番号
     * @param bookingId 予約 ID
     * @param shipperCompanyId 荷主企業 ID
     * @param consigneeCompanyId 荷受人企業 ID
     * @param schedule 確定した経路版の区間の予定
     * @param startedAt 開始時刻
     * @return 追跡記録と DE-22
     */
    public static TrackingStart start(
            TrackingNumber trackingNumber,
            UUID bookingId,
            CompanyId shipperCompanyId,
            CompanyId consigneeCompanyId,
            Schedule schedule,
            UtcInstant startedAt) {
        Objects.requireNonNull(schedule, "schedule");
        TrackingRecord trackingRecord = new TrackingRecord(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                TrackedBookingStatus.CONFIRMED,
                schedule,
                TrackingStatus.PICKUP_SCHEDULED,
                OptionalInt.empty(),
                List.of(),
                schedule.finalArrival(),
                schedule.finalArrival(),
                startedAt,
                INITIAL_AGGREGATE_VERSION);
        return new TrackingStart(
                trackingRecord,
                new TrackingStarted(trackingNumber.value(), bookingId, startedAt, trackingRecord.aggregateVersion));
    }

    /** 保存されている状態から組み立てる（リポジトリが使う）。 */
    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    public static TrackingRecord reconstitute(
            TrackingNumber trackingNumber,
            UUID bookingId,
            CompanyId shipperCompanyId,
            CompanyId consigneeCompanyId,
            TrackedBookingStatus bookingStatus,
            Schedule schedule,
            TrackingStatus currentStatus,
            OptionalInt statusBasisMilestoneNo,
            List<Milestone> milestones,
            UtcInstant originalEta,
            UtcInstant latestEta,
            UtcInstant startedAt,
            long aggregateVersion) {
        return new TrackingRecord(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                bookingStatus,
                schedule,
                currentStatus,
                statusBasisMilestoneNo,
                milestones,
                originalEta,
                latestEta,
                startedAt,
                aggregateVersion);
    }

    /**
     * 出典と発生時刻を伴う主要実績を登録する（US-12 AC1・AC2。Bolt 26b）。追跡管理者が登録した実績は採用で、現在状態を導出し直す
     * （T-INV-08）。同じ出典識別子（出典の種類と参照）の実績があれば、新しい実績を作らず既存の実績を返す（T-INV-02）。
     *
     * @param kind 種類
     * @param location 場所
     * @param occurredAt 発生時刻
     * @param source 出典
     * @param registrant 登録者
     * @param registeredAt 登録時刻
     * @return 実績を足した追跡記録と登録した実績、または元の追跡記録と同じ出典の既存の実績
     * @throws MilestoneRegistrationRejected 発生時刻が登録時刻より後
     */
    public MilestoneRegistration registerMilestone(
            MilestoneKind kind,
            Location location,
            UtcInstant occurredAt,
            Source source,
            UserId registrant,
            UtcInstant registeredAt) {
        Optional<Milestone> sameSource = milestoneOf(source);
        if (sameSource.isPresent()) {
            return new MilestoneRegistration(this, sameSource.get(), true);
        }
        if (occurredAt.instant().isAfter(registeredAt.instant())) {
            throw new MilestoneRegistrationRejected(
                    MilestoneRejectionReason.OCCURRED_IN_FUTURE, "発生時刻が登録時刻より後の実績は登録できません: " + occurredAt.instant());
        }
        Milestone milestone = new Milestone(
                milestones.size() + 1,
                kind,
                location,
                occurredAt,
                source,
                MilestoneState.ADOPTED,
                registrant,
                registeredAt);
        List<Milestone> registered = new ArrayList<>(milestones);
        registered.add(milestone);
        DerivedStatus derived = DERIVER.derive(registered);
        TrackingRecord trackingRecord = new TrackingRecord(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                bookingStatus,
                schedule,
                derived.status(),
                derived.basisMilestoneNo(),
                registered,
                originalEta,
                latestEta,
                startedAt,
                aggregateVersion);
        return new MilestoneRegistration(trackingRecord, milestone, false);
    }

    /** 同じ出典識別子（出典の種類と参照）の実績（T-INV-02）。取得時刻は識別子に含めない。 */
    private Optional<Milestone> milestoneOf(Source source) {
        return milestones.stream()
                .filter(milestone -> milestone.source().kind() == source.kind()
                        && milestone.source().reference().equals(source.reference()))
                .findFirst();
    }

    public TrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public CompanyId shipperCompanyId() {
        return shipperCompanyId;
    }

    public CompanyId consigneeCompanyId() {
        return consigneeCompanyId;
    }

    public TrackedBookingStatus bookingStatus() {
        return bookingStatus;
    }

    public Schedule schedule() {
        return schedule;
    }

    public TrackingStatus currentStatus() {
        return currentStatus;
    }

    /** 現在状態の根拠の実績番号（実績から導出していなければ空。Bolt 26b）。 */
    public OptionalInt statusBasisMilestoneNo() {
        return statusBasisMilestoneNo;
    }

    /** 主要実績の列（実績番号の順。Bolt 26b）。 */
    public List<Milestone> milestones() {
        return milestones;
    }

    /** 当初の到着予定（確定した経路版。T-INV-10）。 */
    public UtcInstant originalEta() {
        return originalEta;
    }

    /** 最新の到着見込み（T-INV-10）。 */
    public UtcInstant latestEta() {
        return latestEta;
    }

    /** 追跡を開始した時刻（追跡記録の作成時刻）。 */
    public UtcInstant startedAt() {
        return startedAt;
    }

    public long aggregateVersion() {
        return aggregateVersion;
    }
}

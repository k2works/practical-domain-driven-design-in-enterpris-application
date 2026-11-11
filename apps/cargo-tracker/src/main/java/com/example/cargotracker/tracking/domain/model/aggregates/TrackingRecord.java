package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.annotation.ddd.CoreConcept;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.DeliveryConfirmed;
import com.example.cargotracker.tracking.domain.events.PickupMilestoneAdopted;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.rules.CurrentStatusDeriver;
import com.example.cargotracker.tracking.domain.model.rules.LatestEstimateRule;
import com.example.cargotracker.tracking.domain.model.rules.MilestoneSequenceRule;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.DerivedStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneGroup;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.UnderReviewReason;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>採用済みの実績と時系列に反する実績（{@link MilestoneSequenceRule}）は確認中で残し、現在状態を確認中にする（T-INV-03。Bolt 39gc）。確認中に入る
 * 登録・採用の結果に DE-18 を載せ、呼び手が保存と同じトランザクションで発行する。
 *
 * <p>状態が採用になった集荷・引渡しの実績では、登録・採用の結果に DE-09・DE-10 を載せる（Bolt 39g2。予約の状態と輸送段階は追跡の listener が予約の
 * 公開 API で進める。ADR-014）。追跡記録の予約状態の写しは完了にしない（【前の決定の変更】(17)。完了後の判定は採用済みの実績から導いた状態で効く）。
 *
 * <p>最新の到着見込みと最終取得時刻は、現在状態と同じく実績が変わったとき（現在状態を導出し直すとき）に採用の実績から導いて持ち、保存した値を読む
 * （Bolt 39g3。{@link LatestEstimateRule}。最終取得時刻は採用の実績の出典の取得時刻のうち最も新しいもの。【業務の判断】(ap)）。当初の到着予定は変えない
 * （T-INV-10）。DE-13 の下書きと完了後の保持のみは採用の実績を変えないので導出し直さない。
 */
@AggregateRoot
@CoreConcept
public final class TrackingRecord {

    private static final long INITIAL_AGGREGATE_VERSION = 0;
    private static final CurrentStatusDeriver DERIVER = new CurrentStatusDeriver();
    private static final MilestoneSequenceRule SEQUENCE = new MilestoneSequenceRule();
    private static final LatestEstimateRule ESTIMATE = new LatestEstimateRule();

    private final TrackingNumber trackingNumber;
    private final UUID bookingId;
    private final CompanyId shipperCompanyId;
    private final CompanyId consigneeCompanyId;
    private final TrackedBookingStatus bookingStatus;
    private final Schedule schedule;
    private final TrackingStatus currentStatus;
    private final OptionalInt statusBasisMilestoneNo;
    private final Optional<UnderReviewReason> underReviewReason;
    private final List<Milestone> milestones;
    private final UtcInstant originalEta;
    private final UtcInstant latestEta;
    private final Optional<UtcInstant> lastAcquiredAt;
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
            DerivedStatus current,
            List<Milestone> milestones,
            UtcInstant originalEta,
            UtcInstant latestEta,
            Optional<UtcInstant> lastAcquiredAt,
            UtcInstant startedAt,
            long aggregateVersion) {
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.shipperCompanyId = Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        this.consigneeCompanyId = Objects.requireNonNull(consigneeCompanyId, "consigneeCompanyId");
        this.bookingStatus = Objects.requireNonNull(bookingStatus, "bookingStatus");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        // 現在状態と確認中の理由がそろうことは DerivedStatus が守る（表の CHECK ck_tracking_record_under_review と同じ規則。Bolt 39gc）
        this.currentStatus = current.status();
        this.statusBasisMilestoneNo = current.basisMilestoneNo();
        this.underReviewReason = current.underReviewReason();
        this.milestones = List.copyOf(milestones);
        this.originalEta = Objects.requireNonNull(originalEta, "originalEta");
        this.latestEta = Objects.requireNonNull(latestEta, "latestEta");
        this.lastAcquiredAt = Objects.requireNonNull(lastAcquiredAt, "lastAcquiredAt");
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
                new DerivedStatus(TrackingStatus.PICKUP_SCHEDULED, OptionalInt.empty()),
                List.of(),
                schedule.finalArrival(),
                schedule.finalArrival(),
                Optional.empty(),
                startedAt,
                INITIAL_AGGREGATE_VERSION);
        return new TrackingStart(
                trackingRecord,
                new TrackingStarted(trackingNumber.value(), bookingId, startedAt, trackingRecord.aggregateVersion));
    }

    /**
     * 保存されている状態から組み立てる（リポジトリが使う）。現在状態が確認中なら理由を持つ（Bolt 39gc）。最新の到着見込みと最終取得時刻は保存した値を
     * そのまま持つ（Bolt 39g3）。
     */
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
            Optional<UnderReviewReason> underReviewReason,
            List<Milestone> milestones,
            UtcInstant originalEta,
            UtcInstant latestEta,
            Optional<UtcInstant> lastAcquiredAt,
            UtcInstant startedAt,
            long aggregateVersion) {
        return new TrackingRecord(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                bookingStatus,
                schedule,
                new DerivedStatus(currentStatus, statusBasisMilestoneNo, underReviewReason),
                milestones,
                originalEta,
                latestEta,
                lastAcquiredAt,
                startedAt,
                aggregateVersion);
    }

    /** 確認中でなく最終取得時刻のない保存されている状態から組み立てる（確認中の理由と最終取得時刻を持たない。Bolt 39gc・39g3 の前の形）。 */
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
        return reconstitute(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                bookingStatus,
                schedule,
                currentStatus,
                statusBasisMilestoneNo,
                Optional.empty(),
                milestones,
                originalEta,
                latestEta,
                Optional.empty(),
                startedAt,
                aggregateVersion);
    }

    /**
     * 出典と発生時刻を伴う主要実績を登録する（US-12 AC1〜AC5。Bolt 26b・39gc）。同じ出典識別子（出典の種類と参照）の実績があれば、新しい実績を作らず
     * 既存の実績を返す（T-INV-02）。判定の順は、同じ出典 → 取消済み（拒否。T-INV-04） → 未来の発生時刻（拒否。T-INV-13） → 完了後（保持のみ。現在状態を
     * 変えない。T-INV-05） → 時系列に反する（確認中。T-INV-03） → 採用（W7 CP8。拒否を先に、状態の選び分けを後にする）。採用と確認中は現在状態を導出し直す
     * （T-INV-08）。
     *
     * @param kind 種類
     * @param location 場所
     * @param occurredAt 発生時刻
     * @param source 出典
     * @param registrant 登録者
     * @param registeredAt 登録時刻
     * @return 実績を足した追跡記録と登録した実績、または元の追跡記録と同じ出典の既存の実績
     * @throws MilestoneRegistrationRejected 取消済みの予約、または発生時刻が登録時刻より後
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
            return MilestoneRegistration.withoutEvent(this, sameSource.get(), true);
        }
        if (bookingStatus == TrackedBookingStatus.CANCELLED) {
            throw new MilestoneRegistrationRejected(
                    MilestoneRejectionReason.BOOKING_CANCELLED, "取消済みの予約には主要実績を登録できません: " + trackingNumber.value());
        }
        if (occurredAt.instant().isAfter(registeredAt.instant())) {
            throw new MilestoneRegistrationRejected(
                    MilestoneRejectionReason.OCCURRED_IN_FUTURE, "発生時刻が登録時刻より後の実績は登録できません: " + occurredAt.instant());
        }
        int milestoneNo = milestones.size() + 1;
        if (isCompleted()) {
            Milestone retained = new Milestone(
                    milestoneNo,
                    kind,
                    location,
                    occurredAt,
                    source,
                    MilestoneState.RETAINED_ONLY,
                    registrant,
                    registeredAt);
            return MilestoneRegistration.withoutEvent(
                    withMilestones(milestonesPlus(retained), current()), retained, false);
        }
        Milestone adopted = new Milestone(
                milestoneNo, kind, location, occurredAt, source, MilestoneState.ADOPTED, registrant, registeredAt);
        Milestone milestone = SEQUENCE.isOutOfSequence(adopted, milestones)
                ? new Milestone(
                        milestoneNo,
                        kind,
                        location,
                        occurredAt,
                        source,
                        MilestoneState.UNDER_REVIEW,
                        registrant,
                        registeredAt)
                : adopted;
        TrackingRecord registered = derivedWith(milestonesPlus(milestone));
        return new MilestoneRegistration(
                registered,
                milestone,
                false,
                placedUnderReview(registered, milestone, registeredAt),
                pickupAdopted(milestone, registrant),
                deliveryConfirmed(milestone, registrant));
    }

    /**
     * DE-13 の値を受け取る（US-12 AC4・AC5 の取り込みの分。Bolt 39g）。判定の順は、同じ出典 → 取消済み → 完了後 → 通常（決定 (7)。同じ出典を先に見るので、受けた後に
     * 状態が変わってから再配信されても結果が変わらない）。取消済みは実績を作らず、完了後は保持のみ、それ以外は下書きで足す。どちらも現在状態と根拠の実績番号を変えない
     * （T-INV-05・T-INV-08）。発生時刻が受けた時刻より後でも受ける（T-INV-13 は追跡管理者の登録の規則）。
     *
     * @param external 採用した主要実績の値
     * @param receivedAt 受けた時刻（実績の登録時刻）
     * @return 受け取りの結果
     */
    public ExternalMilestoneReceipt receiveExternalMilestone(ExternalMilestone external, UtcInstant receivedAt) {
        Optional<Milestone> sameSource = milestoneOf(external.source());
        if (sameSource.isPresent()) {
            return new ExternalMilestoneReceipt.AlreadyReceived(sameSource.get());
        }
        if (bookingStatus == TrackedBookingStatus.CANCELLED) {
            return new ExternalMilestoneReceipt.BookingCancelled();
        }
        Milestone milestone = new Milestone(
                milestones.size() + 1,
                external.kind(),
                external.location(),
                external.occurredAt(),
                external.source(),
                isCompleted() ? MilestoneState.RETAINED_ONLY : MilestoneState.DRAFT,
                external.operator(),
                receivedAt,
                external.origin());
        return new ExternalMilestoneReceipt.Received(withMilestones(milestonesPlus(milestone), current()), milestone);
    }

    /**
     * 外部原本から受けた下書きと判断済み（採用・確認中）の実績を組に分ける（Bolt 39gb。【業務の判断】(aa)。確認中は Bolt 39gc の【業務の判断】(ai)）。組は
     * 追跡番号の中で情報源 ID・外部 ID ごと。保持のみと手入力の実績は入れない。組の並びは組の最初の実績番号の順。
     */
    public List<MilestoneGroup> groupsOf() {
        Map<List<Object>, List<Milestone>> grouped = new LinkedHashMap<>();
        for (Milestone milestone : milestones) {
            ExternalOrigin origin = milestone.externalOrigin();
            if (origin != null && MilestoneGroup.isGroupState(milestone.state())) {
                grouped.computeIfAbsent(List.of(origin.dataSourceId(), origin.externalId()), key -> new ArrayList<>())
                        .add(milestone);
            }
        }
        return grouped.values().stream()
                .map(members -> new MilestoneGroup(
                        members.getFirst().externalOrigin().dataSourceId(),
                        members.getFirst().externalOrigin().externalId(),
                        members))
                .toList();
    }

    /** 置き換えられた下書きか（組の下書きで候補でない。S-12 に「後から公開された原本に置き換えられました」と示す。Bolt 39gb の行 43）。 */
    public boolean isSupersededDraft(Milestone milestone) {
        return groupOf(milestone).map(group -> group.isSuperseded(milestone)).orElse(false);
    }

    private Optional<MilestoneGroup> groupOf(Milestone milestone) {
        return groupsOf().stream()
                .filter(group -> group.members().contains(milestone))
                .findFirst();
    }

    /**
     * 追跡管理者が候補の下書きを採用する（US-12 AC1・AC2 の外部の分。Bolt 39gb）。採用すると現在状態と根拠の実績番号を導出し直す（T-INV-08）。実績は消えず増えない。
     * 判定の順は、候補でない → 取消済み → 完了後 → 判断済み（採用・確認中）と同じ値（何もしない） → 判断済みと値が違う（T-INV-06 を素通りさせない） →
     * 未来の発生時刻（T-INV-13 の採用の分） → 時系列に反する（確認中に移す。採用者と採用時刻は残す。T-INV-03。Bolt 39gc） → 採用。
     * 判定は純粋なので、画面は同じ操作を試して採用のボタンと理由を出し分ける。版はリポジトリが保存のときに増やす。
     *
     * @param milestoneNo 採用する実績の実績番号
     * @param adopter 採用者
     * @param adoptedAt 採用時刻
     * @return 採用の結果
     */
    public MilestoneAdoption adoptCandidate(int milestoneNo, UserId adopter, UtcInstant adoptedAt) {
        Optional<Milestone> target = milestones.stream()
                .filter(milestone -> milestone.milestoneNo() == milestoneNo)
                .findFirst();
        Optional<MilestoneGroup> candidateGroup =
                target.flatMap(this::groupOf).filter(group -> group.candidate().equals(target));
        if (candidateGroup.isEmpty()) {
            return new MilestoneAdoption.Rejected(AdoptionRejectionReason.NOT_CANDIDATE);
        }
        if (bookingStatus == TrackedBookingStatus.CANCELLED) {
            return new MilestoneAdoption.Rejected(AdoptionRejectionReason.BOOKING_CANCELLED);
        }
        if (isCompleted()) {
            return new MilestoneAdoption.Rejected(AdoptionRejectionReason.TRACKING_COMPLETED);
        }
        MilestoneGroup group = candidateGroup.get();
        Milestone candidate = group.candidate().orElseThrow();
        Optional<Milestone> adopted = group.adopted();
        if (adopted.isPresent()) {
            return adopted.get().hasSameValueAs(candidate)
                    ? new MilestoneAdoption.SameAsAdopted(adopted.get())
                    : new MilestoneAdoption.Rejected(AdoptionRejectionReason.CONFLICTS_WITH_ADOPTED, adopted);
        }
        if (candidate.occurredAt().instant().isAfter(adoptedAt.instant())) {
            return new MilestoneAdoption.Rejected(AdoptionRejectionReason.OCCURRED_IN_FUTURE);
        }
        Milestone adoptedMilestone = SEQUENCE.isOutOfSequence(candidate, milestones)
                ? candidate.adoptUnderReview(adopter, adoptedAt)
                : candidate.adopt(adopter, adoptedAt);
        TrackingRecord after = derivedWith(milestones.stream()
                .map(milestone -> milestone.milestoneNo() == milestoneNo ? adoptedMilestone : milestone)
                .toList());
        return new MilestoneAdoption.Adopted(
                after,
                adoptedMilestone,
                placedUnderReview(after, adoptedMilestone, adoptedAt),
                pickupAdopted(adoptedMilestone, adopter),
                deliveryConfirmed(adoptedMilestone, adopter));
    }

    /**
     * 完了後か（予約状態が完了か、採用済みの実績から導いた状態が引渡し済み。【業務の判断】(ad)、T-INV-05）。Bolt 39gc で現在状態から読み替えた
     * （確認中は現在状態を確認中にするので、現在状態を見ると確認中の間に引渡しを採用した追跡が完了後にならない。【前の決定の変更】(14)）。
     */
    private boolean isCompleted() {
        return bookingStatus == TrackedBookingStatus.COMPLETED || statusFromAdopted() == TrackingStatus.DELIVERED;
    }

    /** 採用済みの実績だけから導いた状態（確認中の実績を見ない。完了後の判定と S-12 の確認中の理由の文。Bolt 39gc）。 */
    public TrackingStatus statusFromAdopted() {
        return DERIVER.deriveFromAdopted(milestones).status();
    }

    /** 確認中に入ったときだけ DE-18 を作る（すでに確認中なら空。集約の版は保存した後の版）。 */
    private Optional<TrackingPlacedUnderReview> placedUnderReview(
            TrackingRecord after, Milestone milestone, UtcInstant placedAt) {
        if (currentStatus == TrackingStatus.UNDER_REVIEW || after.currentStatus != TrackingStatus.UNDER_REVIEW) {
            return Optional.empty();
        }
        return Optional.of(new TrackingPlacedUnderReview(
                trackingNumber.value(),
                bookingId,
                after.underReviewReason.orElseThrow().name(),
                milestone.milestoneNo(),
                placedAt,
                aggregateVersion + 1));
    }

    /**
     * 状態が採用になった集荷の実績なら DE-09 を作る（追跡が確認中でも出す。確認中・保持のみ・下書きでは出さない。【業務の判断】(al)。Bolt 39g2）。集約の版は
     * 保存した後の版（DE-18 と同じ読み方）。
     */
    private Optional<PickupMilestoneAdopted> pickupAdopted(Milestone milestone, UserId operator) {
        if (!isAdopted(milestone, MilestoneKind.PICKUP)) {
            return Optional.empty();
        }
        return Optional.of(new PickupMilestoneAdopted(
                trackingNumber.value(),
                bookingId,
                milestone.milestoneNo(),
                milestone.occurredAt(),
                operator.value(),
                aggregateVersion + 1));
    }

    /** 状態が採用になった引渡しの実績なら DE-10 を作る（DE-09 と同じ規則。Bolt 39g2）。 */
    private Optional<DeliveryConfirmed> deliveryConfirmed(Milestone milestone, UserId operator) {
        if (!isAdopted(milestone, MilestoneKind.DELIVERY)) {
            return Optional.empty();
        }
        return Optional.of(new DeliveryConfirmed(
                trackingNumber.value(),
                bookingId,
                milestone.milestoneNo(),
                milestone.occurredAt(),
                operator.value(),
                aggregateVersion + 1));
    }

    private static boolean isAdopted(Milestone milestone, MilestoneKind kind) {
        return milestone.state() == MilestoneState.ADOPTED && milestone.kind() == kind;
    }

    private DerivedStatus current() {
        return new DerivedStatus(currentStatus, statusBasisMilestoneNo, underReviewReason);
    }

    /** 実績を差し替え、現在状態と最新の到着見込みと最終取得時刻を導出し直した追跡記録（Bolt 39g3）。 */
    private TrackingRecord derivedWith(List<Milestone> newMilestones) {
        return withMilestones(
                newMilestones,
                DERIVER.derive(newMilestones),
                ESTIMATE.estimate(schedule, newMilestones),
                lastAcquiredOf(newMilestones));
    }

    /** 採用の実績の出典の取得時刻のうち最も新しいもの（発生時刻の順と独立。下書き・確認中・保持のみは使わない。【業務の判断】(ap)）。 */
    private static Optional<UtcInstant> lastAcquiredOf(List<Milestone> milestones) {
        return milestones.stream()
                .filter(milestone -> milestone.state() == MilestoneState.ADOPTED)
                .map(milestone -> milestone.source().acquiredAt())
                .max(Comparator.comparing(UtcInstant::instant));
    }

    private List<Milestone> milestonesPlus(Milestone milestone) {
        List<Milestone> plus = new ArrayList<>(milestones);
        plus.add(milestone);
        return plus;
    }

    /** 実績を差し替え、現在状態と最新の到着見込みと最終取得時刻を保存した値のまま持つ追跡記録（採用の実績が変わらない DE-13 の下書きと保持のみ）。 */
    private TrackingRecord withMilestones(List<Milestone> newMilestones, DerivedStatus newCurrent) {
        return withMilestones(newMilestones, newCurrent, latestEta, lastAcquiredAt);
    }

    private TrackingRecord withMilestones(
            List<Milestone> newMilestones,
            DerivedStatus newCurrent,
            UtcInstant newLatestEta,
            Optional<UtcInstant> newLastAcquiredAt) {
        return new TrackingRecord(
                trackingNumber,
                bookingId,
                shipperCompanyId,
                consigneeCompanyId,
                bookingStatus,
                schedule,
                newCurrent,
                newMilestones,
                originalEta,
                newLatestEta,
                newLastAcquiredAt,
                startedAt,
                aggregateVersion);
    }

    /**
     * 荷主向けに表示する（ドメインモデルの「顧客向けに表示する(開示範囲) : 照会結果」の荷主の分。BR-07、T-INV-08・09。Bolt 27）。
     * 主要実績は採用済みだけを発生時刻の順（同じ時刻なら登録の順）に、荷主に見せてよい項目だけで示す。経路版などの社内の識別子は含めない。
     * 開示範囲（荷主・荷受人）で項目を分けるのは荷受人の照会（US-10）で足す。
     */
    public CustomerTrackingView customerView() {
        List<CustomerMilestone> adopted = milestones.stream()
                .filter(milestone -> milestone.state() == MilestoneState.ADOPTED)
                .sorted(Comparator.comparing(
                                (Milestone milestone) -> milestone.occurredAt().instant())
                        .thenComparingInt(Milestone::milestoneNo))
                .map(milestone -> new CustomerMilestone(
                        milestone.kind(), milestone.location(), milestone.occurredAt(), milestone.source()))
                .toList();
        return new CustomerTrackingView(
                trackingNumber, currentStatus, originalEta, latestEta, lastAcquiredAt, schedule.legs(), adopted);
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

    /** 確認中の理由（現在状態が確認中のときだけ。Bolt 39gc）。 */
    public Optional<UnderReviewReason> underReviewReason() {
        return underReviewReason;
    }

    /** 主要実績の列（実績番号の順。Bolt 26b）。 */
    public List<Milestone> milestones() {
        return milestones;
    }

    /** 当初の到着予定（確定した経路版。T-INV-10）。 */
    public UtcInstant originalEta() {
        return originalEta;
    }

    /** 最新の到着見込み（T-INV-10。Bolt 39g3 から採用の実績で動く）。 */
    public UtcInstant latestEta() {
        return latestEta;
    }

    /** 最終取得時刻（採用の実績の出典の取得時刻のうち最も新しいもの。採用の実績がなければ空。Bolt 39g3）。 */
    public Optional<UtcInstant> lastAcquiredAt() {
        return lastAcquiredAt;
    }

    /** 追跡を開始した時刻（追跡記録の作成時刻）。 */
    public UtcInstant startedAt() {
        return startedAt;
    }

    public long aggregateVersion() {
        return aggregateVersion;
    }
}

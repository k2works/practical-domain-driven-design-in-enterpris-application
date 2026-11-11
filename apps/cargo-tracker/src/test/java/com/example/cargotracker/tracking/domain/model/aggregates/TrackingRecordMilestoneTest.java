package com.example.cargotracker.tracking.domain.model.aggregates;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.BOOKING_ID;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.UnderReviewReason;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 主要実績の登録（US-12 AC1・AC2、T-INV-02・T-INV-08。Bolt 26b）。 */
class TrackingRecordMilestoneTest {

    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000026b01"));
    private static final UtcInstant NOW = at("2026-11-01T03:00:00Z");

    @Test
    void 実績を登録すると実績番号1の採用済みの実績になり現在状態と根拠の実績番号を導出し直す() {
        TrackingRecord before = started();

        MilestoneRegistration registration = before.registerMilestone(
                MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-118"), REGISTRANT, NOW);

        Milestone milestone = registration.milestone();
        assertThat(registration.alreadyRegistered()).isFalse();
        assertThat(milestone.milestoneNo()).isEqualTo(1);
        assertThat(milestone.kind()).isEqualTo(MilestoneKind.PICKUP);
        assertThat(milestone.location()).isEqualTo(tokyo());
        assertThat(milestone.occurredAt()).isEqualTo(at("2026-11-01T02:30:00Z"));
        assertThat(milestone.source()).isEqualTo(source("F-118"));
        assertThat(milestone.state()).isEqualTo(MilestoneState.ADOPTED);
        assertThat(milestone.registeredBy()).isEqualTo(REGISTRANT);
        assertThat(milestone.registeredAt()).isEqualTo(NOW);
        TrackingRecord after = registration.trackingRecord();
        assertThat(after.milestones()).containsExactly(milestone);
        assertThat(after.currentStatus()).isEqualTo(TrackingStatus.PICKED_UP);
        assertThat(after.statusBasisMilestoneNo()).isEqualTo(OptionalInt.of(1));
    }

    @Test
    void 登録しても元の追跡記録は変わらず版はリポジトリが増やすので変えない() {
        TrackingRecord before = started();

        TrackingRecord after = before.registerMilestone(
                        MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-118"), REGISTRANT, NOW)
                .trackingRecord();

        assertThat(before.milestones()).isEmpty();
        assertThat(before.currentStatus()).isEqualTo(TrackingStatus.PICKUP_SCHEDULED);
        assertThat(before.statusBasisMilestoneNo()).isEmpty();
        assertThat(after.aggregateVersion()).isEqualTo(before.aggregateVersion());
        assertThat(after.trackingNumber()).isEqualTo(before.trackingNumber());
        assertThat(after.schedule()).isEqualTo(before.schedule());
        assertThat(after.originalEta()).isEqualTo(before.originalEta());
        assertThat(after.latestEta()).isEqualTo(before.latestEta());
    }

    @Test
    void 実績番号は登録の順に1から増える() {
        TrackingRecord first = started()
                .registerMilestone(
                        MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-118"), REGISTRANT, NOW)
                .trackingRecord();

        MilestoneRegistration second = first.registerMilestone(
                MilestoneKind.RECEIPT_AT_ORIGIN, tokyo(), at("2026-11-01T02:50:00Z"), source("F-119"), REGISTRANT, NOW);

        assertThat(second.milestone().milestoneNo()).isEqualTo(2);
        assertThat(second.trackingRecord().milestones())
                .extracting(Milestone::milestoneNo)
                .containsExactly(1, 2);
        assertThat(second.trackingRecord().currentStatus()).isEqualTo(TrackingStatus.RECEIVED_AT_ORIGIN);
        assertThat(second.trackingRecord().statusBasisMilestoneNo()).isEqualTo(OptionalInt.of(2));
    }

    @Test
    void 同じ出典の種類と参照の実績があれば新しい実績を作らず既存の実績を返す() {
        TrackingRecord registered = started()
                .registerMilestone(
                        MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-118"), REGISTRANT, NOW)
                .trackingRecord();

        MilestoneRegistration again = registered.registerMilestone(
                MilestoneKind.DEPARTURE, tokyo(), at("2026-11-01T02:40:00Z"), source("F-118"), REGISTRANT, NOW);

        assertThat(again.alreadyRegistered()).isTrue();
        assertThat(again.milestone()).isEqualTo(registered.milestones().getFirst());
        assertThat(again.trackingRecord()).isSameAs(registered);
    }

    @Test
    void 参照が同じでも出典の種類が違えば別の実績として登録する() {
        TrackingRecord registered = started()
                .registerMilestone(
                        MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-118"), REGISTRANT, NOW)
                .trackingRecord();

        MilestoneRegistration other = registered.registerMilestone(
                MilestoneKind.RECEIPT_AT_ORIGIN,
                tokyo(),
                at("2026-11-01T02:40:00Z"),
                new Source(SourceKind.INTERNAL_CHECK, "F-118", NOW),
                REGISTRANT,
                NOW);

        assertThat(other.alreadyRegistered()).isFalse();
        assertThat(other.milestone().milestoneNo()).isEqualTo(2);
    }

    @Test
    void 発生時刻が登録時刻と同じなら受け付ける() {
        MilestoneRegistration registration =
                started().registerMilestone(MilestoneKind.PICKUP, tokyo(), NOW, source("F-118"), REGISTRANT, NOW);

        assertThat(registration.milestone().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void 発生時刻が登録時刻より後なら拒否する() {
        TrackingRecord trackingRecord = started();
        UtcInstant oneSecondLater = at("2026-11-01T03:00:01Z");

        assertThatThrownBy(() -> trackingRecord.registerMilestone(
                        MilestoneKind.PICKUP, tokyo(), oneSecondLater, source("F-118"), REGISTRANT, NOW))
                .isInstanceOfSatisfying(
                        MilestoneRegistrationRejected.class,
                        rejected ->
                                assertThat(rejected.reason()).isEqualTo(MilestoneRejectionReason.OCCURRED_IN_FUTURE));
    }

    @Test
    void 同じ出典の実績があれば発生時刻が未来でも拒否せず既存の実績を返す() {
        TrackingRecord registered = started()
                .registerMilestone(
                        MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-118"), REGISTRANT, NOW)
                .trackingRecord();

        MilestoneRegistration again = registered.registerMilestone(
                MilestoneKind.PICKUP, tokyo(), at("2026-11-01T03:00:01Z"), source("F-118"), REGISTRANT, NOW);

        assertThat(again.alreadyRegistered()).isTrue();
        assertThat(again.milestone().milestoneNo()).isEqualTo(1);
    }

    @Test
    void 実績番号が1からの連番でない追跡記録は組み立てられない() {
        TrackingRecord trackingRecord = started();
        TrackingNumber trackingNumber = trackingRecord.trackingNumber();
        UUID bookingId = trackingRecord.bookingId();
        CompanyId shipper = trackingRecord.shipperCompanyId();
        CompanyId consignee = trackingRecord.consigneeCompanyId();
        TrackedBookingStatus bookingStatus = trackingRecord.bookingStatus();
        Schedule schedule = trackingRecord.schedule();
        UtcInstant originalEta = trackingRecord.originalEta();
        UtcInstant latestEta = trackingRecord.latestEta();
        UtcInstant startedAt = trackingRecord.startedAt();
        OptionalInt basis = OptionalInt.of(2);
        Milestone second = new Milestone(
                2,
                MilestoneKind.PICKUP,
                tokyo(),
                at("2026-11-01T02:30:00Z"),
                source("F-118"),
                MilestoneState.ADOPTED,
                REGISTRANT,
                NOW);
        List<Milestone> milestones = List.of(second);

        assertThatThrownBy(() -> TrackingRecord.reconstitute(
                        trackingNumber,
                        bookingId,
                        shipper,
                        consignee,
                        bookingStatus,
                        schedule,
                        TrackingStatus.PICKED_UP,
                        basis,
                        milestones,
                        originalEta,
                        latestEta,
                        startedAt,
                        1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("連番");
    }

    /** Bolt 39gc の手入力の分（行 4・5・8〜11・16。US-12 AC3〜AC5、T-INV-03〜05）。 */
    @Nested
    class Bolt39gc_手入力の確認中と取消済みと完了後 {

        private static final UtcInstant LATER = at("2026-11-02T03:00:00Z");

        private TrackingRecord arrived() {
            return started()
                    .registerMilestone(
                            MilestoneKind.ARRIVAL, tokyo(), at("2026-11-01T02:00:00Z"), source("F-1"), REGISTRANT, NOW)
                    .trackingRecord();
        }

        @Test
        void 行5_時系列に反する実績は確認中で残り現在状態は確認中になりDE18が1件() {
            TrackingRecord before = arrived();

            MilestoneRegistration registration = before.registerMilestone(
                    MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-2"), REGISTRANT, NOW);

            TrackingRecord after = registration.trackingRecord();
            assertThat(registration.milestone().state()).isEqualTo(MilestoneState.UNDER_REVIEW);
            assertThat(after.milestones()).hasSize(2);
            assertThat(after.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(after.statusBasisMilestoneNo()).hasValue(2);
            assertThat(after.underReviewReason()).contains(UnderReviewReason.OUT_OF_SEQUENCE);
            assertThat(after.statusFromAdopted()).isEqualTo(TrackingStatus.ARRIVED_AT_DESTINATION);
            assertThat(registration.placedUnderReview())
                    .contains(new TrackingPlacedUnderReview(
                            TRACKING_NUMBER.value(),
                            BOOKING_ID,
                            "OUT_OF_SEQUENCE",
                            2,
                            NOW,
                            before.aggregateVersion() + 1));
        }

        @Test
        void 行4_確認中の後に時系列どおりの実績を登録しても確認中のままで根拠と理由は変わらずDE18は出ない() {
            TrackingRecord underReview = arrived()
                    .registerMilestone(
                            MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-2"), REGISTRANT, NOW)
                    .trackingRecord();

            MilestoneRegistration registration = underReview.registerMilestone(
                    MilestoneKind.DELIVERY, tokyo(), at("2026-11-02T01:00:00Z"), source("F-3"), REGISTRANT, LATER);

            assertThat(registration.milestone().state()).isEqualTo(MilestoneState.ADOPTED);
            assertThat(registration.trackingRecord().currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(registration.trackingRecord().statusBasisMilestoneNo()).hasValue(2);
            assertThat(registration.trackingRecord().underReviewReason()).contains(UnderReviewReason.OUT_OF_SEQUENCE);
            assertThat(registration.placedUnderReview()).isEmpty();
        }

        @Test
        void 行16_すでに確認中なら時系列に反する実績を足してもDE18は出ない() {
            TrackingRecord underReview = arrived()
                    .registerMilestone(
                            MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-2"), REGISTRANT, NOW)
                    .trackingRecord();

            MilestoneRegistration registration = underReview.registerMilestone(
                    MilestoneKind.RECEIPT_AT_ORIGIN,
                    tokyo(),
                    at("2026-11-01T02:40:00Z"),
                    source("F-3"),
                    REGISTRANT,
                    NOW);

            assertThat(registration.milestone().state()).isEqualTo(MilestoneState.UNDER_REVIEW);
            assertThat(registration.trackingRecord().statusBasisMilestoneNo()).hasValue(2);
            assertThat(registration.placedUnderReview()).isEmpty();
        }

        @Test
        void 行16_時系列どおりの登録と同じ出典の二重送信ではDE18は出ない() {
            TrackingRecord before = arrived();

            assertThat(before.registerMilestone(
                                    MilestoneKind.DELIVERY,
                                    tokyo(),
                                    at("2026-11-01T02:30:00Z"),
                                    source("F-2"),
                                    REGISTRANT,
                                    NOW)
                            .placedUnderReview())
                    .isEmpty();
            assertThat(before.registerMilestone(
                                    MilestoneKind.PICKUP,
                                    tokyo(),
                                    at("2026-11-01T01:00:00Z"),
                                    source("F-1"),
                                    REGISTRANT,
                                    NOW)
                            .placedUnderReview())
                    .isEmpty();
        }

        @Test
        void 行9_取消済みの予約への登録は拒否し実績を作らない() {
            TrackingRecord cancelled = withBookingStatus(arrived(), TrackedBookingStatus.CANCELLED);

            assertThatThrownBy(() -> cancelled.registerMilestone(
                            MilestoneKind.DEPARTURE,
                            tokyo(),
                            at("2026-11-01T02:30:00Z"),
                            source("F-2"),
                            REGISTRANT,
                            NOW))
                    .isInstanceOfSatisfying(
                            MilestoneRegistrationRejected.class,
                            rejected -> assertThat(rejected.reason())
                                    .isEqualTo(MilestoneRejectionReason.BOOKING_CANCELLED));
        }

        /** 行 10。完了後は予約状態が完了か、採用済みの実績から導いた状態が引渡し済み。 */
        @ParameterizedTest(name = "{0}")
        @CsvSource({"予約状態が完了, COMPLETED, ARRIVAL", "採用済みから導いた状態が引渡し済み, CONFIRMED, DELIVERY"})
        void 行10_完了後の登録は保持のみで現在状態と根拠は変わらず確認中にしない(
                String name, TrackedBookingStatus bookingStatus, MilestoneKind lastKind) {
            TrackingRecord completed = withBookingStatus(
                    started()
                            .registerMilestone(
                                    lastKind, tokyo(), at("2026-11-01T02:00:00Z"), source("F-1"), REGISTRANT, NOW)
                            .trackingRecord(),
                    bookingStatus);

            MilestoneRegistration registration = completed.registerMilestone(
                    MilestoneKind.PICKUP, tokyo(), at("2026-11-01T01:00:00Z"), source("F-2"), REGISTRANT, NOW);

            assertThat(registration.milestone().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(registration.trackingRecord().currentStatus()).isEqualTo(completed.currentStatus());
            assertThat(registration.trackingRecord().statusBasisMilestoneNo()).hasValue(1);
            assertThat(registration.placedUnderReview()).isEmpty();
        }

        /** 行 8。判定の順: 同じ出典 → 取消済み → 未来の発生時刻 → 完了後 → 時系列に反する → 採用。2 つの条件が重なる組。 */
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "同じ出典かつ取消済みは同じ出典, CANCELLED, ARRIVAL, F-1, 2026-11-01T01:00:00Z, ALREADY",
            "取消済みかつ未来は取消済み, CANCELLED, ARRIVAL, F-2, 2026-11-01T04:00:00Z, BOOKING_CANCELLED",
            "完了後かつ未来は未来の拒否, COMPLETED, ARRIVAL, F-2, 2026-11-01T04:00:00Z, OCCURRED_IN_FUTURE",
            "完了後かつ時系列に反するは保持のみ, COMPLETED, ARRIVAL, F-2, 2026-11-01T02:30:00Z, RETAINED_ONLY",
            "未来かつ時系列に反するは未来の拒否, CONFIRMED, ARRIVAL, F-2, 2026-11-01T04:00:00Z, OCCURRED_IN_FUTURE",
            "時系列に反するは確認中, CONFIRMED, ARRIVAL, F-2, 2026-11-01T02:30:00Z, UNDER_REVIEW",
            "時系列どおりは採用, CONFIRMED, PICKUP, F-2, 2026-11-01T02:30:00Z, ADOPTED"
        })
        void 行8_手入力の判定の順(
                String name,
                TrackedBookingStatus bookingStatus,
                MilestoneKind firstKind,
                String reference,
                String occurredAt,
                String expected) {
            TrackingRecord trackingRecord = withBookingStatus(
                    started()
                            .registerMilestone(
                                    firstKind, tokyo(), at("2026-11-01T02:00:00Z"), source("F-1"), REGISTRANT, NOW)
                            .trackingRecord(),
                    bookingStatus);

            String actual;
            try {
                MilestoneRegistration registration = trackingRecord.registerMilestone(
                        MilestoneKind.RECEIPT_AT_ORIGIN, tokyo(), at(occurredAt), source(reference), REGISTRANT, NOW);
                actual = registration.alreadyRegistered()
                        ? "ALREADY"
                        : registration.milestone().state().name();
            } catch (MilestoneRegistrationRejected rejected) {
                actual = rejected.reason().name();
            }

            assertThat(actual).isEqualTo(expected);
        }

        /** 行 11（【前の決定の変更】(14)）。確認中の間に引渡しを採用した追跡は、以後の手入力が保持のみ。 */
        @Test
        void 行11_確認中の間に引渡しを登録した追跡への手入力は保持のみ() {
            TrackingRecord delivered = arrived()
                    .registerMilestone(
                            MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:30:00Z"), source("F-2"), REGISTRANT, NOW)
                    .trackingRecord()
                    .registerMilestone(
                            MilestoneKind.DELIVERY,
                            tokyo(),
                            at("2026-11-02T01:00:00Z"),
                            source("F-3"),
                            REGISTRANT,
                            LATER)
                    .trackingRecord();
            assertThat(delivered.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(delivered.statusFromAdopted()).isEqualTo(TrackingStatus.DELIVERED);

            MilestoneRegistration registration = delivered.registerMilestone(
                    MilestoneKind.DEPARTURE, tokyo(), at("2026-11-02T02:00:00Z"), source("F-4"), REGISTRANT, LATER);

            assertThat(registration.milestone().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(registration.trackingRecord().currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
        }

        @Test
        void 現在状態が確認中なら理由を持ちそうでなければ持たない() {
            TrackingRecord trackingRecord = started();
            Optional<UnderReviewReason> none = Optional.empty();
            Optional<UnderReviewReason> reason = Optional.of(UnderReviewReason.OUT_OF_SEQUENCE);

            assertThatThrownBy(() -> reconstitute(trackingRecord, TrackingStatus.UNDER_REVIEW, none))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> reconstitute(trackingRecord, TrackingStatus.PICKUP_SCHEDULED, reason))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private static TrackingRecord reconstitute(
                TrackingRecord from, TrackingStatus status, Optional<UnderReviewReason> reason) {
            return TrackingRecord.reconstitute(
                    from.trackingNumber(),
                    from.bookingId(),
                    from.shipperCompanyId(),
                    from.consigneeCompanyId(),
                    from.bookingStatus(),
                    from.schedule(),
                    status,
                    from.statusBasisMilestoneNo(),
                    reason,
                    from.milestones(),
                    from.originalEta(),
                    from.latestEta(),
                    from.startedAt(),
                    from.aggregateVersion());
        }
    }

    /** 予約状態だけを変えた追跡記録（予約状態の書き手は W11・39g2 なので組み立てで作る）。 */
    static TrackingRecord withBookingStatus(TrackingRecord from, TrackedBookingStatus bookingStatus) {
        return TrackingRecord.reconstitute(
                from.trackingNumber(),
                from.bookingId(),
                from.shipperCompanyId(),
                from.consigneeCompanyId(),
                bookingStatus,
                from.schedule(),
                from.currentStatus(),
                from.statusBasisMilestoneNo(),
                from.underReviewReason(),
                from.milestones(),
                from.originalEta(),
                from.latestEta(),
                from.startedAt(),
                from.aggregateVersion());
    }

    private static TrackingRecord started() {
        return TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                .trackingRecord();
    }

    private static Location tokyo() {
        return new Location("JPTYO");
    }

    private static Source source(String reference) {
        return new Source(SourceKind.FIELD_RECORD, reference, NOW);
    }
}

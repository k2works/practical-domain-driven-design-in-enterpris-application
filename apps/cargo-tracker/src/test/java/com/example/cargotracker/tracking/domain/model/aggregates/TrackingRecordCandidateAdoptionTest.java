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

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.events.PickupMilestoneAdopted;
import com.example.cargotracker.tracking.domain.events.TrackingPlacedUnderReview;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneGroup;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.UnderReviewReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 組の候補と、追跡管理者の候補の採用（Bolt 39gb の行 5〜12・15・50〜56）。 */
class TrackingRecordCandidateAdoptionTest {

    private static final UUID SOURCE_A = UUID.fromString("00000000-0000-0000-0000-0000039b0a01");
    private static final UUID SOURCE_B = UUID.fromString("00000000-0000-0000-0000-0000039b0a02");
    private static final UserId STEWARD = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039b0a03"));
    private static final UserId MANAGER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039b0a04"));
    private static final Location TOKYO = new Location("JPTYO");
    private static final Location BUSAN = new Location("KRPUS");
    private static final UtcInstant OCCURRED = at("2026-11-19T00:00:00Z");
    private static final UtcInstant ADOPTED_AT = at("2026-11-20T00:00:00Z");

    /** 外部原本の値（DE-13 の受け取りで実績を足す。届いた順のまま実績番号を振る）。 */
    private record Arrival(
            UUID source, String externalId, Location location, String publishedAt, String acquiredAt, String ref) {}

    private static Arrival arrival(Location location, String publishedAt, String ref) {
        return new Arrival(SOURCE_A, "MS-0001", location, publishedAt, "2026-11-19T01:00:00Z", ref);
    }

    private static ExternalMilestone external(Arrival arrival) {
        return new ExternalMilestone(
                TRACKING_NUMBER,
                MilestoneKind.DEPARTURE,
                arrival.location(),
                OCCURRED,
                new Source(SourceKind.EXTERNAL_RECORD, arrival.ref(), at(arrival.acquiredAt())),
                new ExternalOrigin(arrival.source(), arrival.externalId(), at(arrival.publishedAt())),
                STEWARD);
    }

    private static TrackingRecord empty(TrackedBookingStatus bookingStatus, TrackingStatus status) {
        return TrackingRecord.reconstitute(
                TRACKING_NUMBER,
                BOOKING_ID,
                SHIPPER,
                CONSIGNEE,
                bookingStatus,
                schedule(),
                status,
                OptionalInt.empty(),
                List.of(),
                at("2026-11-15T00:00:00Z"),
                at("2026-11-15T00:00:00Z"),
                STARTED_AT,
                3);
    }

    private static TrackingRecord received(TrackingRecord start, Arrival... arrivals) {
        TrackingRecord current = start;
        for (Arrival arrival : arrivals) {
            current = ((ExternalMilestoneReceipt.Received)
                            current.receiveExternalMilestone(external(arrival), at(arrival.publishedAt())))
                    .trackingRecord();
        }
        return current;
    }

    private static TrackingRecord received(Arrival... arrivals) {
        return received(empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED), arrivals);
    }

    private static int candidateNo(TrackingRecord trackingRecord) {
        List<MilestoneGroup> groups = trackingRecord.groupsOf();
        assertThat(groups).hasSize(1);
        return groups.getFirst().candidate().orElseThrow().milestoneNo();
    }

    @Nested
    class 候補の判定 {

        @Test
        void 行6_古いDE13が後から届いても候補は公開した時刻の新しい方で届いた順によらない() {
            Arrival older = arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1");
            Arrival newer = arrival(BUSAN, "2026-11-19T11:00:00Z", "r-2");

            TrackingRecord newerFirst = received(newer, older);
            TrackingRecord olderFirst = received(older, newer);

            assertThat(newerFirst.milestones()).hasSize(2);
            assertThat(newerFirst.milestones().get(candidateNo(newerFirst) - 1).location())
                    .isEqualTo(BUSAN);
            assertThat(olderFirst.milestones().get(candidateNo(olderFirst) - 1).location())
                    .isEqualTo(BUSAN);
        }

        @Test
        void 行7_同じ情報版で値の違う組は公開した時刻の新しい方が候補() {
            TrackingRecord trackingRecord = received(
                    arrival(TOKYO, "2026-11-19T10:00:00Z", "r-a"), arrival(BUSAN, "2026-11-19T12:00:00Z", "r-b"));

            assertThat(candidateNo(trackingRecord)).isEqualTo(2);
        }

        @Test
        void 行8_同じ値の組も値を比べず公開した時刻の新しい方が候補() {
            TrackingRecord trackingRecord = received(
                    arrival(TOKYO, "2026-11-19T12:00:00Z", "r-a"), arrival(TOKYO, "2026-11-19T10:00:00Z", "r-b"));

            assertThat(candidateNo(trackingRecord)).isEqualTo(1);
        }

        @Test
        void 行9_外部版をまたいでV1の後にV2が届くとV2が候補() {
            TrackingRecord trackingRecord = received(
                    arrival(TOKYO, "2026-11-19T10:00:00Z", "r-v1"), arrival(BUSAN, "2026-11-20T10:00:00Z", "r-v2"));

            assertThat(candidateNo(trackingRecord)).isEqualTo(2);
        }

        /** 外部版の順は比べない（D-105）。V2 の取込の後に V1 を採用・取込すると、公開した時刻の新しい V1 が候補。 */
        @ParameterizedTest(name = "{0}")
        @CsvSource({"V1 の隔離を採用, r-v1-quarantine", "V1 を取り込む, r-v1-ingest"})
        void 行10_V2の後にV1を公開すると公開した時刻の新しいV1が候補(String name, String v1Ref) {
            TrackingRecord trackingRecord = received(
                    arrival(BUSAN, "2026-11-19T10:00:00Z", "r-v2"), arrival(TOKYO, "2026-11-20T10:00:00Z", v1Ref));

            assertThat(trackingRecord
                            .milestones()
                            .get(candidateNo(trackingRecord) - 1)
                            .location())
                    .isEqualTo(TOKYO);
        }

        @Test
        void 行11_誤った採用の後に元の原本を送り直すと最後に公開した元の値が候補() {
            TrackingRecord trackingRecord = received(
                    arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"),
                    arrival(BUSAN, "2026-11-19T11:00:00Z", "r-2"),
                    arrival(TOKYO, "2026-11-19T12:00:00Z", "r-3"));

            assertThat(candidateNo(trackingRecord)).isEqualTo(3);
            assertThat(trackingRecord.isSupersededDraft(
                            trackingRecord.milestones().get(0)))
                    .isTrue();
            assertThat(trackingRecord.isSupersededDraft(
                            trackingRecord.milestones().get(1)))
                    .isTrue();
            assertThat(trackingRecord.isSupersededDraft(
                            trackingRecord.milestones().get(2)))
                    .isFalse();
        }

        @Test
        void 行12_情報源が違えば同じ外部IDでも別の組でそれぞれが候補() {
            TrackingRecord trackingRecord = received(
                    new Arrival(SOURCE_A, "MS-0001", TOKYO, "2026-11-19T10:00:00Z", "2026-11-19T01:00:00Z", "r-a"),
                    new Arrival(SOURCE_B, "MS-0001", BUSAN, "2026-11-19T11:00:00Z", "2026-11-19T01:00:00Z", "r-b"));

            assertThat(trackingRecord.groupsOf()).hasSize(2);
            assertThat(trackingRecord.groupsOf())
                    .allSatisfy(group -> assertThat(group.candidate()).isPresent());
            assertThat(trackingRecord.isSupersededDraft(
                            trackingRecord.milestones().get(0)))
                    .isFalse();
        }

        @Test
        void 行12_外部IDが違えば別の組() {
            TrackingRecord trackingRecord = received(
                    new Arrival(SOURCE_A, "MS-0001", TOKYO, "2026-11-19T10:00:00Z", "2026-11-19T01:00:00Z", "r-a"),
                    new Arrival(SOURCE_A, "MS-0002", BUSAN, "2026-11-19T11:00:00Z", "2026-11-19T01:00:00Z", "r-b"));

            assertThat(trackingRecord.groupsOf()).hasSize(2);
        }

        @Test
        void 行15_手入力の実績と保持のみは組に入らない() {
            TrackingRecord withManual = empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                    .registerMilestone(
                            MilestoneKind.PICKUP,
                            TOKYO,
                            at("2026-11-18T00:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-18T00:00:00Z")),
                            MANAGER,
                            at("2026-11-18T01:00:00Z"))
                    .trackingRecord();
            TrackingRecord retained = received(
                    empty(TrackedBookingStatus.COMPLETED, TrackingStatus.PICKUP_SCHEDULED),
                    arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));

            assertThat(withManual.groupsOf()).isEmpty();
            assertThat(retained.milestones().getFirst().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(retained.groupsOf()).isEmpty();
            assertThat(retained.isSupersededDraft(retained.milestones().getFirst()))
                    .isFalse();
        }
    }

    @Nested
    class 候補の採用 {

        @Test
        void 行50_候補を採用すると採用になり現在状態と根拠が導出し直され採用した人と時刻が残る() {
            TrackingRecord trackingRecord = received(
                    arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"), arrival(BUSAN, "2026-11-19T11:00:00Z", "r-2"));

            MilestoneAdoption.Adopted adopted =
                    (MilestoneAdoption.Adopted) trackingRecord.adoptCandidate(2, MANAGER, ADOPTED_AT);

            TrackingRecord after = adopted.trackingRecord();
            assertThat(after.milestones()).hasSize(2);
            Milestone milestone = after.milestones().get(1);
            assertThat(milestone.state()).isEqualTo(MilestoneState.ADOPTED);
            assertThat(milestone.adoptedBy()).isEqualTo(MANAGER);
            assertThat(milestone.adoptedAt()).isEqualTo(ADOPTED_AT);
            assertThat(milestone.registeredBy()).isEqualTo(STEWARD);
            assertThat(adopted.milestone()).isEqualTo(milestone);
            assertThat(after.milestones().get(0).state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(after.currentStatus()).isEqualTo(TrackingStatus.IN_TRANSIT);
            assertThat(after.statusBasisMilestoneNo()).hasValue(2);
            assertThat(after.aggregateVersion()).isEqualTo(trackingRecord.aggregateVersion());
            // 採用の後は最も新しい実績が採用なので候補はなく、古い下書きは置き換えられたまま
            assertThat(after.groupsOf().getFirst().candidate()).isEmpty();
            assertThat(after.isSupersededDraft(after.milestones().get(0))).isTrue();
            // 採用の前の追跡記録は変わらない
            assertThat(trackingRecord.milestones().get(1).state()).isEqualTo(MilestoneState.DRAFT);
        }

        @Test
        void 行51_候補でない下書きは採用できない() {
            TrackingRecord trackingRecord = received(
                    arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"), arrival(BUSAN, "2026-11-19T11:00:00Z", "r-2"));

            assertThat(trackingRecord.adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.NOT_CANDIDATE));
        }

        @Test
        void 行51_採用済み_手入力_保持のみ_ない実績番号は候補でない() {
            TrackingRecord adopted = ((MilestoneAdoption.Adopted)
                            received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"))
                                    .adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .trackingRecord();
            TrackingRecord manual = empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                    .registerMilestone(
                            MilestoneKind.PICKUP,
                            TOKYO,
                            at("2026-11-18T00:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-18T00:00:00Z")),
                            MANAGER,
                            at("2026-11-18T01:00:00Z"))
                    .trackingRecord();

            assertThat(adopted.adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.NOT_CANDIDATE));
            assertThat(manual.adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.NOT_CANDIDATE));
            assertThat(adopted.adoptCandidate(9, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.NOT_CANDIDATE));
        }

        @Test
        void 行52_採用済みの実績と値が違う候補は採用できない() {
            TrackingRecord adopted = ((MilestoneAdoption.Adopted)
                            received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"))
                                    .adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .trackingRecord();
            TrackingRecord withNewer = received(adopted, arrival(BUSAN, "2026-11-19T11:00:00Z", "r-2"));

            assertThat(withNewer.groupsOf().getFirst().candidate().orElseThrow().milestoneNo())
                    .isEqualTo(2);
            // 拒否は判断済みの実績を持つ（画面が判断済みの実績の状態で文を分ける。Bolt 39gc）
            assertThat(withNewer.adoptCandidate(2, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(
                            AdoptionRejectionReason.CONFLICTS_WITH_ADOPTED,
                            Optional.of(withNewer.milestones().getFirst())));
        }

        @Test
        void 行53_候補と同じ値を採用済みなら採用は要らず既存を返す() {
            TrackingRecord adopted = ((MilestoneAdoption.Adopted)
                            received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"))
                                    .adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .trackingRecord();
            TrackingRecord withSame = received(adopted, arrival(TOKYO, "2026-11-19T11:00:00Z", "r-2"));

            assertThat(withSame.adoptCandidate(2, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.SameAsAdopted(
                            withSame.milestones().getFirst()));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "取消済みの予約, CANCELLED, PICKUP_SCHEDULED, BOOKING_CANCELLED",
            "完了の予約, COMPLETED, PICKUP_SCHEDULED, TRACKING_COMPLETED",
            "引渡し済み, CONFIRMED, DELIVERED, TRACKING_COMPLETED",
            "取消済みかつ引渡し済みは取消済みが先, CANCELLED, DELIVERED, BOOKING_CANCELLED"
        })
        void 行54_取消済み_完了後の追跡の下書きは採用できない(
                String name,
                TrackedBookingStatus bookingStatus,
                TrackingStatus status,
                AdoptionRejectionReason reason) {
            // 確定のときに受けた下書きの後に予約状態・現在状態が変わった追跡記録（変わる経路は 39g2・W11 なので組み立てで作る）。
            // 引渡し済みは採用済みの引渡しの実績から導く（Bolt 39gc の【前の決定の変更】(14)。現在状態だけでは完了後にしない）
            Milestone draft = received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"))
                    .milestones()
                    .getFirst();
            List<Milestone> milestones = status == TrackingStatus.DELIVERED
                    ? List.of(
                            draft,
                            new Milestone(
                                    2,
                                    MilestoneKind.DELIVERY,
                                    BUSAN,
                                    at("2026-11-19T12:00:00Z"),
                                    new Source(SourceKind.FIELD_RECORD, "F-2", at("2026-11-19T12:00:00Z")),
                                    MilestoneState.ADOPTED,
                                    MANAGER,
                                    at("2026-11-19T12:00:00Z")))
                    : List.of(draft);
            TrackingRecord trackingRecord = TrackingRecord.reconstitute(
                    TRACKING_NUMBER,
                    BOOKING_ID,
                    SHIPPER,
                    CONSIGNEE,
                    bookingStatus,
                    schedule(),
                    status,
                    status == TrackingStatus.DELIVERED ? OptionalInt.of(2) : OptionalInt.empty(),
                    milestones,
                    at("2026-11-15T00:00:00Z"),
                    at("2026-11-15T00:00:00Z"),
                    STARTED_AT,
                    3);

            assertThat(trackingRecord.adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(reason));
        }

        @Test
        void 行55_発生時刻が採用時刻より後の下書きは採用できず同時刻は採用できる() {
            ExternalMilestone future = new ExternalMilestone(
                    TRACKING_NUMBER,
                    MilestoneKind.DEPARTURE,
                    TOKYO,
                    ADOPTED_AT,
                    new Source(SourceKind.EXTERNAL_RECORD, "r-f", at("2026-11-19T01:00:00Z")),
                    new ExternalOrigin(SOURCE_A, "MS-0001", at("2026-11-19T10:00:00Z")),
                    STEWARD);
            TrackingRecord trackingRecord = ((ExternalMilestoneReceipt.Received)
                            empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                                    .receiveExternalMilestone(future, at("2026-11-19T10:00:00Z")))
                    .trackingRecord();

            assertThat(trackingRecord.adoptCandidate(1, MANAGER, at("2026-11-19T23:59:59.999Z")))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.OCCURRED_IN_FUTURE));
            assertThat(trackingRecord.adoptCandidate(1, MANAGER, ADOPTED_AT))
                    .isInstanceOf(MilestoneAdoption.Adopted.class);
        }

        @Test
        void 行56_元の追跡番号の下書きは元の追跡番号の組として採用できる() {
            TrackingNumber original = new TrackingNumber("CTPPPPPPPPP234");
            TrackingRecord trackingRecord = TrackingRecord.reconstitute(
                    original,
                    BOOKING_ID,
                    SHIPPER,
                    CONSIGNEE,
                    TrackedBookingStatus.CONFIRMED,
                    schedule(),
                    TrackingStatus.PICKUP_SCHEDULED,
                    OptionalInt.empty(),
                    List.of(),
                    at("2026-11-15T00:00:00Z"),
                    at("2026-11-15T00:00:00Z"),
                    STARTED_AT,
                    3);
            ExternalMilestone v1 = new ExternalMilestone(
                    original,
                    MilestoneKind.DEPARTURE,
                    TOKYO,
                    OCCURRED,
                    new Source(SourceKind.EXTERNAL_RECORD, "r-v1", at("2026-11-19T01:00:00Z")),
                    new ExternalOrigin(SOURCE_A, "MS-0001", at("2026-11-19T10:00:00Z")),
                    STEWARD);
            TrackingRecord withV1 = ((ExternalMilestoneReceipt.Received)
                            trackingRecord.receiveExternalMilestone(v1, at("2026-11-19T10:00:00Z")))
                    .trackingRecord();

            assertThat(withV1.adoptCandidate(1, MANAGER, ADOPTED_AT)).isInstanceOf(MilestoneAdoption.Adopted.class);
        }
    }

    /** Bolt 39gc の採用の分（行 6・7・11〜14・16。US-12 AC3、T-INV-03・05・06）。 */
    @Nested
    class Bolt39gc_採用の確認中 {

        /** 到着を手入力で採用した後（11-18）に、それより後の発生時刻（11-19）の出発の下書きが届いた追跡記録。出発は到着より前の段なので時系列に反する。 */
        private TrackingRecord arrivedThenDraft(Arrival... arrivals) {
            TrackingRecord arrived = empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                    .registerMilestone(
                            MilestoneKind.ARRIVAL,
                            BUSAN,
                            at("2026-11-18T00:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-18T00:00:00Z")),
                            MANAGER,
                            at("2026-11-18T01:00:00Z"))
                    .trackingRecord();
            return received(arrived, arrivals);
        }

        private TrackingRecord underReview() {
            return ((MilestoneAdoption.Adopted) arrivedThenDraft(
                                    arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"),
                                    arrival(BUSAN, "2026-11-19T11:00:00Z", "r-2"))
                            .adoptCandidate(3, MANAGER, ADOPTED_AT))
                    .trackingRecord();
        }

        @Test
        void 行6_時系列に反する候補を採用すると確認中に移り採用者と採用時刻を持ち現在状態は確認中でDE18が1件() {
            TrackingRecord before = arrivedThenDraft(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));

            MilestoneAdoption.Adopted adopted =
                    (MilestoneAdoption.Adopted) before.adoptCandidate(2, MANAGER, ADOPTED_AT);

            Milestone milestone = adopted.milestone();
            assertThat(milestone.state()).isEqualTo(MilestoneState.UNDER_REVIEW);
            assertThat(milestone.adoptedBy()).isEqualTo(MANAGER);
            assertThat(milestone.adoptedAt()).isEqualTo(ADOPTED_AT);
            TrackingRecord after = adopted.trackingRecord();
            assertThat(after.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(after.statusBasisMilestoneNo()).hasValue(2);
            assertThat(after.underReviewReason()).contains(UnderReviewReason.OUT_OF_SEQUENCE);
            assertThat(adopted.placedUnderReview())
                    .contains(new TrackingPlacedUnderReview(
                            TRACKING_NUMBER.value(),
                            BOOKING_ID,
                            "OUT_OF_SEQUENCE",
                            2,
                            ADOPTED_AT,
                            before.aggregateVersion() + 1));
        }

        @Test
        void 行16_時系列どおりの採用ではDE18は出ない() {
            TrackingRecord before = received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));

            MilestoneAdoption.Adopted adopted =
                    (MilestoneAdoption.Adopted) before.adoptCandidate(1, MANAGER, ADOPTED_AT);

            assertThat(adopted.milestone().state()).isEqualTo(MilestoneState.ADOPTED);
            assertThat(adopted.placedUnderReview()).isEmpty();
        }

        @Test
        void 行12_確認中の外部原本の実績が組で最も新しいとき候補はなく古い下書きは置き換えられたまま() {
            TrackingRecord trackingRecord = underReview();

            MilestoneGroup group = trackingRecord.groupsOf().getFirst();
            assertThat(group.members()).extracting(Milestone::milestoneNo).containsExactly(2, 3);
            assertThat(group.candidate()).isEmpty();
            assertThat(group.adopted()).map(Milestone::milestoneNo).hasValue(3);
            assertThat(trackingRecord.isSupersededDraft(
                            trackingRecord.milestones().get(1)))
                    .isTrue();
            assertThat(trackingRecord.adoptCandidate(2, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.NOT_CANDIDATE));
        }

        @Test
        void 行13_確認中の実績と値が違う新しい下書きは採用できない() {
            TrackingRecord trackingRecord = received(underReview(), arrival(TOKYO, "2026-11-19T12:00:00Z", "r-3"));

            assertThat(trackingRecord.adoptCandidate(4, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(
                            AdoptionRejectionReason.CONFLICTS_WITH_ADOPTED,
                            Optional.of(trackingRecord.milestones().get(2))));
        }

        @Test
        void 行14_確認中の実績と同じ値の新しい下書きは採用は要らない() {
            TrackingRecord trackingRecord = received(underReview(), arrival(BUSAN, "2026-11-19T12:00:00Z", "r-3"));

            assertThat(trackingRecord.adoptCandidate(4, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.SameAsAdopted(
                            trackingRecord.milestones().get(2)));
        }

        @Test
        void 行11_確認中の間に引渡しを採用した追跡では手入力は保持のみ_採用は完了後_DE13は保持のみ() {
            TrackingRecord delivered = underReview()
                    .registerMilestone(
                            MilestoneKind.DELIVERY,
                            BUSAN,
                            at("2026-11-19T20:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-9", at("2026-11-19T20:00:00Z")),
                            MANAGER,
                            ADOPTED_AT)
                    .trackingRecord();
            assertThat(delivered.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            TrackingRecord withDraft = received(
                    delivered,
                    new Arrival(SOURCE_B, "MS-0009", TOKYO, "2026-11-19T21:00:00Z", "2026-11-19T01:00:00Z", "r-9"));

            assertThat(withDraft.milestones().getLast().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(withDraft.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(delivered
                            .registerMilestone(
                                    MilestoneKind.DEPARTURE,
                                    TOKYO,
                                    at("2026-11-19T21:00:00Z"),
                                    new Source(SourceKind.FIELD_RECORD, "F-10", at("2026-11-19T21:00:00Z")),
                                    MANAGER,
                                    ADOPTED_AT)
                            .milestone()
                            .state())
                    .isEqualTo(MilestoneState.RETAINED_ONLY);
            // 完了の前に届いた下書き（組の候補）は採用できない
            TrackingRecord draftBeforeDelivery = received(
                            arrivedThenDraft(),
                            new Arrival(
                                    SOURCE_B, "MS-0008", TOKYO, "2026-11-19T09:00:00Z", "2026-11-19T01:00:00Z", "r-8"))
                    .registerMilestone(
                            MilestoneKind.PICKUP,
                            TOKYO,
                            at("2026-11-18T12:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-2", at("2026-11-18T12:00:00Z")),
                            MANAGER,
                            ADOPTED_AT)
                    .trackingRecord()
                    .registerMilestone(
                            MilestoneKind.DELIVERY,
                            BUSAN,
                            at("2026-11-19T20:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-3", at("2026-11-19T20:00:00Z")),
                            MANAGER,
                            ADOPTED_AT)
                    .trackingRecord();
            assertThat(draftBeforeDelivery.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(draftBeforeDelivery.adoptCandidate(2, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.TRACKING_COMPLETED));
        }

        @Test
        void 行26_確認中の追跡にもDE13は下書きで届き現在状態は確認中のまま() {
            TrackingRecord before = underReview();

            TrackingRecord after = received(
                    before,
                    new Arrival(SOURCE_B, "MS-0007", TOKYO, "2026-11-19T21:00:00Z", "2026-11-19T01:00:00Z", "r-7"));

            assertThat(after.milestones().getLast().state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(after.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(after.statusBasisMilestoneNo()).isEqualTo(before.statusBasisMilestoneNo());
            assertThat(after.underReviewReason()).contains(UnderReviewReason.OUT_OF_SEQUENCE);
        }

        @Test
        void 行25_荷主の照会は現在状態の確認中を示し確認中の実績は示さない() {
            CustomerTrackingView view = underReview().customerView();

            assertThat(view.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(view.milestones()).extracting(CustomerMilestone::kind).containsExactly(MilestoneKind.ARRIVAL);
        }

        /** 行 7。判定の順で、時系列に反する条件と別の条件が重なる組。 */
        @Nested
        class 行7_採用の判定の順 {

            @Test
            void 取消済みかつ時系列に反するは取消済み() {
                TrackingRecord cancelled = TrackingRecordMilestoneTest.withBookingStatus(
                        arrivedThenDraft(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1")),
                        TrackedBookingStatus.CANCELLED);

                assertThat(cancelled.adoptCandidate(2, MANAGER, ADOPTED_AT))
                        .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.BOOKING_CANCELLED));
            }

            @Test
            void 完了後かつ時系列に反するは完了後() {
                TrackingRecord completed = TrackingRecordMilestoneTest.withBookingStatus(
                        arrivedThenDraft(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1")),
                        TrackedBookingStatus.COMPLETED);

                assertThat(completed.adoptCandidate(2, MANAGER, ADOPTED_AT))
                        .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.TRACKING_COMPLETED));
            }

            @Test
            void 未来かつ時系列に反するは未来の発生時刻() {
                TrackingRecord trackingRecord = arrivedThenDraft(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));

                assertThat(trackingRecord.adoptCandidate(2, MANAGER, at("2026-11-18T23:59:59Z")))
                        .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.OCCURRED_IN_FUTURE));
            }

            @Test
            void 判断済みと値が違うかつ時系列に反するは値が違う() {
                TrackingRecord adoptedThenOther = received(
                        TrackingRecordMilestoneTest.withBookingStatus(
                                ((MilestoneAdoption.Adopted)
                                                arrivedThenDraft(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"))
                                                        .adoptCandidate(2, MANAGER, ADOPTED_AT))
                                        .trackingRecord(),
                                TrackedBookingStatus.CONFIRMED),
                        arrival(BUSAN, "2026-11-19T11:00:00Z", "r-2"));

                assertThat(adoptedThenOther.adoptCandidate(3, MANAGER, ADOPTED_AT))
                        .isInstanceOfSatisfying(
                                MilestoneAdoption.Rejected.class,
                                rejected -> assertThat(rejected.reason())
                                        .isEqualTo(AdoptionRejectionReason.CONFLICTS_WITH_ADOPTED));
            }
        }
    }

    /** Bolt 39g2 の行 11・14・15（採用の経路の DE-09・DE-10。【業務の判断】(al)）。 */
    @Nested
    class Bolt39g2_採用のDE09とDE10 {

        private TrackingRecord draftOf(TrackingRecord start, MilestoneKind kind, String occurredAt) {
            ExternalMilestone external = new ExternalMilestone(
                    TRACKING_NUMBER,
                    kind,
                    TOKYO,
                    at(occurredAt),
                    new Source(SourceKind.EXTERNAL_RECORD, "r-1", at("2026-11-19T01:00:00Z")),
                    new ExternalOrigin(SOURCE_A, "MS-0001", at("2026-11-19T10:00:00Z")),
                    STEWARD);
            return ((ExternalMilestoneReceipt.Received)
                            start.receiveExternalMilestone(external, at("2026-11-19T10:00:00Z")))
                    .trackingRecord();
        }

        /** 到着（11-18）を手入力で採用した追跡記録。 */
        private TrackingRecord arrived() {
            return empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                    .registerMilestone(
                            MilestoneKind.ARRIVAL,
                            BUSAN,
                            at("2026-11-18T00:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-18T00:00:00Z")),
                            MANAGER,
                            at("2026-11-18T01:00:00Z"))
                    .trackingRecord();
        }

        private static String eventOf(MilestoneAdoption.Adopted adopted) {
            List<String> events = new ArrayList<>();
            adopted.pickupAdopted().ifPresent(event -> events.add("DE09"));
            adopted.deliveryConfirmed().ifPresent(event -> events.add("DE10"));
            adopted.placedUnderReview().ifPresent(event -> events.add("DE18"));
            // 行 14。採用と確認中は同じ実績で重ならない
            assertThat(events).hasSizeLessThanOrEqualTo(1);
            return events.isEmpty() ? "NONE" : events.getFirst();
        }

        /** 行 11。採用に移った集荷・引渡しは出し、確認中に移った（DE-18 だけ）・搬入・出発は出さない。 */
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "採用に移った集荷はDE09, false, PICKUP, 2026-11-19T00:00:00Z, ADOPTED, DE09",
            "採用に移った引渡しはDE10, false, DELIVERY, 2026-11-19T00:00:00Z, ADOPTED, DE10",
            "採用に移った出発は出さない, false, DEPARTURE, 2026-11-19T00:00:00Z, ADOPTED, NONE",
            "確認中に移った集荷はDE18だけ, true, PICKUP, 2026-11-19T00:00:00Z, UNDER_REVIEW, DE18",
            "確認中に移った引渡しはDE18だけ, true, DELIVERY, 2026-11-17T00:00:00Z, UNDER_REVIEW, DE18",
            "時系列どおりの引渡しは到着の後でもDE10, true, DELIVERY, 2026-11-19T00:00:00Z, ADOPTED, DE10"
        })
        void 行11_採用の経路でDE09とDE10を出す(
                String name,
                boolean afterArrival,
                MilestoneKind kind,
                String occurredAt,
                MilestoneState state,
                String expected) {
            TrackingRecord start =
                    afterArrival ? arrived() : empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);
            TrackingRecord draft = draftOf(start, kind, occurredAt);
            int milestoneNo = draft.milestones().size();

            MilestoneAdoption.Adopted adopted =
                    (MilestoneAdoption.Adopted) draft.adoptCandidate(milestoneNo, MANAGER, ADOPTED_AT);

            assertThat(adopted.milestone().state()).isEqualTo(state);
            assertThat(eventOf(adopted)).isEqualTo(expected);
        }

        @Test
        void 行15_採用のDE09は採用者と保存した後の版を持つ() {
            TrackingRecord draft = draftOf(
                    empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED),
                    MilestoneKind.PICKUP,
                    "2026-11-19T00:00:00Z");

            MilestoneAdoption.Adopted adopted =
                    (MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT);

            assertThat(adopted.pickupAdopted())
                    .contains(new PickupMilestoneAdopted(
                            TRACKING_NUMBER.value(),
                            BOOKING_ID,
                            1,
                            at("2026-11-19T00:00:00Z"),
                            MANAGER.value(),
                            draft.aggregateVersion() + 1));
        }

        @Test
        void 行11_拒否と採用済みの再採用はイベントを持たない結果になる() {
            TrackingRecord draft = draftOf(
                    empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED),
                    MilestoneKind.PICKUP,
                    "2026-11-19T00:00:00Z");
            TrackingRecord adopted =
                    ((MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT)).trackingRecord();

            // 未来の発生時刻の拒否と、採用済みの実績をもう一度採用する（候補でない）拒否。Rejected はイベントを持たない
            assertThat(draft.adoptCandidate(1, MANAGER, at("2026-11-18T00:00:00Z")))
                    .isInstanceOf(MilestoneAdoption.Rejected.class);
            assertThat(adopted.adoptCandidate(1, MANAGER, ADOPTED_AT)).isInstanceOf(MilestoneAdoption.Rejected.class);
        }
    }

    @Nested
    class 組の不変条件 {

        @Test
        void 組の実績は同じ情報源と外部IDの下書きか採用だけ() {
            List<Milestone> drafts = new ArrayList<>(received(
                            arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"),
                            new Arrival(
                                    SOURCE_B, "MS-0001", BUSAN, "2026-11-19T11:00:00Z", "2026-11-19T01:00:00Z", "r-2"))
                    .milestones());

            assertThatThrownBy(() -> new MilestoneGroup(SOURCE_A, "MS-0001", drafts))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Bolt 39g3 の行 10（下書きの採用で最新の到着見込みと最終取得時刻が動くのは採用に移ったときだけ。T-INV-10）。外部原本の出発は JPTYO の 2026-11-19 00:00
     * （区間 1 の出発予定 2026-11-01 00:00 の 18 日後）。拒否・判断済みと同じ値は追跡記録を返さないので変わらない。
     */
    @Nested
    class Bolt39g3_採用の最新の到着見込み {

        @Test
        void 行10_採用に移ると見込みは遅れの分だけ動き最終取得時刻は採用した実績の取得時刻() {
            TrackingRecord draft = received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));
            assertThat(draft.latestEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
            assertThat(draft.lastAcquiredAt()).isEmpty();

            TrackingRecord adopted =
                    ((MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT)).trackingRecord();

            assertThat(adopted.latestEta()).isEqualTo(at("2026-12-03T00:00:00Z"));
            assertThat(adopted.lastAcquiredAt()).contains(at("2026-11-19T01:00:00Z"));
            assertThat(adopted.originalEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
        }

        @Test
        void 行10_確認中に移ると見込みと最終取得時刻は変わらない() {
            TrackingRecord arrived = empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                    .registerMilestone(
                            MilestoneKind.ARRIVAL,
                            new Location("USLAX"),
                            at("2026-11-18T00:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-18T01:00:00Z")),
                            MANAGER,
                            ADOPTED_AT)
                    .trackingRecord();
            TrackingRecord draft = received(arrived, arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));

            MilestoneAdoption.Adopted adoption =
                    (MilestoneAdoption.Adopted) draft.adoptCandidate(2, MANAGER, ADOPTED_AT);

            assertThat(adoption.milestone().state()).isEqualTo(MilestoneState.UNDER_REVIEW);
            assertThat(adoption.trackingRecord().latestEta()).isEqualTo(at("2026-11-18T00:00:00Z"));
            assertThat(adoption.trackingRecord().lastAcquiredAt()).contains(at("2026-11-18T01:00:00Z"));
        }

        @Test
        void 行10_拒否と判断済みと同じ値は追跡記録を返さない() {
            TrackingRecord draft = received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));
            TrackingRecord adopted =
                    ((MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT)).trackingRecord();
            TrackingRecord sameValue = received(adopted, arrival(TOKYO, "2026-11-19T11:00:00Z", "r-2"));

            assertThat(draft.adoptCandidate(1, MANAGER, at("2026-11-18T00:00:00Z")))
                    .isInstanceOf(MilestoneAdoption.Rejected.class);
            assertThat(sameValue.adoptCandidate(2, MANAGER, ADOPTED_AT))
                    .isInstanceOf(MilestoneAdoption.SameAsAdopted.class);
            assertThat(sameValue.latestEta()).isEqualTo(adopted.latestEta());
            assertThat(sameValue.lastAcquiredAt()).isEqualTo(adopted.lastAcquiredAt());
        }
    }

    /** Bolt 39g4 の行 11（下書きの採用で次の予定の実績が進むのは採用に移ったときだけ。【業務の判断】(aq)）。外部原本の下書きは区間 1 の出発・JPTYO。 */
    @Nested
    class Bolt39g4_採用の次の予定の実績 {

        @Test
        void 行11_採用に移ると次の予定の実績は区間1の積替() {
            // 組み立ての追跡記録は次の予定の実績を持たない（39g4 の前の行と同じ形）。下書きの受け取りでは導出し直さない
            TrackingRecord draft = received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));
            assertThat(draft.nextExpectedAt()).isEmpty();

            TrackingRecord adopted =
                    ((MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT)).trackingRecord();

            assertThat(adopted.nextExpectedAt()).contains(at("2026-11-03T00:00:00Z"));
        }

        @Test
        void 行11_確認中に移ると次の予定の実績は進まない() {
            TrackingRecord pickedUp = empty(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                    .registerMilestone(
                            MilestoneKind.PICKUP,
                            new Location("JPTYO"),
                            at("2026-11-19T12:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-19T12:30:00Z")),
                            MANAGER,
                            ADOPTED_AT)
                    .trackingRecord();
            TrackingRecord draft = received(pickedUp, arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));

            MilestoneAdoption.Adopted adoption =
                    (MilestoneAdoption.Adopted) draft.adoptCandidate(2, MANAGER, ADOPTED_AT);

            assertThat(adoption.milestone().state()).isEqualTo(MilestoneState.UNDER_REVIEW);
            assertThat(adoption.trackingRecord().nextExpectedAt()).isEqualTo(draft.nextExpectedAt());
            assertThat(draft.nextExpectedAt()).contains(at("2026-11-01T00:00:00Z"));
        }

        @Test
        void 行11_拒否と判断済みと同じ値は追跡記録を返さない() {
            TrackingRecord draft = received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"));
            TrackingRecord adopted =
                    ((MilestoneAdoption.Adopted) draft.adoptCandidate(1, MANAGER, ADOPTED_AT)).trackingRecord();
            TrackingRecord sameValue = received(adopted, arrival(TOKYO, "2026-11-19T11:00:00Z", "r-2"));

            assertThat(draft.adoptCandidate(1, MANAGER, at("2026-11-18T00:00:00Z")))
                    .isInstanceOf(MilestoneAdoption.Rejected.class);
            assertThat(sameValue.adoptCandidate(2, MANAGER, ADOPTED_AT))
                    .isInstanceOf(MilestoneAdoption.SameAsAdopted.class);
            assertThat(sameValue.nextExpectedAt()).isEqualTo(adopted.nextExpectedAt());
        }
    }
}

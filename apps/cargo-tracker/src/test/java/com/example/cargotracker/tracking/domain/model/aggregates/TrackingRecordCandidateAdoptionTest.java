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
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneGroup;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.ArrayList;
import java.util.List;
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
            assertThat(withNewer.adoptCandidate(2, MANAGER, ADOPTED_AT))
                    .isEqualTo(new MilestoneAdoption.Rejected(AdoptionRejectionReason.CONFLICTS_WITH_ADOPTED));
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
            // 確定のときに受けた下書きの後に予約状態・現在状態が変わった追跡記録（変わる経路は 39g2・W11 なので組み立てで作る）
            Milestone draft = received(arrival(TOKYO, "2026-11-19T10:00:00Z", "r-1"))
                    .milestones()
                    .getFirst();
            TrackingRecord trackingRecord = TrackingRecord.reconstitute(
                    TRACKING_NUMBER,
                    BOOKING_ID,
                    SHIPPER,
                    CONSIGNEE,
                    bookingStatus,
                    schedule(),
                    status,
                    OptionalInt.empty(),
                    List.of(draft),
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
}

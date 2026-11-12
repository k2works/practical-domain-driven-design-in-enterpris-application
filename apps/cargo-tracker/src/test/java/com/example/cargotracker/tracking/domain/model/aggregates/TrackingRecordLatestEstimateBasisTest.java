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
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import com.example.cargotracker.tracking.domain.model.valueobjects.SchedulePoint;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyageArrival;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 追跡記録が見込みと根拠を同じ結果から持つ（Bolt 39g4c の行 4・5。計画 決定 (4)）。予定は 2 区間（区間 1: V100 で JPTYO から KRPUS、出発予定
 * D1 = 2026-11-01 00:00。区間 2: V200 で KRPUS から USLAX、到着予定 A2 = 2026-11-15 00:00）。当初の到着予定は A2。
 */
class TrackingRecordLatestEstimateBasisTest {

    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039c4c11"));
    private static final UtcInstant A2 = at("2026-11-15T00:00:00Z");
    private static final UtcInstant A2_PLUS_6 = at("2026-11-15T06:00:00Z");
    private static final UtcInstant A2_PLUS_8 = at("2026-11-15T08:00:00Z");
    private static final UtcInstant G1 = at("2026-11-06T01:00:00Z");
    private static final UtcInstant REGISTERED_AT = at("2026-11-20T00:00:00Z");
    private static final PublicationOrder FIRST = new PublicationOrder(at("2026-11-06T00:00:00Z"), G1, "rr-1");
    private static final PublicationOrder NEWER = new PublicationOrder(at("2026-11-06T01:00:00Z"), G1, "rr-2");

    private static TrackingRecord started() {
        return TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                .trackingRecord();
    }

    private static TrackingRecord register(
            TrackingRecord from, MilestoneKind kind, String location, String occurredAt) {
        return from.registerMilestone(
                        kind,
                        new Location(location),
                        at(occurredAt),
                        new Source(SourceKind.FIELD_RECORD, "F-" + kind + occurredAt, at("2026-11-19T00:00:00Z")),
                        REGISTRANT,
                        REGISTERED_AT)
                .trackingRecord();
    }

    private static List<VoyagePortCall> arrivingAt(UtcInstant arrival) {
        return List.of(
                VoyagePortCall.of(new Location("KRPUS"), null, at("2026-11-04T00:00:00Z")),
                VoyagePortCall.of(new Location("USLAX"), arrival, null));
    }

    private static List<VoyagePortCall> notCallingAtDischarge() {
        return List.of(
                VoyagePortCall.of(new Location("KRPUS"), null, at("2026-11-04T00:00:00Z")),
                VoyagePortCall.of(new Location("CNSHA"), at("2026-11-06T00:00:00Z"), null));
    }

    private static ExternalMilestone draft(MilestoneKind kind, String location, String occurredAt, String reference) {
        return new ExternalMilestone(
                TRACKING_NUMBER,
                kind,
                new Location(location),
                at(occurredAt),
                new Source(SourceKind.EXTERNAL_RECORD, reference, G1),
                new ExternalOrigin(UUID.fromString("00000000-0000-0000-0000-0000039c4c12"), "MS-" + reference, G1),
                REGISTRANT);
    }

    private static Milestone milestone(
            int no, MilestoneKind kind, String location, String occurredAt, MilestoneState state) {
        return new Milestone(
                no,
                kind,
                new Location(location),
                at(occurredAt),
                new Source(SourceKind.FIELD_RECORD, "F-" + no, at("2026-11-19T00:00:00Z")),
                state,
                REGISTRANT,
                REGISTERED_AT);
    }

    /** Bolt 39g4c の前の形（根拠なし）で組み立てる。 */
    private static TrackingRecord beforeBasis(
            TrackedBookingStatus bookingStatus, List<Milestone> milestones, UtcInstant latestEta) {
        return TrackingRecord.reconstitute(
                TRACKING_NUMBER,
                BOOKING_ID,
                SHIPPER,
                CONSIGNEE,
                bookingStatus,
                schedule(),
                TrackingStatus.IN_TRANSIT,
                OptionalInt.of(milestones.size()),
                Optional.empty(),
                milestones,
                A2,
                latestEta,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                STARTED_AT,
                3);
    }

    /** 根拠を受ける最新の形で組み立てる（行 5）。 */
    private static TrackingRecord withBasis(
            List<Milestone> milestones,
            UtcInstant latestEta,
            Optional<LatestEstimateBasis> basis,
            Optional<VoyageArrival> voyageArrival) {
        return TrackingRecord.reconstitute(
                TRACKING_NUMBER,
                BOOKING_ID,
                SHIPPER,
                CONSIGNEE,
                TrackedBookingStatus.CONFIRMED,
                schedule(),
                TrackingStatus.PICKUP_SCHEDULED,
                OptionalInt.empty(),
                Optional.empty(),
                milestones,
                A2,
                latestEta,
                basis,
                Optional.empty(),
                Optional.empty(),
                voyageArrival,
                STARTED_AT,
                3);
    }

    @Nested
    class Bolt39g4c_行4_見込みと根拠を同じ結果から持つ {

        @Test
        void a_追跡を始めると根拠は当初の到着予定で見込みの遅れは0() {
            TrackingRecord started = started();

            assertThat(started.latestEstimateBasis()).contains(LatestEstimateBasis.originalEta());
            assertThat(started.latestEstimateBasisPoint()).isEmpty();
            assertThat(started.latestEstimateDelay()).isEqualTo(Duration.ZERO);
        }

        @Test
        void b_遅れた出発を登録すると根拠はその実績で予定の時点と見込みの遅れを出す() {
            TrackingRecord after = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z");

            assertThat(after.latestEstimateBasis()).contains(LatestEstimateBasis.milestone(1));
            assertThat(after.latestEstimateBasisPoint())
                    .map(SchedulePoint::kind)
                    .contains(MilestoneKind.DEPARTURE);
            assertThat(after.latestEstimateBasisPoint())
                    .map(SchedulePoint::legIndex)
                    .contains(0);
            assertThat(after.latestEstimateDelay()).isEqualTo(Duration.ofHours(6));
            assertThat(after.latestEta()).isEqualTo(A2_PLUS_6);
        }

        @Test
        void c_下書きを採用すると根拠は採用した実績() {
            TrackingRecord withDraft = ((ExternalMilestoneReceipt.Received) started()
                            .receiveExternalMilestone(
                                    draft(MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", "rr-d1"),
                                    REGISTERED_AT))
                    .trackingRecord();
            assertThat(withDraft.latestEstimateBasis()).contains(LatestEstimateBasis.originalEta());

            TrackingRecord adopted = ((MilestoneAdoption.Adopted)
                            withDraft.adoptCandidate(1, REGISTRANT, REGISTERED_AT))
                    .trackingRecord();

            assertThat(adopted.latestEstimateBasis()).contains(LatestEstimateBasis.milestone(1));
            assertThat(adopted.latestEta()).isEqualTo(A2_PLUS_6);
        }

        @Test
        void d_遅い航海の到着予定を受けると根拠は航海の到着予定() {
            TrackingRecord delayed = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z");

            TrackingRecord after = delayed.receiveVoyageArrival("V200", arrivingAt(A2_PLUS_8), FIRST)
                    .orElseThrow();

            assertThat(after.latestEstimateBasis()).contains(LatestEstimateBasis.voyageArrival());
            assertThat(after.latestEstimateBasisPoint()).isEmpty();
            assertThat(after.latestEstimateDelay()).isEqualTo(Duration.ofHours(8));
        }

        @Test
        void e_結べない新しい知らせを受けると実績に戻る() {
            TrackingRecord withVoyage = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z")
                    .receiveVoyageArrival("V200", arrivingAt(A2_PLUS_8), FIRST)
                    .orElseThrow();

            TrackingRecord after = withVoyage
                    .receiveVoyageArrival("V200", notCallingAtDischarge(), NEWER)
                    .orElseThrow();

            assertThat(after.latestEstimateBasis()).contains(LatestEstimateBasis.milestone(1));
            assertThat(after.latestEta()).isEqualTo(A2_PLUS_6);
        }

        @Test
        void f_根拠のない追跡がDE13の下書きを受けても根拠は空のまま() {
            TrackingRecord before = beforeBasis(
                    TrackedBookingStatus.CONFIRMED,
                    List.of(milestone(
                            1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", MilestoneState.ADOPTED)),
                    A2_PLUS_6);

            TrackingRecord after = ((ExternalMilestoneReceipt.Received) before.receiveExternalMilestone(
                            draft(MilestoneKind.TRANSSHIPMENT, "KRPUS", "2026-11-03T02:00:00Z", "rr-d2"),
                            REGISTERED_AT))
                    .trackingRecord();

            assertThat(after.latestEstimateBasis()).isEmpty();
            assertThat(after.latestEta()).isEqualTo(A2_PLUS_6);
        }

        @Test
        void g_根拠のない完了後の追跡に保持のみの実績を登録しても根拠は空のまま() {
            TrackingRecord before = beforeBasis(
                    TrackedBookingStatus.COMPLETED,
                    List.of(milestone(
                            1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", MilestoneState.ADOPTED)),
                    A2_PLUS_6);

            TrackingRecord after = register(before, MilestoneKind.TRANSSHIPMENT, "KRPUS", "2026-11-03T02:00:00Z");

            assertThat(after.milestones().getLast().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(after.latestEstimateBasis()).isEmpty();
        }

        @Test
        void h_時系列に反する実績を登録しても根拠は採用の実績のまま() {
            TrackingRecord delayed = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z");

            TrackingRecord after = register(delayed, MilestoneKind.PICKUP, "JPTYO", "2026-11-01T10:00:00Z");

            assertThat(after.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(after.latestEstimateBasis()).contains(LatestEstimateBasis.milestone(1));
        }

        @Test
        void i_根拠のない形で組み立てると根拠は空で見込みは保存した値() {
            TrackingRecord before = beforeBasis(TrackedBookingStatus.CONFIRMED, List.of(), at("2026-11-16T03:00:00Z"));

            assertThat(before.latestEstimateBasis()).isEmpty();
            assertThat(before.latestEta()).isEqualTo(at("2026-11-16T03:00:00Z"));
            assertThat(before.latestEstimateBasisPoint()).isEmpty();
        }
    }

    @Nested
    class Bolt39g4c_行5_見込みと根拠の一致の不変条件 {

        private static final Milestone DEPARTED =
                milestone(1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", MilestoneState.ADOPTED);
        private static final Milestone ARRIVED =
                milestone(1, MilestoneKind.ARRIVAL, "USLAX", "2026-11-15T01:00:00Z", MilestoneState.ADOPTED);
        private static final Optional<VoyageArrival> VOYAGE_A2_PLUS_8 =
                Optional.of(new VoyageArrival(Optional.of(A2_PLUS_8), FIRST));

        static Stream<Arguments> 拒む組() {
            return Stream.of(
                    Arguments.of(
                            "(a) 根拠の実績番号の実績がない",
                            List.of(DEPARTED),
                            A2_PLUS_6,
                            LatestEstimateBasis.milestone(2),
                            Optional.empty()),
                    Arguments.of(
                            "(b) 根拠の実績が下書き",
                            List.of(milestone(
                                    1, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", MilestoneState.DRAFT)),
                            A2_PLUS_6,
                            LatestEstimateBasis.milestone(1),
                            Optional.empty()),
                    Arguments.of(
                            "(c) 根拠の実績が予定の時点に結ばない",
                            List.of(milestone(
                                    1, MilestoneKind.PICKUP, "JPTYO", "2026-10-31T00:00:00Z", MilestoneState.ADOPTED)),
                            A2,
                            LatestEstimateBasis.milestone(1),
                            Optional.empty()),
                    Arguments.of(
                            "(d) 航海の到着予定を受けていない",
                            List.of(),
                            A2_PLUS_8,
                            LatestEstimateBasis.voyageArrival(),
                            Optional.empty()),
                    Arguments.of(
                            "(d) 航海の到着予定が結べない",
                            List.of(),
                            A2_PLUS_8,
                            LatestEstimateBasis.voyageArrival(),
                            Optional.of(new VoyageArrival(Optional.empty(), FIRST))),
                    Arguments.of(
                            "(e) 見込みが航海の到着予定と違う",
                            List.of(DEPARTED),
                            A2_PLUS_6,
                            LatestEstimateBasis.voyageArrival(),
                            VOYAGE_A2_PLUS_8),
                    Arguments.of(
                            "(f) 見込みが当初の到着予定と違う",
                            List.of(),
                            A2_PLUS_6,
                            LatestEstimateBasis.originalEta(),
                            Optional.empty()),
                    Arguments.of(
                            "(g) 到着の実績で見込みが発生時刻と違う",
                            List.of(ARRIVED),
                            at("2026-11-15T02:00:00Z"),
                            LatestEstimateBasis.milestone(1),
                            Optional.empty()),
                    Arguments.of(
                            "(h) 出発の実績で見込みが当初の到着予定より前",
                            List.of(DEPARTED),
                            at("2026-11-14T23:00:00Z"),
                            LatestEstimateBasis.milestone(1),
                            Optional.empty()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("拒む組")
        void 拒む(
                String row,
                List<Milestone> milestones,
                UtcInstant latestEta,
                LatestEstimateBasis basis,
                Optional<VoyageArrival> voyageArrival) {
            Optional<LatestEstimateBasis> stored = Optional.of(basis);
            assertThatThrownBy(() -> withBasis(milestones, latestEta, stored, voyageArrival))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void i_根拠が空なら組み立てられる() {
            assertThat(withBasis(List.of(DEPARTED), at("2026-11-16T03:00:00Z"), Optional.empty(), Optional.empty())
                            .latestEstimateBasis())
                    .isEmpty();
        }

        @Test
        void 一致していれば組み立てられる() {
            assertThat(withBasis(
                                    List.of(DEPARTED),
                                    A2_PLUS_6,
                                    Optional.of(LatestEstimateBasis.milestone(1)),
                                    Optional.empty())
                            .latestEstimateDelay())
                    .isEqualTo(Duration.ofHours(6));
            assertThat(withBasis(
                                    List.of(ARRIVED),
                                    at("2026-11-15T01:00:00Z"),
                                    Optional.of(LatestEstimateBasis.milestone(1)),
                                    Optional.empty())
                            .latestEstimateBasisPoint())
                    .map(SchedulePoint::kind)
                    .contains(MilestoneKind.ARRIVAL);
            assertThat(withBasis(
                                    List.of(),
                                    A2_PLUS_8,
                                    Optional.of(LatestEstimateBasis.voyageArrival()),
                                    VOYAGE_A2_PLUS_8)
                            .latestEta())
                    .isEqualTo(A2_PLUS_8);
        }
    }
}

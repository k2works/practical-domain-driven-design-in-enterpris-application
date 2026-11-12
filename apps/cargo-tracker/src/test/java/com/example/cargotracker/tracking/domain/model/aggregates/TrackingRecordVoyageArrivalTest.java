package com.example.cargotracker.tracking.domain.model.aggregates;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.BOOKING_ID;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.CONSIGNEE;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.SHIPPER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.STARTED_AT;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static com.example.cargotracker.tracking.domain.model.TrackingFixture.schedule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyageArrival;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 航海の到着予定の受け取り（Bolt 39g4b の行 5・7・8・9。【業務の判断】(at)・(au)・(av)、T-INV-10）。予定は 2 区間（区間 2 は V200 で KRPUS から USLAX、
 * 到着予定 A2 = 2026-11-15 00:00）。集約が自分の予定で航海の到着予定を結び、見込みと最終取得時刻だけを導き直す。
 */
class TrackingRecordVoyageArrivalTest {

    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-0000039b4b01"));
    private static final UtcInstant A2 = at("2026-11-15T00:00:00Z");
    private static final UtcInstant A2_PLUS_10 = at("2026-11-15T10:00:00Z");
    private static final UtcInstant G1 = at("2026-11-06T01:00:00Z");
    private static final UtcInstant G2 = at("2026-11-06T02:00:00Z");
    private static final UtcInstant G3 = at("2026-11-06T03:00:00Z");
    private static final UtcInstant REGISTERED_AT = at("2026-11-07T00:00:00Z");

    /** V200 の寄港の並び（KRPUS を出発し、USLAX に到着）。 */
    private static List<VoyagePortCall> arrivingAt(UtcInstant arrival) {
        return List.of(
                VoyagePortCall.of(new Location("KRPUS"), null, at("2026-11-04T00:00:00Z")),
                VoyagePortCall.of(new Location("USLAX"), arrival, null));
    }

    /** USLAX に寄らない並び（結べない。行 2 (c)）。 */
    private static List<VoyagePortCall> notCallingAtDischarge() {
        return List.of(
                VoyagePortCall.of(new Location("KRPUS"), null, at("2026-11-04T00:00:00Z")),
                VoyagePortCall.of(new Location("CNSHA"), at("2026-11-06T00:00:00Z"), null));
    }

    private static PublicationOrder order(String publishedAt, UtcInstant acquiredAt, String reference) {
        return new PublicationOrder(at(publishedAt), acquiredAt, reference);
    }

    private static final PublicationOrder FIRST = order("2026-11-06T00:00:00Z", G1, "rr-5");

    private static TrackingRecord started() {
        return TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                .trackingRecord();
    }

    private static TrackingRecord received(
            TrackingRecord trackingRecord, List<VoyagePortCall> calls, PublicationOrder order) {
        return trackingRecord.receiveVoyageArrival("V200", calls, order).orElseThrow();
    }

    private static TrackingRecord register(
            TrackingRecord trackingRecord,
            MilestoneKind kind,
            String location,
            String occurredAt,
            UtcInstant acquiredAt) {
        return trackingRecord
                .registerMilestone(
                        kind,
                        new Location(location),
                        at(occurredAt),
                        new Source(SourceKind.FIELD_RECORD, "F-" + occurredAt, acquiredAt),
                        REGISTRANT,
                        REGISTERED_AT)
                .trackingRecord();
    }

    private static TrackingRecord withBookingStatus(TrackingRecord from, TrackedBookingStatus bookingStatus) {
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
                from.lastAcquiredAt(),
                from.nextExpectedAt(),
                from.voyageArrival(),
                from.startedAt(),
                from.aggregateVersion());
    }

    @Nested
    class Bolt39g4b_行5_受け取り {

        @Test
        void a_初めての知らせは結んで航海の到着予定と順の鍵を書き見込みだけを導き直す() {
            TrackingRecord before = started();

            TrackingRecord after = received(before, arrivingAt(A2_PLUS_10), FIRST);

            assertThat(after.voyageArrival()).contains(new VoyageArrival(Optional.of(A2_PLUS_10), FIRST));
            assertThat(after.latestEta()).isEqualTo(A2_PLUS_10);
            assertThat(after.originalEta()).isEqualTo(A2);
            assertThat(after.currentStatus()).isEqualTo(before.currentStatus());
            assertThat(after.statusBasisMilestoneNo()).isEqualTo(before.statusBasisMilestoneNo());
            assertThat(after.milestones()).isEqualTo(before.milestones());
            assertThat(after.aggregateVersion()).isEqualTo(before.aggregateVersion());
        }

        @Test
        void b_保存した順より厳密に新しい知らせは受ける() {
            TrackingRecord first = received(started(), arrivingAt(A2_PLUS_10), FIRST);
            PublicationOrder newer = order("2026-11-06T00:00:00Z", G1, "rr-6");

            TrackingRecord after = received(first, arrivingAt(at("2026-11-15T12:00:00Z")), newer);

            assertThat(after.voyageArrival())
                    .contains(new VoyageArrival(Optional.of(at("2026-11-15T12:00:00Z")), newer));
            assertThat(after.latestEta()).isEqualTo(at("2026-11-15T12:00:00Z"));
        }

        @Test
        void c_保存した順と同じ知らせは受けない() {
            TrackingRecord first = received(started(), arrivingAt(A2_PLUS_10), FIRST);

            assertThat(first.receiveVoyageArrival("V200", arrivingAt(at("2026-11-15T12:00:00Z")), FIRST))
                    .isEmpty();
        }

        @Test
        void d_保存した順より古い知らせは受けない() {
            TrackingRecord first = received(started(), arrivingAt(A2_PLUS_10), FIRST);
            PublicationOrder older = order("2026-11-05T23:59:59Z", G3, "rr-9");

            assertThat(first.receiveVoyageArrival("V200", arrivingAt(at("2026-11-15T12:00:00Z")), older))
                    .isEmpty();
        }

        @Test
        void e_新しいが結べない知らせは航海の到着予定を空にし順の鍵は書く() {
            TrackingRecord first = received(started(), arrivingAt(A2_PLUS_10), FIRST);
            PublicationOrder newer = order("2026-11-06T01:00:00Z", G3, "rr-7");

            TrackingRecord after = received(first, notCallingAtDischarge(), newer);

            assertThat(after.voyageArrival()).contains(new VoyageArrival(Optional.empty(), newer));
            assertThat(after.latestEta()).isEqualTo(A2);
        }

        @Test
        void f1_引渡しを採用した追跡は受けない() {
            TrackingRecord delivered = register(started(), MilestoneKind.DELIVERY, "USLAX", "2026-11-06T00:00:00Z", G2);

            assertThat(delivered.receiveVoyageArrival("V200", arrivingAt(A2_PLUS_10), FIRST))
                    .isEmpty();
        }

        @Test
        void f2_予約状態が完了の追跡は受けない() {
            TrackingRecord completed = withBookingStatus(started(), TrackedBookingStatus.COMPLETED);

            assertThat(completed.receiveVoyageArrival("V200", arrivingAt(A2_PLUS_10), FIRST))
                    .isEmpty();
        }

        @Test
        void g_取消済みの追跡は受けない() {
            TrackingRecord cancelled = withBookingStatus(started(), TrackedBookingStatus.CANCELLED);

            assertThat(cancelled.receiveVoyageArrival("V200", arrivingAt(A2_PLUS_10), FIRST))
                    .isEmpty();
        }

        @Test
        void h_確認中の追跡は受けて現在状態は確認中のまま() {
            TrackingRecord pickedUp = register(started(), MilestoneKind.PICKUP, "JPTYO", "2026-11-01T10:00:00Z", G2);
            TrackingRecord underReview =
                    register(pickedUp, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G2);
            assertThat(underReview.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);

            TrackingRecord after = received(underReview, arrivingAt(A2_PLUS_10), FIRST);

            assertThat(after.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(after.underReviewReason()).isEqualTo(underReview.underReviewReason());
            assertThat(after.latestEta()).isEqualTo(A2_PLUS_10);
        }

        @Test
        void i_最後の区間でない航海の知らせは受けない() {
            List<VoyagePortCall> v100 = List.of(
                    VoyagePortCall.of(new Location("JPTYO"), null, at("2026-11-01T00:00:00Z")),
                    VoyagePortCall.of(new Location("KRPUS"), at("2026-11-03T10:00:00Z"), null));

            assertThat(started().receiveVoyageArrival("V100", v100, FIRST)).isEmpty();
        }
    }

    @Nested
    class Bolt39g4b_行7_最終取得時刻 {

        @Test
        void a_実績なしで結べた知らせの取得時刻() {
            assertThat(received(started(), arrivingAt(A2_PLUS_10), FIRST).lastAcquiredAt())
                    .contains(G1);
        }

        @Test
        void b_採用の実績の取得時刻が新しければその時刻() {
            TrackingRecord adopted = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G2);

            assertThat(received(adopted, arrivingAt(A2_PLUS_10), FIRST).lastAcquiredAt())
                    .contains(G2);
        }

        @Test
        void c_知らせの取得時刻が新しければその時刻() {
            TrackingRecord adopted = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G1);
            PublicationOrder later = order("2026-11-06T00:00:00Z", G2, "rr-5");

            TrackingRecord after = received(adopted, arrivingAt(A2_PLUS_10), later);

            assertThat(after.lastAcquiredAt()).contains(G2);
        }

        @Test
        void d_採用の実績の後に結べない知らせを受けたらその取得時刻() {
            TrackingRecord adopted = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G2);

            TrackingRecord after =
                    received(adopted, notCallingAtDischarge(), order("2026-11-06T00:00:00Z", G3, "rr-5"));

            assertThat(after.lastAcquiredAt()).contains(G3);
        }

        @Test
        void e_結べた後に結べない新しい知らせを受けても戻らない() {
            TrackingRecord first = received(started(), arrivingAt(A2_PLUS_10), FIRST);

            TrackingRecord after = received(first, notCallingAtDischarge(), order("2026-11-06T01:00:00Z", G3, "rr-6"));

            assertThat(after.lastAcquiredAt()).contains(G3);
        }

        @Test
        void f_下書きの実績の取得時刻だけなら空() {
            assertThat(started().lastAcquiredAt()).isEmpty();
        }

        @Test
        void g_受けなかった古い知らせの取得時刻は使わない() {
            TrackingRecord first = received(started(), arrivingAt(A2_PLUS_10), FIRST);
            UtcInstant g4 = at("2026-11-06T09:00:00Z");

            Optional<TrackingRecord> after = first.receiveVoyageArrival(
                    "V200", arrivingAt(A2_PLUS_10), order("2026-11-05T00:00:00Z", g4, "rr-9"));

            assertThat(after).isEmpty();
            assertThat(first.lastAcquiredAt()).contains(G1);
        }

        @Test
        void 実績が変わって導出し直しても受けた知らせの取得時刻を使う() {
            TrackingRecord first =
                    received(started(), arrivingAt(A2_PLUS_10), order("2026-11-06T00:00:00Z", G3, "rr-5"));

            TrackingRecord after = register(first, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G2);

            assertThat(after.lastAcquiredAt()).contains(G3);
        }
    }

    /** 行 6。航海の到着予定 A2 + 10 時間を受けた後に実績が変わる。 */
    @Nested
    class Bolt39g4b_行6_保存した航海の到着予定を使う {

        private final TrackingRecord withVoyage = received(started(), arrivingAt(A2_PLUS_10), FIRST);

        @Test
        void a_区間1の出発の遅れを登録しても遅い方の航海の到着予定のまま() {
            TrackingRecord after = register(withVoyage, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G2);

            assertThat(after.latestEta()).isEqualTo(A2_PLUS_10);
            assertThat(after.voyageArrival()).isEqualTo(withVoyage.voyageArrival());
        }

        @Test
        void b_到着の実績を採用すると実績() {
            TrackingRecord after = register(withVoyage, MilestoneKind.ARRIVAL, "USLAX", "2026-11-06T01:00:00Z", G2);

            assertThat(after.latestEta()).isEqualTo(at("2026-11-06T01:00:00Z"));
        }

        @Test
        void c_DE13の下書きを受けても保存した見込みのまま() {
            com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone draft =
                    new com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone(
                            TRACKING_NUMBER,
                            MilestoneKind.ARRIVAL,
                            new Location("USLAX"),
                            at("2026-11-06T01:00:00Z"),
                            new Source(SourceKind.EXTERNAL_RECORD, "rr-draft", G3),
                            new com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin(
                                    UUID.fromString("00000000-0000-0000-0000-0000039b4b02"), "MS-1", G3),
                            REGISTRANT);

            ExternalMilestoneReceipt receipt = withVoyage.receiveExternalMilestone(draft, REGISTERED_AT);

            TrackingRecord after = ((ExternalMilestoneReceipt.Received) receipt).trackingRecord();
            assertThat(after.latestEta()).isEqualTo(A2_PLUS_10);
            assertThat(after.lastAcquiredAt()).isEqualTo(withVoyage.lastAcquiredAt());
            assertThat(after.voyageArrival()).isEqualTo(withVoyage.voyageArrival());
        }

        @Test
        void d_確認中になる登録でも保存した見込みのまま() {
            TrackingRecord pickedUp = register(withVoyage, MilestoneKind.PICKUP, "JPTYO", "2026-11-01T10:00:00Z", G1);

            TrackingRecord after = register(pickedUp, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G2);

            assertThat(after.currentStatus()).isEqualTo(TrackingStatus.UNDER_REVIEW);
            assertThat(after.latestEta()).isEqualTo(A2_PLUS_10);
        }
    }

    @Test
    void Bolt39g4b_行8_航海の到着予定を受けても次の予定の実績は変わらない() {
        TrackingRecord before = register(started(), MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", G2);

        TrackingRecord after = received(before, arrivingAt(A2_PLUS_10), FIRST);

        assertThat(after.nextExpectedAt()).isEqualTo(before.nextExpectedAt()).isPresent();
    }

    @Test
    void Bolt39g4b_行9_追跡を始めると航海の到着予定は空で見込みは当初の到着予定() {
        TrackingRecord started = started();

        assertThat(started.voyageArrival()).isEmpty();
        assertThat(started.latestEta()).isEqualTo(A2);
    }
}

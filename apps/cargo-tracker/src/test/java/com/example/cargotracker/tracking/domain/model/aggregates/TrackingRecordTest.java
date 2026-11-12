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
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 追跡記録の開始（ADR-015、T-INV-08・T-INV-10。Bolt 25）。 */
class TrackingRecordTest {

    @Test
    void 予定を採用して追跡を始めると現在状態は集荷予定で予約は確定() {
        TrackingRecord trackingRecord = start().trackingRecord();

        assertThat(trackingRecord.trackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(trackingRecord.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(trackingRecord.shipperCompanyId()).isEqualTo(SHIPPER);
        assertThat(trackingRecord.consigneeCompanyId()).isEqualTo(CONSIGNEE);
        assertThat(trackingRecord.bookingStatus()).isEqualTo(TrackedBookingStatus.CONFIRMED);
        assertThat(trackingRecord.currentStatus()).isEqualTo(TrackingStatus.PICKUP_SCHEDULED);
        assertThat(trackingRecord.schedule()).isEqualTo(schedule());
        assertThat(trackingRecord.startedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void 当初の到着予定と最新の見込みは最後の区間の到着予定で始まる() {
        TrackingRecord trackingRecord = start().trackingRecord();

        assertThat(trackingRecord.originalEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
        assertThat(trackingRecord.latestEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
    }

    @Test
    void 追跡を始めるとDE22を発行する() {
        TrackingStart start = start();

        assertThat(start.event())
                .isEqualTo(new TrackingStarted(
                        TRACKING_NUMBER.value(),
                        BOOKING_ID,
                        STARTED_AT,
                        start.trackingRecord().aggregateVersion()));
    }

    private static TrackingStart start() {
        return TrackingRecord.start(TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT);
    }

    /** Bolt 39g3 の行 7・8（最終取得時刻。【業務の判断】(ap)）。最終取得時刻は採用の実績の出典の取得時刻のうち最も新しいもの。 */
    @Nested
    class Bolt39g3_最終取得時刻 {

        private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000039302"));
        private static final UtcInstant NOW = at("2026-11-02T00:00:00Z");

        @Test
        void 行8_追跡を始めると見込みは当初の到着予定で最終取得時刻は空() {
            TrackingRecord trackingRecord = start().trackingRecord();

            assertThat(trackingRecord.latestEta()).isEqualTo(trackingRecord.originalEta());
            assertThat(trackingRecord.lastAcquiredAt()).isEmpty();
        }

        @Test
        void 行7a_実績がなければ空() {
            assertThat(start().trackingRecord().lastAcquiredAt()).isEmpty();
        }

        @Test
        void 行7b_採用1件ならその取得時刻() {
            TrackingRecord registered = register(
                    start().trackingRecord(),
                    MilestoneKind.PICKUP,
                    "JPTYO",
                    "2026-11-01T01:00:00Z",
                    "F-1",
                    "2026-11-01T07:00:00Z");

            assertThat(registered.lastAcquiredAt()).contains(at("2026-11-01T07:00:00Z"));
        }

        @Test
        void 行7c_採用2件なら発生時刻の順と独立に取得時刻の新しい方() {
            TrackingRecord first = register(
                    start().trackingRecord(),
                    MilestoneKind.DEPARTURE,
                    "JPTYO",
                    "2026-11-01T06:00:00Z",
                    "F-1",
                    "2026-11-01T07:00:00Z");
            TrackingRecord second = register(
                    first, MilestoneKind.PICKUP, "JPTYO", "2026-11-01T01:00:00Z", "F-2", "2026-11-01T08:00:00Z");

            assertThat(second.milestones()).extracting(Milestone::state).containsOnly(MilestoneState.ADOPTED);
            assertThat(second.lastAcquiredAt()).contains(at("2026-11-01T08:00:00Z"));
        }

        @Test
        void 行7d_取得時刻の新しい下書きは使わない() {
            TrackingRecord adopted = register(
                    start().trackingRecord(),
                    MilestoneKind.DEPARTURE,
                    "JPTYO",
                    "2026-11-01T06:00:00Z",
                    "F-1",
                    "2026-11-01T07:00:00Z");

            TrackingRecord withDraft = ((ExternalMilestoneReceipt.Received) adopted.receiveExternalMilestone(
                            new ExternalMilestone(
                                    TRACKING_NUMBER,
                                    MilestoneKind.DEPARTURE,
                                    new Location("KRPUS"),
                                    at("2026-11-01T09:00:00Z"),
                                    new Source(SourceKind.EXTERNAL_RECORD, "R-1", at("2026-11-01T10:00:00Z")),
                                    new ExternalOrigin(
                                            UUID.fromString("00000000-0000-0000-0000-000000039303"),
                                            "MS-0001",
                                            at("2026-11-01T10:00:00Z")),
                                    REGISTRANT),
                            NOW))
                    .trackingRecord();

            assertThat(withDraft.milestones().get(1).state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(withDraft.lastAcquiredAt()).contains(at("2026-11-01T07:00:00Z"));

            // 下書きの後に取得時刻の古い採用の実績を登録して導出し直しても、下書きの取得時刻は使わない
            TrackingRecord registered = register(
                    withDraft, MilestoneKind.PICKUP, "JPTYO", "2026-11-01T01:00:00Z", "F-2", "2026-11-01T05:00:00Z");

            assertThat(registered.milestones().get(2).state()).isEqualTo(MilestoneState.ADOPTED);
            assertThat(registered.lastAcquiredAt()).contains(at("2026-11-01T07:00:00Z"));
        }

        @Test
        void 行7d_取得時刻の新しい確認中は使わない() {
            TrackingRecord adopted = register(
                    start().trackingRecord(),
                    MilestoneKind.PICKUP,
                    "JPTYO",
                    "2026-11-01T10:00:00Z",
                    "F-1",
                    "2026-11-01T07:00:00Z");

            TrackingRecord underReview = register(
                    adopted, MilestoneKind.DEPARTURE, "JPTYO", "2026-11-01T06:00:00Z", "F-2", "2026-11-01T11:00:00Z");

            assertThat(underReview.milestones().get(1).state()).isEqualTo(MilestoneState.UNDER_REVIEW);
            assertThat(underReview.lastAcquiredAt()).contains(at("2026-11-01T07:00:00Z"));
        }

        @Test
        void 行7d_取得時刻の新しい保持のみは使わない() {
            TrackingRecord delivered = register(
                    start().trackingRecord(),
                    MilestoneKind.DELIVERY,
                    "USLAX",
                    "2026-11-01T06:00:00Z",
                    "F-1",
                    "2026-11-01T07:00:00Z");

            TrackingRecord retained = register(
                    delivered, MilestoneKind.ARRIVAL, "USLAX", "2026-11-01T09:00:00Z", "F-2", "2026-11-01T11:00:00Z");

            assertThat(retained.milestones().get(1).state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(retained.lastAcquiredAt()).contains(at("2026-11-01T07:00:00Z"));
        }

        private static TrackingRecord register(
                TrackingRecord trackingRecord,
                MilestoneKind kind,
                String location,
                String occurredAt,
                String reference,
                String acquiredAt) {
            return trackingRecord
                    .registerMilestone(
                            kind,
                            new Location(location),
                            at(occurredAt),
                            new Source(SourceKind.FIELD_RECORD, reference, at(acquiredAt)),
                            REGISTRANT,
                            NOW)
                    .trackingRecord();
        }
    }
}

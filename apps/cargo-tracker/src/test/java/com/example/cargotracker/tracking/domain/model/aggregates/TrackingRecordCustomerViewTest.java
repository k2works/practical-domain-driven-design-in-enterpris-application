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
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 荷主向けに表示する（顧客向けに表示する(開示範囲) の荷主の分。BR-07、T-INV-08・09。Bolt 27）。 */
class TrackingRecordCustomerViewTest {

    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000002799"));

    @Test
    void 荷主向けの照会結果は現在状態と到着予定と予定区間を持つ() {
        TrackingRecord trackingRecord = withMilestones(TrackingStatus.PICKUP_SCHEDULED, List.of());

        CustomerTrackingView view = trackingRecord.customerView();

        assertThat(view.trackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(view.currentStatus()).isEqualTo(TrackingStatus.PICKUP_SCHEDULED);
        assertThat(view.originalEta()).isEqualTo(at("2026-11-15T00:00:00Z"));
        assertThat(view.latestEta()).isEqualTo(at("2026-11-16T03:00:00Z"));
        assertThat(view.legs()).isEqualTo(schedule().legs());
        assertThat(view.milestones()).isEmpty();
    }

    @Test
    void 荷主には採用済みの主要実績だけを発生時刻の順に見せ実績番号や状態や登録者は持たせない() {
        TrackingRecord trackingRecord = withMilestones(
                TrackingStatus.RECEIVED_AT_ORIGIN,
                List.of(
                        milestone(
                                1,
                                MilestoneKind.RECEIPT_AT_ORIGIN,
                                "2026-11-01T05:00:00Z",
                                "F-2",
                                MilestoneState.ADOPTED),
                        milestone(2, MilestoneKind.PICKUP, "2026-11-01T02:00:00Z", "F-1", MilestoneState.ADOPTED),
                        milestone(3, MilestoneKind.DEPARTURE, "2026-11-01T06:00:00Z", "F-3", MilestoneState.DRAFT),
                        milestone(4, MilestoneKind.ARRIVAL, "2026-11-01T07:00:00Z", "F-4", MilestoneState.UNDER_REVIEW),
                        milestone(
                                5,
                                MilestoneKind.DELIVERY,
                                "2026-11-01T08:00:00Z",
                                "F-5",
                                MilestoneState.RETAINED_ONLY)));

        List<CustomerMilestone> milestones = trackingRecord.customerView().milestones();

        assertThat(milestones)
                .containsExactly(
                        new CustomerMilestone(MilestoneKind.PICKUP, tokyo(), at("2026-11-01T02:00:00Z"), source("F-1")),
                        new CustomerMilestone(
                                MilestoneKind.RECEIPT_AT_ORIGIN, tokyo(), at("2026-11-01T05:00:00Z"), source("F-2")));
    }

    /** Bolt 39g の行 44（T-INV-09）。DE-13 の下書きと保持のみは荷主に出ない。 */
    @Test
    void Bolt39g_行44_外部原本の下書きと保持のみは荷主に見せない() {
        Source external = new Source(SourceKind.EXTERNAL_RECORD, "R-1", at("2026-11-01T09:00:00Z"));
        Source externalRetained = new Source(SourceKind.EXTERNAL_RECORD, "R-2", at("2026-11-01T09:00:00Z"));
        ExternalOrigin origin = new ExternalOrigin(UUID.randomUUID(), "MS-1", at("2026-11-01T09:00:00Z"));
        TrackingRecord trackingRecord = withMilestones(
                TrackingStatus.PICKUP_SCHEDULED,
                List.of(
                        new Milestone(
                                1,
                                MilestoneKind.DEPARTURE,
                                tokyo(),
                                at("2026-11-01T06:00:00Z"),
                                external,
                                MilestoneState.DRAFT,
                                REGISTRANT,
                                at("2026-11-01T09:00:00Z"),
                                origin),
                        new Milestone(
                                2,
                                MilestoneKind.ARRIVAL,
                                tokyo(),
                                at("2026-11-01T07:00:00Z"),
                                externalRetained,
                                MilestoneState.RETAINED_ONLY,
                                REGISTRANT,
                                at("2026-11-01T09:00:00Z"),
                                origin)));

        assertThat(trackingRecord.customerView().milestones()).isEmpty();
    }

    @Test
    void 発生時刻が同じ主要実績は登録の順に見せる() {
        TrackingRecord trackingRecord = withMilestones(
                TrackingStatus.RECEIVED_AT_ORIGIN,
                List.of(
                        milestone(1, MilestoneKind.PICKUP, "2026-11-01T02:00:00Z", "F-1", MilestoneState.ADOPTED),
                        milestone(
                                2,
                                MilestoneKind.RECEIPT_AT_ORIGIN,
                                "2026-11-01T02:00:00Z",
                                "F-2",
                                MilestoneState.ADOPTED)));

        assertThat(trackingRecord.customerView().milestones())
                .extracting(CustomerMilestone::kind)
                .containsExactly(MilestoneKind.PICKUP, MilestoneKind.RECEIPT_AT_ORIGIN);
    }

    @Test
    void 荷主向けの照会結果の項目は開示してよいものだけ() {
        // 見張りのテスト。経路版・実績番号・実績の状態・登録者・企業 ID を足すと、画面に出せてしまうので落ちる（BR-07、T-INV-09。
        // Bolt 27 の開発レビュー）。項目を足すときは開示してよいかを決めてから、ここも直す
        assertThat(Arrays.stream(CustomerTrackingView.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .containsExactly(
                        "trackingNumber",
                        "currentStatus",
                        "originalEta",
                        "latestEta",
                        "lastAcquiredAt",
                        "legs",
                        "milestones");
        assertThat(Arrays.stream(CustomerMilestone.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("kind", "location", "occurredAt", "source");
    }

    /**
     * Bolt 39g3 の行 19・20（T-INV-09）。荷主の照会の結果の最終取得時刻は採用の実績から決まり、取得時刻の新しい下書き・確認中の取得時刻を漏らさない
     * （本計画 CP-4、【業務の判断】(ap)）。最新の到着見込みは採用の実績で動いた値を示す。
     */
    @Test
    void Bolt39g3_行19と行20_最終取得時刻は採用の実績から決まり下書きと確認中の取得時刻を見せない() {
        UtcInstant now = at("2026-11-02T00:00:00Z");
        TrackingRecord pickedUp = TrackingRecord.start(
                        TRACKING_NUMBER, BOOKING_ID, SHIPPER, CONSIGNEE, schedule(), STARTED_AT)
                .trackingRecord()
                .registerMilestone(
                        MilestoneKind.PICKUP,
                        tokyo(),
                        at("2026-11-01T10:00:00Z"),
                        new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-01T11:00:00Z")),
                        REGISTRANT,
                        now)
                .trackingRecord();
        TrackingRecord underReview = pickedUp.registerMilestone(
                        MilestoneKind.DEPARTURE,
                        tokyo(),
                        at("2026-11-01T06:00:00Z"),
                        new Source(SourceKind.FIELD_RECORD, "F-2", at("2026-11-01T12:00:00Z")),
                        REGISTRANT,
                        now)
                .trackingRecord();
        TrackingRecord withDraft = ((ExternalMilestoneReceipt.Received) underReview.receiveExternalMilestone(
                        new ExternalMilestone(
                                TRACKING_NUMBER,
                                MilestoneKind.DEPARTURE,
                                new Location("KRPUS"),
                                at("2026-11-01T09:00:00Z"),
                                new Source(SourceKind.EXTERNAL_RECORD, "R-1", at("2026-11-01T13:00:00Z")),
                                new ExternalOrigin(
                                        UUID.fromString("00000000-0000-0000-0000-000000039304"),
                                        "MS-0001",
                                        at("2026-11-01T13:00:00Z")),
                                REGISTRANT),
                        now))
                .trackingRecord();

        CustomerTrackingView view = withDraft.customerView();

        assertThat(view.lastAcquiredAt()).contains(at("2026-11-01T11:00:00Z"));
        assertThat(view.latestEta()).isEqualTo(withDraft.latestEta());
        assertThat(view.milestones()).hasSize(1);
    }

    private static TrackingRecord withMilestones(TrackingStatus status, List<Milestone> milestones) {
        return TrackingRecord.reconstitute(
                TRACKING_NUMBER,
                BOOKING_ID,
                SHIPPER,
                CONSIGNEE,
                TrackedBookingStatus.CONFIRMED,
                schedule(),
                status,
                milestones.isEmpty() ? OptionalInt.empty() : OptionalInt.of(1),
                milestones,
                at("2026-11-15T00:00:00Z"),
                at("2026-11-16T03:00:00Z"),
                STARTED_AT,
                0);
    }

    private static Milestone milestone(
            int no, MilestoneKind kind, String occurredAt, String reference, MilestoneState state) {
        return new Milestone(
                no, kind, tokyo(), at(occurredAt), source(reference), state, REGISTRANT, at("2026-11-01T09:00:00Z"));
    }

    private static Location tokyo() {
        return new Location("JPTYO");
    }

    private static Source source(String reference) {
        return new Source(SourceKind.FIELD_RECORD, reference, at("2026-11-01T09:00:00Z"));
    }
}

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
import com.example.cargotracker.tracking.domain.model.valueobjects.LatestEstimateBasis;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 追跡記録の組み立てが、当初の到着予定と予定の最後の到着の一致、現在状態の根拠の実績番号が実績にあることを確かめる（Bolt 40-1 の M1。W7 週全体レビュー）。
 * 予定の最後の到着 A2 = 2026-11-15 00:00。
 */
class TrackingRecordOriginInvariantTest {

    private static final UtcInstant A2 = at("2026-11-15T00:00:00Z");
    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040c1001"));

    private static Milestone departure(int no) {
        return new Milestone(
                no,
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                at("2026-11-01T00:00:00Z"),
                new Source(SourceKind.FIELD_RECORD, "F-" + no, at("2026-11-19T00:00:00Z")),
                MilestoneState.ADOPTED,
                REGISTRANT,
                at("2026-11-20T00:00:00Z"));
    }

    private static TrackingRecord reconstitute(
            UtcInstant originalEta, UtcInstant latestEta, OptionalInt statusBasis, List<Milestone> milestones) {
        return TrackingRecord.reconstitute(
                TRACKING_NUMBER,
                BOOKING_ID,
                SHIPPER,
                CONSIGNEE,
                TrackedBookingStatus.CONFIRMED,
                schedule(),
                statusBasis.isPresent() ? TrackingStatus.IN_TRANSIT : TrackingStatus.PICKUP_SCHEDULED,
                statusBasis,
                Optional.empty(),
                milestones,
                originalEta,
                latestEta,
                Optional.of(LatestEstimateBasis.originalEta()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                STARTED_AT,
                1);
    }

    @Test
    void 当初の到着予定が予定の最後の到着と同じなら組み立てられる() {
        assertThat(reconstitute(A2, A2, OptionalInt.empty(), List.of())).isNotNull();
    }

    @Test
    void 当初の到着予定が予定の最後の到着と違うと拒む() {
        UtcInstant other = at("2026-11-16T00:00:00Z");

        OptionalInt noBasis = OptionalInt.empty();
        List<Milestone> none = List.of();

        assertThatThrownBy(() -> reconstitute(other, other, noBasis, none))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当初の到着予定");
    }

    @Test
    void 現在状態の根拠の実績番号が実績にあれば組み立てられる() {
        assertThat(reconstitute(A2, A2, OptionalInt.of(1), List.of(departure(1))))
                .isNotNull();
    }

    @Test
    void 現在状態の根拠の実績番号が実績にないと拒む() {
        OptionalInt missingBasis = OptionalInt.of(2);
        List<Milestone> milestones = List.of(departure(1));

        assertThatThrownBy(() -> reconstitute(A2, A2, missingBasis, milestones))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("根拠の実績番号");
    }
}

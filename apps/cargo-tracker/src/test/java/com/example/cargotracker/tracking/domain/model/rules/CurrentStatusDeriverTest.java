package com.example.cargotracker.tracking.domain.model.rules;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.DerivedStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
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

/** 現在状態の導出（T-INV-08。採用済みの実績のうち発生時刻が最も新しいものの種類で決める。Bolt 26b）。 */
class CurrentStatusDeriverTest {

    private static final UserId REGISTRANT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000026b01"));

    private final CurrentStatusDeriver deriver = new CurrentStatusDeriver();

    @Test
    void 実績がなければ予定を採用した集荷予定のままで根拠の実績はない() {
        assertThat(deriver.derive(List.of()))
                .isEqualTo(new DerivedStatus(TrackingStatus.PICKUP_SCHEDULED, OptionalInt.empty()));
    }

    @ParameterizedTest
    @CsvSource({
        "PICKUP, PICKED_UP",
        "RECEIPT_AT_ORIGIN, RECEIVED_AT_ORIGIN",
        "DEPARTURE, IN_TRANSIT",
        "TRANSSHIPMENT, TRANSSHIPPING",
        "ARRIVAL, ARRIVED_AT_DESTINATION",
        "DELIVERY, DELIVERED"
    })
    void 実績の種類ごとに対応する追跡状態にする(MilestoneKind kind, TrackingStatus expected) {
        assertThat(deriver.derive(List.of(milestone(1, kind, "2026-11-01T02:30:00Z", MilestoneState.ADOPTED))))
                .isEqualTo(new DerivedStatus(expected, OptionalInt.of(1)));
    }

    @Test
    void すべての実績の種類に対応する追跡状態がある() {
        for (MilestoneKind kind : MilestoneKind.values()) {
            assertThat(deriver.derive(List.of(milestone(1, kind, "2026-11-01T02:30:00Z", MilestoneState.ADOPTED)))
                            .status())
                    .as(kind.name())
                    .isNotEqualTo(TrackingStatus.PICKUP_SCHEDULED);
        }
    }

    @Test
    void 登録の順と発生時刻の順が違うときは発生時刻が最も新しい実績で導出する() {
        List<Milestone> milestones = List.of(
                milestone(1, MilestoneKind.DEPARTURE, "2026-11-01T10:00:00Z", MilestoneState.ADOPTED),
                milestone(2, MilestoneKind.RECEIPT_AT_ORIGIN, "2026-11-01T05:00:00Z", MilestoneState.ADOPTED));

        assertThat(deriver.derive(milestones))
                .isEqualTo(new DerivedStatus(TrackingStatus.IN_TRANSIT, OptionalInt.of(1)));
    }

    @Test
    void 発生時刻が同じなら実績番号の大きい後の登録で導出する() {
        List<Milestone> milestones = List.of(
                milestone(1, MilestoneKind.PICKUP, "2026-11-01T05:00:00Z", MilestoneState.ADOPTED),
                milestone(2, MilestoneKind.RECEIPT_AT_ORIGIN, "2026-11-01T05:00:00Z", MilestoneState.ADOPTED));

        assertThat(deriver.derive(milestones))
                .isEqualTo(new DerivedStatus(TrackingStatus.RECEIVED_AT_ORIGIN, OptionalInt.of(2)));
    }

    /** 確認中の実績は現在状態を確認中にする（Bolt 39gc。下の Bolt39gc_確認中の導出）ので、ここでは下書きと保持のみだけを置く。 */
    @Test
    void 下書きと保持のみの実績は導出に入れない() {
        List<Milestone> milestones = List.of(
                milestone(1, MilestoneKind.PICKUP, "2026-11-01T05:00:00Z", MilestoneState.ADOPTED),
                milestone(2, MilestoneKind.DEPARTURE, "2026-11-01T06:00:00Z", MilestoneState.DRAFT),
                milestone(3, MilestoneKind.DELIVERY, "2026-11-01T08:00:00Z", MilestoneState.RETAINED_ONLY));

        assertThat(deriver.derive(milestones))
                .isEqualTo(new DerivedStatus(TrackingStatus.PICKED_UP, OptionalInt.of(1)));
    }

    /** Bolt 39gc の行 3（【業務の判断】(ah)）。 */
    @Nested
    class Bolt39gc_確認中の導出 {

        @Test
        void 確認中の実績があれば現在状態は確認中で根拠は実績番号の最も小さい確認中で理由は時系列に反する() {
            List<Milestone> milestones = List.of(
                    milestone(1, MilestoneKind.ARRIVAL, "2026-11-01T05:00:00Z", MilestoneState.ADOPTED),
                    milestone(2, MilestoneKind.PICKUP, "2026-11-01T09:00:00Z", MilestoneState.UNDER_REVIEW),
                    milestone(3, MilestoneKind.DELIVERY, "2026-11-01T10:00:00Z", MilestoneState.ADOPTED),
                    milestone(4, MilestoneKind.PICKUP, "2026-11-01T06:00:00Z", MilestoneState.UNDER_REVIEW));

            assertThat(deriver.derive(milestones))
                    .isEqualTo(new DerivedStatus(
                            TrackingStatus.UNDER_REVIEW,
                            OptionalInt.of(2),
                            Optional.of(UnderReviewReason.OUT_OF_SEQUENCE)));
        }

        @Test
        void 確認中の実績がなければ理由はなく採用済みから導く() {
            List<Milestone> milestones =
                    List.of(milestone(1, MilestoneKind.ARRIVAL, "2026-11-01T05:00:00Z", MilestoneState.ADOPTED));

            assertThat(deriver.derive(milestones).underReviewReason()).isEmpty();
            assertThat(deriver.derive(milestones).status()).isEqualTo(TrackingStatus.ARRIVED_AT_DESTINATION);
        }

        @Test
        void 採用済みから導いた状態は確認中の実績を見ない() {
            List<Milestone> milestones = List.of(
                    milestone(1, MilestoneKind.DELIVERY, "2026-11-01T05:00:00Z", MilestoneState.ADOPTED),
                    milestone(2, MilestoneKind.PICKUP, "2026-11-01T09:00:00Z", MilestoneState.UNDER_REVIEW));

            assertThat(deriver.deriveFromAdopted(milestones))
                    .isEqualTo(new DerivedStatus(TrackingStatus.DELIVERED, OptionalInt.of(1)));
        }

        @Test
        void 確認中の現在状態と理由はそろう() {
            OptionalInt basis = OptionalInt.of(1);
            Optional<UnderReviewReason> none = Optional.empty();
            Optional<UnderReviewReason> reason = Optional.of(UnderReviewReason.OUT_OF_SEQUENCE);

            assertThatThrownBy(() -> new DerivedStatus(TrackingStatus.UNDER_REVIEW, basis, none))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new DerivedStatus(TrackingStatus.PICKED_UP, basis, reason))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Milestone milestone(int no, MilestoneKind kind, String occurredAt, MilestoneState state) {
        return new Milestone(
                no,
                kind,
                new Location("JPTYO"),
                at(occurredAt),
                new Source(SourceKind.FIELD_RECORD, "F-" + no, at("2026-11-01T12:00:00Z")),
                state,
                REGISTRANT,
                at("2026-11-01T12:00:00Z"));
    }
}

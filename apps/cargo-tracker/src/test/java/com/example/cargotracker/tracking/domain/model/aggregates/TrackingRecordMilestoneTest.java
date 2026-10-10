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
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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

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
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalOrigin;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** 追跡記録が DE-13 の値を受け取る（Bolt 39g の行 1〜4・16〜19。判定の順は決定 (7)）。 */
class TrackingRecordExternalMilestoneTest {

    private static final UtcInstant OCCURRED_AT = at("2026-11-19T14:00:00Z");
    private static final UtcInstant ACQUIRED_AT = at("2026-11-19T14:30:00Z");
    private static final UtcInstant PUBLISHED_AT = at("2026-11-19T15:00:00Z");
    private static final UtcInstant RECEIVED_AT = at("2026-11-19T15:00:05Z");
    private static final UserId STEWARD = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000390b"));
    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-00000000390c");
    private static final UserId MANAGER = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000390d"));
    private static final Location TOKYO = new Location("JPTYO");
    private static final String RECEIVED_RECORD = "11111111-1111-1111-1111-111111111111";

    private static ExternalMilestone external(String receivedRecordId) {
        return external(receivedRecordId, MilestoneKind.DEPARTURE, TOKYO, OCCURRED_AT, ACQUIRED_AT);
    }

    private static ExternalMilestone external(
            String receivedRecordId,
            MilestoneKind kind,
            Location location,
            UtcInstant occurredAt,
            UtcInstant acquiredAt) {
        return new ExternalMilestone(
                TRACKING_NUMBER,
                kind,
                location,
                occurredAt,
                new Source(SourceKind.EXTERNAL_RECORD, receivedRecordId, acquiredAt),
                new ExternalOrigin(SOURCE_ID, "MS-0001", PUBLISHED_AT),
                STEWARD);
    }

    private static TrackingRecord trackingRecord(TrackedBookingStatus bookingStatus, TrackingStatus status) {
        return trackingRecord(bookingStatus, status, OptionalInt.empty(), List.of());
    }

    private static TrackingRecord trackingRecord(
            TrackedBookingStatus bookingStatus, TrackingStatus status, OptionalInt basis, List<Milestone> milestones) {
        return TrackingRecord.reconstitute(
                TRACKING_NUMBER,
                BOOKING_ID,
                SHIPPER,
                CONSIGNEE,
                bookingStatus,
                schedule(),
                status,
                basis,
                milestones,
                at("2026-11-15T00:00:00Z"),
                at("2026-11-16T03:00:00Z"),
                STARTED_AT,
                3);
    }

    private static TrackingRecord confirmed() {
        return trackingRecord(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED);
    }

    private static ExternalMilestoneReceipt.Received received(ExternalMilestoneReceipt receipt) {
        assertThat(receipt).isInstanceOf(ExternalMilestoneReceipt.Received.class);
        return (ExternalMilestoneReceipt.Received) receipt;
    }

    /** 受け取った直後の追跡記録（DE-13 を 1 回受けた後）。 */
    private static TrackingRecord withDraft() {
        return received(confirmed().receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                .trackingRecord();
    }

    @Nested
    class Bolt39g_受け取り {

        @Test
        void 行1_確定の追跡に受けると下書きの実績が1件増え値と出典と外部の識別と操作者と受けた時刻を持つ() {
            ExternalMilestoneReceipt.Received receipt =
                    received(confirmed().receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT));

            assertThat(receipt.trackingRecord().milestones()).containsExactly(receipt.milestone());
            Milestone milestone = receipt.milestone();
            assertThat(milestone.milestoneNo()).isEqualTo(1);
            assertThat(milestone.state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(milestone.kind()).isEqualTo(MilestoneKind.DEPARTURE);
            assertThat(milestone.location()).isEqualTo(TOKYO);
            assertThat(milestone.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(milestone.source())
                    .isEqualTo(new Source(SourceKind.EXTERNAL_RECORD, RECEIVED_RECORD, ACQUIRED_AT));
            assertThat(milestone.externalOrigin()).isEqualTo(new ExternalOrigin(SOURCE_ID, "MS-0001", PUBLISHED_AT));
            assertThat(milestone.registeredBy()).isEqualTo(STEWARD);
            assertThat(milestone.registeredAt()).isEqualTo(RECEIVED_AT);
        }

        @Test
        void 行1_2件目の実績番号は次の番号になる() {
            ExternalMilestoneReceipt.Received receipt = received(withDraft()
                    .receiveExternalMilestone(external("22222222-2222-2222-2222-222222222222"), RECEIVED_AT));

            assertThat(receipt.milestone().milestoneNo()).isEqualTo(2);
            assertThat(receipt.trackingRecord().milestones()).hasSize(2);
        }

        @Test
        void 行2_下書きを受けても現在状態と根拠の実績番号は変わらない() {
            TrackingRecord updated = received(
                            confirmed().receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .trackingRecord();

            assertThat(updated.currentStatus()).isEqualTo(TrackingStatus.PICKUP_SCHEDULED);
            assertThat(updated.statusBasisMilestoneNo()).isEmpty();
        }

        @Test
        void 行2_採用した実績があっても下書きで現在状態と根拠の実績番号は変わらない() {
            TrackingRecord adopted = confirmed()
                    .registerMilestone(
                            MilestoneKind.PICKUP,
                            TOKYO,
                            at("2026-11-18T00:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-118", at("2026-11-18T00:30:00Z")),
                            MANAGER,
                            at("2026-11-18T00:30:00Z"))
                    .trackingRecord();

            TrackingRecord updated = received(adopted.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .trackingRecord();

            assertThat(updated.currentStatus()).isEqualTo(TrackingStatus.PICKED_UP);
            assertThat(updated.statusBasisMilestoneNo()).hasValue(1);
            assertThat(updated.milestones()).hasSize(2);
        }

        @Test
        void 行3_同じ受信記録IDのDE13がもう1度届くと実績は増えず既存の実績を返す() {
            TrackingRecord draft = withDraft();

            ExternalMilestoneReceipt receipt = draft.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT);

            assertThat(receipt)
                    .isEqualTo(new ExternalMilestoneReceipt.AlreadyReceived(
                            draft.milestones().getFirst()));
        }

        static Stream<Arguments> sameSourceWithDifferentValues() {
            UtcInstant other = at("2026-11-19T16:00:00Z");
            return Stream.of(
                    Arguments.of("取得時刻だけ違う", MilestoneKind.DEPARTURE, TOKYO, OCCURRED_AT, other),
                    Arguments.of("種類だけ違う", MilestoneKind.ARRIVAL, TOKYO, OCCURRED_AT, ACQUIRED_AT),
                    Arguments.of("場所だけ違う", MilestoneKind.DEPARTURE, new Location("KRPUS"), OCCURRED_AT, ACQUIRED_AT),
                    Arguments.of("発生時刻だけ違う", MilestoneKind.DEPARTURE, TOKYO, other, ACQUIRED_AT));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sameSourceWithDifferentValues")
        void 行4_同じ受信記録IDで値が違っても実績は増えず既存の実績の値を変えない(
                String description,
                MilestoneKind kind,
                Location location,
                UtcInstant occurredAt,
                UtcInstant acquiredAt) {
            TrackingRecord draft = withDraft();
            Milestone existing = draft.milestones().getFirst();

            ExternalMilestoneReceipt receipt = draft.receiveExternalMilestone(
                    external(RECEIVED_RECORD, kind, location, occurredAt, acquiredAt), RECEIVED_AT);

            assertThat(receipt).as(description).isEqualTo(new ExternalMilestoneReceipt.AlreadyReceived(existing));
            assertThat(draft.milestones()).as(description).containsExactly(existing);
        }

        @Test
        void 行16_発生時刻が受けた時刻より後でも下書きで受ける() {
            UtcInstant future = at("2026-12-01T00:00:00Z");

            ExternalMilestoneReceipt.Received receipt = received(confirmed()
                    .receiveExternalMilestone(
                            external(RECEIVED_RECORD, MilestoneKind.DEPARTURE, TOKYO, future, ACQUIRED_AT),
                            RECEIVED_AT));

            assertThat(receipt.milestone().state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(receipt.milestone().occurredAt()).isEqualTo(future);
        }
    }

    @Nested
    class Bolt39g_取消済みと完了後 {

        @Test
        void 行17_取消済みの予約は実績を作らず受け取りの結果で知らせる() {
            TrackingRecord cancelled = trackingRecord(TrackedBookingStatus.CANCELLED, TrackingStatus.PICKUP_SCHEDULED);

            ExternalMilestoneReceipt receipt =
                    cancelled.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT);

            assertThat(receipt).isEqualTo(new ExternalMilestoneReceipt.BookingCancelled());
            assertThat(cancelled.milestones()).isEmpty();
            assertThat(cancelled.aggregateVersion()).isEqualTo(3);
        }

        @ParameterizedTest(name = "予約状態 {0}・現在状態 {1}")
        @CsvSource({"CONFIRMED, DELIVERED", "COMPLETED, IN_TRANSIT", "COMPLETED, DELIVERED"})
        void 行18_完了後は保持のみの実績を作り現在状態と根拠の実績番号は変わらない(TrackedBookingStatus bookingStatus, TrackingStatus status) {
            Milestone delivery = new Milestone(
                    1,
                    MilestoneKind.DELIVERY,
                    TOKYO,
                    at("2026-11-18T00:00:00Z"),
                    new Source(SourceKind.FIELD_RECORD, "F-1", at("2026-11-18T00:00:00Z")),
                    MilestoneState.ADOPTED,
                    MANAGER,
                    at("2026-11-18T00:00:00Z"));
            TrackingRecord completed = trackingRecord(bookingStatus, status, OptionalInt.of(1), List.of(delivery));

            ExternalMilestoneReceipt.Received receipt =
                    received(completed.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT));

            assertThat(receipt.milestone().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(receipt.milestone().milestoneNo()).isEqualTo(2);
            assertThat(receipt.trackingRecord().currentStatus()).isEqualTo(status);
            assertThat(receipt.trackingRecord().statusBasisMilestoneNo()).hasValue(1);
        }

        @Test
        void 行19_取消済みかつ引渡し済みは取消済みが先で実績を作らない() {
            TrackingRecord cancelledAndDelivered =
                    trackingRecord(TrackedBookingStatus.CANCELLED, TrackingStatus.DELIVERED);

            ExternalMilestoneReceipt receipt =
                    cancelledAndDelivered.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT);

            assertThat(receipt).isEqualTo(new ExternalMilestoneReceipt.BookingCancelled());
        }

        @ParameterizedTest(name = "予約状態 {0}")
        @CsvSource({"CANCELLED", "COMPLETED"})
        void 行32b_行32c_同じ出典は取消済みと完了後より先で既存の実績を返す(TrackedBookingStatus changedTo) {
            TrackingRecord draft = withDraft();
            TrackingRecord changed =
                    trackingRecord(changedTo, TrackingStatus.PICKUP_SCHEDULED, OptionalInt.empty(), draft.milestones());

            ExternalMilestoneReceipt receipt = changed.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT);

            assertThat(receipt)
                    .isEqualTo(new ExternalMilestoneReceipt.AlreadyReceived(
                            draft.milestones().getFirst()));
            assertThat(changed.milestones().getFirst().state()).isEqualTo(MilestoneState.DRAFT);
        }
    }

    /**
     * Bolt 39g3 の行 11（DE-13 の受け取りは採用の実績を変えないので、最新の到着見込みと最終取得時刻を保存した値のまま持つ。現在状態と同じく導出し直さない）。
     * 組み立ての見込みは当初の到着予定と違う値（2026-11-16 03:00）にして、受け取りで導出し直していないことを確かめる。
     */
    @Nested
    class Bolt39g3_受け取りの最新の到着見込み {

        @Test
        void 行11_下書きを足しても見込みと最終取得時刻は変わらない() {
            TrackingRecord before = confirmed();

            TrackingRecord after = received(before.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .trackingRecord();

            assertThat(after.milestones().getFirst().state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(after.latestEta()).isEqualTo(at("2026-11-16T03:00:00Z"));
            assertThat(after.lastAcquiredAt()).isEmpty();
        }

        @Test
        void 行11_完了後の保持のみを足しても見込みと最終取得時刻は変わらない() {
            TrackingRecord before = trackingRecord(TrackedBookingStatus.COMPLETED, TrackingStatus.DELIVERED);

            TrackingRecord after = received(before.receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .trackingRecord();

            assertThat(after.milestones().getFirst().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(after.latestEta()).isEqualTo(at("2026-11-16T03:00:00Z"));
            assertThat(after.lastAcquiredAt()).isEmpty();
        }

        @Test
        void 行11_取消済みは実績を作らない() {
            assertThat(trackingRecord(TrackedBookingStatus.CANCELLED, TrackingStatus.PICKUP_SCHEDULED)
                            .receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .isInstanceOf(ExternalMilestoneReceipt.BookingCancelled.class);
        }
    }

    /**
     * Bolt 39g4 の行 12（DE-13 の受け取りは採用の実績を変えないので、次の予定の実績の予定時刻を保存した値のまま持つ。39g3 の議題 1 の形）。組み立ての値は
     * 導出と違う値（区間 2 の到着予定 2026-11-15 00:00）にして、受け取りで導出し直していないことを確かめ、その後の採用で導出し直すことも確かめる
     * （導出しない経路の後に導出が走る手順を組に入れる。39g3 の M6 の学び）。
     */
    @Nested
    class Bolt39g4_受け取りの次の予定の実績 {

        private static final UtcInstant SAVED = at("2026-11-15T00:00:00Z");

        private static TrackingRecord withSavedNextExpected(TrackedBookingStatus bookingStatus, TrackingStatus status) {
            return TrackingRecord.reconstitute(
                    TRACKING_NUMBER,
                    BOOKING_ID,
                    SHIPPER,
                    CONSIGNEE,
                    bookingStatus,
                    schedule(),
                    status,
                    OptionalInt.empty(),
                    Optional.empty(),
                    List.of(),
                    at("2026-11-15T00:00:00Z"),
                    at("2026-11-15T00:00:00Z"),
                    Optional.empty(),
                    Optional.of(SAVED),
                    STARTED_AT,
                    3);
        }

        @Test
        void 行12_下書きを足しても保存した値のままで後の採用で導出し直す() {
            TrackingRecord withDraft = received(
                            withSavedNextExpected(TrackedBookingStatus.CONFIRMED, TrackingStatus.PICKUP_SCHEDULED)
                                    .receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .trackingRecord();

            assertThat(withDraft.milestones().getFirst().state()).isEqualTo(MilestoneState.DRAFT);
            assertThat(withDraft.nextExpectedAt()).contains(SAVED);

            TrackingRecord pickedUp = withDraft
                    .registerMilestone(
                            MilestoneKind.PICKUP,
                            TOKYO,
                            at("2026-10-31T01:00:00Z"),
                            new Source(SourceKind.FIELD_RECORD, "F-1", RECEIVED_AT),
                            MANAGER,
                            RECEIVED_AT)
                    .trackingRecord();

            assertThat(pickedUp.milestones().get(1).state()).isEqualTo(MilestoneState.ADOPTED);
            assertThat(pickedUp.nextExpectedAt()).contains(at("2026-11-01T00:00:00Z"));
        }

        @Test
        void 行12_完了後の保持のみを足しても保存した値のまま() {
            TrackingRecord after = received(
                            withSavedNextExpected(TrackedBookingStatus.COMPLETED, TrackingStatus.DELIVERED)
                                    .receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .trackingRecord();

            assertThat(after.milestones().getFirst().state()).isEqualTo(MilestoneState.RETAINED_ONLY);
            assertThat(after.nextExpectedAt()).contains(SAVED);
        }

        @Test
        void 行12_取消済みは実績を作らない() {
            assertThat(withSavedNextExpected(TrackedBookingStatus.CANCELLED, TrackingStatus.PICKUP_SCHEDULED)
                            .receiveExternalMilestone(external(RECEIVED_RECORD), RECEIVED_AT))
                    .isInstanceOf(ExternalMilestoneReceipt.BookingCancelled.class);
        }
    }
}

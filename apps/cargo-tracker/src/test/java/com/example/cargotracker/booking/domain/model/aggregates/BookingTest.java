package com.example.cargotracker.booking.domain.model.aggregates;

import static com.example.cargotracker.booking.domain.model.BookingFixture.SALES;
import static com.example.cargotracker.booking.domain.model.BookingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.booking.domain.model.BookingFixture.allConditions;
import static com.example.cargotracker.booking.domain.model.BookingFixture.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.booking.domain.model.entities.BookingVersion;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingConditions;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhase;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhaseRefusal;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 貨物予約の本予約の確定（US-04 AC1、BR-01、B-INV-01・08、DE-07。Bolt 23）。 */
class BookingTest {

    private static final BookingId ID = new BookingId(UUID.fromString("00000000-0000-0000-0000-0000000e0001"));
    private static final UtcInstant COMMITTED_AT = new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"));

    @Test
    void 五つの確定条件がそろうと確定し予約版1と追跡番号とcommit時刻と確定者が残る() {
        BookingConfirmation confirmation =
                Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT);

        Booking booking = confirmation.booking();
        assertThat(booking.id()).isEqualTo(ID);
        assertThat(booking.trackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.transportPhase()).isEqualTo(TransportPhase.BEFORE_PICKUP);
        assertThat(booking.transportRequestNumber()).isEqualTo("TR-2026-0001");
        assertThat(booking.shipperCompanyId()).isEqualTo(terms().shipperCompanyId());
        assertThat(booking.aggregateVersion()).isZero();
        BookingVersion version = booking.currentVersion();
        assertThat(version.versionNo()).isEqualTo(1);
        assertThat(version.terms()).isEqualTo(terms());
        assertThat(version.confirmedBy()).isEqualTo(SALES);
        assertThat(version.committedAt()).isEqualTo(COMMITTED_AT);
        assertThat(booking.versions()).containsExactly(version);
    }

    @Test
    void 確定するとDE07を返す() {
        BookingConfirmed event = Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT)
                .event();

        assertThat(event)
                .isEqualTo(new BookingConfirmed(
                        ID.value(),
                        1,
                        "CTABCDEFGH2345",
                        terms().quotationId(),
                        terms().transportRequestId(),
                        2,
                        "TR-2026-0001",
                        "RC-2026-0001",
                        1,
                        COMMITTED_AT,
                        0,
                        terms().shipperCompanyId(),
                        terms().consigneeCompanyId()));
    }

    @Test
    void 確定条件が一つでも欠けると確定せず欠けた条件を示す() {
        BookingConditions withoutStaff = new BookingConditions(true, true, true, true, false);

        assertThatThrownBy(() -> Booking.confirm(ID, withoutStaff, terms(), TRACKING_NUMBER, SALES, COMMITTED_AT))
                .isInstanceOfSatisfying(
                        BookingConfirmationRejected.class,
                        rejected -> assertThat(rejected.missingConditions())
                                .containsExactly(BookingCondition.STAFF_CONFIRMATION));
    }

    @Test
    void 特殊貨物を含む予約条件では確定条件がそろっても確定しない() {
        BookingTerms special = withCargoCategory(terms(), "DANGEROUS");
        BookingConditions conditions = allConditions();

        assertThatThrownBy(() -> Booking.confirm(ID, conditions, special, TRACKING_NUMBER, SALES, COMMITTED_AT))
                .isInstanceOf(SpecialCargoRejected.class);
    }

    @Test
    void 特殊貨物は確定条件の不足より先に拒否する() {
        BookingTerms special = withCargoCategory(terms(), "OTHER_SPECIAL");
        BookingConditions withoutStaff = new BookingConditions(true, true, true, true, false);

        assertThatThrownBy(() -> Booking.confirm(ID, withoutStaff, special, TRACKING_NUMBER, SALES, COMMITTED_AT))
                .isInstanceOf(SpecialCargoRejected.class);
    }

    private static BookingTerms withCargoCategory(BookingTerms terms, String cargoCategory) {
        return new BookingTerms(
                terms.transportRequestId(),
                terms.transportRequestVersionNo(),
                terms.transportRequestNumber(),
                terms.quotationId(),
                terms.quotationNo(),
                terms.shipperCompanyId(),
                terms.consigneeCompanyId(),
                terms.routingCaseNumber(),
                terms.routeVersionNo(),
                cargoCategory,
                terms.cargoSummary(),
                terms.shipperApproverId());
    }

    @Test
    void 保存された状態から組み立てても同じ値を持つ() {
        Booking confirmed = Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT)
                .booking();

        Booking reconstituted = Booking.reconstitute(
                confirmed.id(),
                confirmed.trackingNumber(),
                confirmed.status(),
                confirmed.transportPhase(),
                confirmed.versions(),
                3);

        assertThat(reconstituted.currentVersion()).isEqualTo(confirmed.currentVersion());
        assertThat(reconstituted.aggregateVersion()).isEqualTo(3);
        assertThat(reconstituted.transportRequestNumber()).isEqualTo("TR-2026-0001");
    }

    /** Bolt 39g2 の行 1〜3（集荷・引渡しの記録と届く順。BR-04、B-INV-12 の代わりに段を戻さない。【業務の判断】(am)）。 */
    @Nested
    class Bolt39g2_輸送段階の記録 {

        private static final long VERSION = 4;

        private Booking booking(BookingStatus status, TransportPhase phase) {
            Booking confirmed = Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT)
                    .booking();
            return Booking.reconstitute(
                    confirmed.id(), confirmed.trackingNumber(), status, phase, confirmed.versions(), VERSION);
        }

        /** 結果の名前（進めた CHANGED、記録済み UNCHANGED、記録しないは理由の名前）。 */
        private static String outcome(TransportPhaseChange change) {
            return switch (change) {
                case TransportPhaseChange.Changed _ -> "CHANGED";
                case TransportPhaseChange.Unchanged _ -> "UNCHANGED";
                case TransportPhaseChange.Refused refused -> refused.reason().name();
            };
        }

        private static void assertChanged(
                Booking before, TransportPhaseChange change, BookingStatus status, TransportPhase phase) {
            assertThat(change).isInstanceOfSatisfying(TransportPhaseChange.Changed.class, changed -> {
                Booking after = changed.booking();
                assertThat(after.status()).isEqualTo(status);
                assertThat(after.transportPhase()).isEqualTo(phase);
                assertThat(after.id()).isEqualTo(before.id());
                assertThat(after.versions()).isEqualTo(before.versions());
                // 版はリポジトリが保存のときに増やす（追跡記録と同じ）
                assertThat(after.aggregateVersion()).isEqualTo(VERSION);
            });
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "確定・集荷前は輸送中・集荷後に進める, CONFIRMED, BEFORE_PICKUP, CHANGED",
            "輸送中・集荷後は記録済みで段を戻さない, IN_TRANSIT, AFTER_PICKUP, UNCHANGED",
            "完了・完了は記録済みで段を戻さない, COMPLETED, COMPLETED, UNCHANGED",
            "取消済みは記録しない, CANCELLED, BEFORE_PICKUP, BOOKING_CANCELLED",
            "変更承認待ちは記録しない, AMENDMENT_PENDING, BEFORE_PICKUP, BOOKING_UNDER_REQUEST",
            "取消承認待ちは記録しない, CANCELLATION_PENDING, BEFORE_PICKUP, BOOKING_UNDER_REQUEST",
            "変更中は記録しない, AMENDING, BEFORE_PICKUP, BOOKING_UNDER_REQUEST"
        })
        void 行1_集荷の記録(String name, BookingStatus status, TransportPhase phase, String expected) {
            Booking before = booking(status, phase);

            TransportPhaseChange change = before.recordPickup();

            assertThat(outcome(change)).isEqualTo(expected);
            if ("CHANGED".equals(expected)) {
                assertChanged(before, change, BookingStatus.IN_TRANSIT, TransportPhase.AFTER_PICKUP);
            }
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "確定・集荷前は完了・完了に進める（集荷の通知より先）, CONFIRMED, BEFORE_PICKUP, CHANGED",
            "輸送中・集荷後は完了・完了に進める, IN_TRANSIT, AFTER_PICKUP, CHANGED",
            "完了・完了は記録済み, COMPLETED, COMPLETED, UNCHANGED",
            "取消済みは記録しない, CANCELLED, BEFORE_PICKUP, BOOKING_CANCELLED",
            "変更承認待ちは記録しない, AMENDMENT_PENDING, BEFORE_PICKUP, BOOKING_UNDER_REQUEST",
            "取消承認待ちは記録しない, CANCELLATION_PENDING, BEFORE_PICKUP, BOOKING_UNDER_REQUEST",
            "変更中は記録しない, AMENDING, BEFORE_PICKUP, BOOKING_UNDER_REQUEST"
        })
        void 行2_引渡しの記録(String name, BookingStatus status, TransportPhase phase, String expected) {
            Booking before = booking(status, phase);

            TransportPhaseChange change = before.recordDelivery();

            assertThat(outcome(change)).isEqualTo(expected);
            if ("CHANGED".equals(expected)) {
                assertChanged(before, change, BookingStatus.COMPLETED, TransportPhase.COMPLETED);
            }
        }

        /** 行 3。通知を届いた順に当て、進めた予約に次の通知を当てる。どの順でも段は戻らない。 */
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "集荷 → 引渡し, PICKUP, DELIVERY, CHANGED, COMPLETED",
            "引渡し → 集荷（後から届いた集荷は記録済み）, DELIVERY, PICKUP, UNCHANGED, COMPLETED",
            "集荷を 2 度, PICKUP, PICKUP, UNCHANGED, IN_TRANSIT",
            "引渡しを 2 度, DELIVERY, DELIVERY, UNCHANGED, COMPLETED"
        })
        void 行3_届く順によらず段は戻らない(
                String name, String first, String second, String secondOutcome, BookingStatus finalStatus) {
            Booking confirmed = booking(BookingStatus.CONFIRMED, TransportPhase.BEFORE_PICKUP);

            TransportPhaseChange firstChange = notify(confirmed, first);
            assertThat(outcome(firstChange)).isEqualTo("CHANGED");
            Booking afterFirst = ((TransportPhaseChange.Changed) firstChange).booking();
            TransportPhaseChange secondChange = notify(afterFirst, second);

            assertThat(outcome(secondChange)).isEqualTo(secondOutcome);
            Booking last = secondChange instanceof TransportPhaseChange.Changed(Booking changed) ? changed : afterFirst;
            assertThat(last.status()).isEqualTo(finalStatus);
            assertThat(last.transportPhase())
                    .isEqualTo(
                            finalStatus == BookingStatus.COMPLETED
                                    ? TransportPhase.COMPLETED
                                    : TransportPhase.AFTER_PICKUP);
        }

        private static TransportPhaseChange notify(Booking booking, String kind) {
            return "PICKUP".equals(kind) ? booking.recordPickup() : booking.recordDelivery();
        }

        @Test
        void 記録しない理由は取消済みと申請中の2つ() {
            assertThat(TransportPhaseRefusal.values())
                    .containsExactly(
                            TransportPhaseRefusal.BOOKING_CANCELLED, TransportPhaseRefusal.BOOKING_UNDER_REQUEST);
        }
    }
}

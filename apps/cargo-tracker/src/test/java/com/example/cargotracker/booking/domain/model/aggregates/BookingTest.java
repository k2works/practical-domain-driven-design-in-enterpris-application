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
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhase;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}

package com.example.cargotracker.booking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** C-06 予約一覧の荷主の照会（BR-07。Bolt 27b）。荷主企業での絞り込みはリポジトリの照会で行い、他社の予約は返さない。 */
class CustomerBookingQueryServiceTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000027b01"));
    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000027b02"));

    private final InMemoryBookingRepository bookings = new InMemoryBookingRepository();
    private final InMemoryBookingSagaRepository sagas = new InMemoryBookingSagaRepository();
    private final CustomerBookingQueryService service = new CustomerBookingQueryService(bookings, sagas);

    @Test
    void 一覧は自社の予約だけを確定時刻の新しい順に予約サガの状態とともに返す() {
        Booking older = booked(1, "2026-10-07T00:00:00Z", SHIPPER);
        Booking newer = booked(2, "2026-10-08T00:00:00Z", SHIPPER);
        booked(3, "2026-10-09T00:00:00Z", OTHER_SHIPPER);
        sagas.update(sagas.findByBookingId(newer.id()).orElseThrow().complete());

        RecentBookings recent = service.recent(SHIPPER);

        assertThat(recent.rows())
                .extracting(RecentBookings.Row::trackingNumber)
                .containsExactly(newer.trackingNumber(), older.trackingNumber());
        assertThat(recent.rows().getFirst())
                .isEqualTo(new RecentBookings.Row(
                        newer.trackingNumber(),
                        "TR-2026-0002",
                        1,
                        new UtcInstant(Instant.parse("2026-10-08T00:00:00Z")),
                        BookingSagaStatus.COMPLETED));
        assertThat(recent.rows().get(1).sagaStatus()).isEqualTo(BookingSagaStatus.IN_PROGRESS);
    }

    @Test
    void 他社の予約しかなければ空の一覧を返す() {
        booked(1, "2026-10-07T00:00:00Z", OTHER_SHIPPER);

        RecentBookings recent = service.recent(SHIPPER);

        assertThat(recent.rows()).isEmpty();
    }

    @Test
    void 一覧は自社の上限の50件までを返し超えたことを示す() {
        for (int i = 1; i <= 51; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString(), SHIPPER);
        }
        booked(52, "2026-10-02T00:00:00Z", OTHER_SHIPPER);

        RecentBookings recent = service.recent(SHIPPER);

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isTrue();
        assertThat(recent.limit()).isEqualTo(50);
        assertThat(recent.rows().getFirst().transportRequestNumber()).isEqualTo("TR-2026-0051");
    }

    /** 自社の予約がちょうど上限なら、他社の予約があっても超えたと示さない（他社を数えない）。 */
    @Test
    void 自社の予約がちょうど50件なら他社の予約があっても上限を超えたと示さない() {
        for (int i = 1; i <= 50; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString(), SHIPPER);
        }
        booked(51, "2026-10-02T00:00:00Z", OTHER_SHIPPER);

        RecentBookings recent = service.recent(SHIPPER);

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isFalse();
    }

    /** 予約の要約と予約サガの状態を、それぞれ 1 回の照会で引く（行ごとに照会しない。仮説 H1）。 */
    @Test
    void 一覧は予約の要約と予約サガの状態をそれぞれ1回の照会で引く() {
        for (int i = 1; i <= 3; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString(), SHIPPER);
        }
        AtomicInteger summaryQueries = new AtomicInteger();
        AtomicInteger statusQueries = new AtomicInteger();
        CustomerBookingQueryService counted = new CustomerBookingQueryService(
                new InMemoryBookingRepository() {
                    @Override
                    public List<BookingSummary> findRecentSummariesByShipper(CompanyId shipperCompanyId, int limit) {
                        summaryQueries.incrementAndGet();
                        return bookings.findRecentSummariesByShipper(shipperCompanyId, limit);
                    }
                },
                new InMemoryBookingSagaRepository() {
                    @Override
                    public Map<BookingId, BookingSagaStatus> findStatusesByBookingIds(Set<BookingId> bookingIds) {
                        statusQueries.incrementAndGet();
                        return sagas.findStatusesByBookingIds(bookingIds);
                    }
                });

        assertThat(counted.recent(SHIPPER).rows()).hasSize(3);
        assertThat(summaryQueries).hasValue(1);
        assertThat(statusQueries).hasValue(1);
    }

    @Test
    void 予約サガのない予約は不変条件の違反にする() {
        Booking booking = booked(1, "2026-10-07T00:00:00Z", SHIPPER);
        sagas.clear();

        assertThatThrownBy(() -> service.recent(SHIPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(booking.id().value().toString());
    }

    @Test
    void 一覧の行の項目は荷主に示してよいものだけ() {
        // 見張りのテスト。予約 ID・荷主企業 ID などの社内の識別子を行に足すと、荷主の画面に出せてしまうので落ちる（BR-07。
        // Bolt 27 の P-2）。項目を足すときは荷主に示してよいかを決めてから、ここも直す
        assertThat(Arrays.stream(RecentBookings.Row.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly(
                        "trackingNumber", "transportRequestNumber", "quotationNo", "committedAt", "sagaStatus");
    }

    private Booking booked(int sequence, String committedAt, CompanyId shipper) {
        BookingTerms base = BookingFixture.terms();
        BookingTerms terms = new BookingTerms(
                UUID.randomUUID(),
                base.transportRequestVersionNo(),
                String.format("TR-2026-%04d", sequence),
                UUID.randomUUID(),
                1,
                shipper,
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
        UtcInstant at = new UtcInstant(Instant.parse(committedAt));
        Booking booking = Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        terms,
                        TrackingNumber.generate(new Random(sequence)),
                        BookingFixture.SALES,
                        at)
                .booking();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagas.save(BookingSaga.start(booking.id(), booking.trackingNumber(), at));
        return booking;
    }
}

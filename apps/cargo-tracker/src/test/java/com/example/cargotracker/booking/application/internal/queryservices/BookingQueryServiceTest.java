package com.example.cargotracker.booking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 予約の照会（S-09・S-24。Bolt 23b）。S-09 を開いたときの判定は開いた時刻での参考で（ADR-016）、確定済みは予約の側で判定する
 * （見積りの公開 API では分からない）。S-24 は予約と予約サガの状態を返す。
 */
class BookingQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");
    private static final UtcInstant APPROVED_AT = new UtcInstant(Instant.parse("2026-10-07T06:00:00Z"));
    private static final UtcInstant EXPIRES_AT = new UtcInstant(Instant.parse("2099-10-08T09:00:00Z"));

    private final InMemoryBookingRepository bookings = new InMemoryBookingRepository();
    private final InMemoryBookingSagaRepository sagas = new InMemoryBookingSagaRepository();
    private final List<BookableQuotationRequest> queries = new ArrayList<>();
    private BookableQuotationResult quotation = bookable();

    private final BookingQueryService service = new BookingQueryService(
            bookings,
            sagas,
            new QuotationBookability(request -> {
                queries.add(request);
                return quotation;
            }),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 確定に使える見積りは開いた時刻で照会して確定条件の表示に要る値を返す() {
        assertThat(service.confirmation("TR-2026-0001", 1))
                .isEqualTo(new BookingConfirmationPage.Available(BookingFixture.terms(), APPROVED_AT, EXPIRES_AT));
        assertThat(queries).containsExactly(new BookableQuotationRequest("TR-2026-0001", 1, new UtcInstant(NOW)));
    }

    @Test
    void 確定に使えない見積りは理由を返す() {
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirmation("TR-2026-0001", 1))
                .isEqualTo(new BookingConfirmationPage.Unavailable(QuotationUnavailability.EXPIRED));
    }

    @Test
    void 同じ見積りの予約があれば見積りを照会せずに既存の追跡番号を返す() {
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirmation("TR-2026-0001", 1))
                .as("確定の後に見積りが失効しても、失効でなく同じ見積りの予約があると示す（Bolt 24、H1）")
                .isEqualTo(new BookingConfirmationPage.AlreadyBooked(BookingFixture.TRACKING_NUMBER));
        assertThat(queries).isEmpty();
    }

    @Test
    void 予約の詳細は予約と予約サガの状態を返しない追跡番号は空() {
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        sagas.save(BookingSaga.start(booking.id(), booking.trackingNumber(), new UtcInstant(NOW)));

        assertThat(service.detail(booking.trackingNumber()))
                .contains(new BookingDetail(booking, BookingSagaStatus.IN_PROGRESS));
        assertThat(service.detail(new TrackingNumber("CTZZZZZZZZZZZZ"))).isEmpty();
    }

    /** S-10 予約一覧（Bolt 25b）。確定時刻の新しい順に、予約サガの状態とともに返す。 */
    @Test
    void 予約一覧は確定時刻の新しい順に予約サガの状態とともに返す() {
        Booking older = booked(1, "2026-10-07T00:00:00Z");
        Booking newer = booked(2, "2026-10-08T00:00:00Z");
        sagas.update(sagas.findByBookingId(newer.id()).orElseThrow().complete());

        RecentBookings recent = service.recent();

        assertThat(recent.truncated()).isFalse();
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
    void 予約一覧は上限の50件までを返し超えたことを示す() {
        for (int i = 1; i <= 51; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }

        RecentBookings recent = service.recent();

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isTrue();
        assertThat(recent.limit()).isEqualTo(50);
        assertThat(recent.rows().getFirst().transportRequestNumber()).isEqualTo("TR-2026-0051");
        assertThat(recent.rows())
                .extracting(RecentBookings.Row::transportRequestNumber)
                .doesNotContain("TR-2026-0001");
    }

    /** 上限ちょうどは超えていない（off-by-one の境界。Bolt 25b レビュー P-1）。 */
    @Test
    void 予約一覧はちょうど50件なら上限を超えたと示さない() {
        for (int i = 1; i <= 50; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }

        RecentBookings recent = service.recent();

        assertThat(recent.rows()).hasSize(50);
        assertThat(recent.truncated()).isFalse();
    }

    @Test
    void 予約がなければ空の一覧を返す() {
        RecentBookings recent = service.recent();

        assertThat(recent.rows()).isEmpty();
        assertThat(recent.truncated()).isFalse();
    }

    /** 予約の要約と予約サガの状態を、それぞれ 1 回の照会で引く（行ごとに照会しない。仮説 H1）。 */
    @Test
    void 予約一覧は予約の要約と予約サガの状態をそれぞれ1回の照会で引く() {
        for (int i = 1; i <= 3; i++) {
            booked(i, Instant.parse("2026-10-01T00:00:00Z").plusSeconds(i * 60L).toString());
        }
        AtomicInteger summaryQueries = new AtomicInteger();
        AtomicInteger statusQueries = new AtomicInteger();
        BookingQueryService counted = new BookingQueryService(
                new InMemoryBookingRepository() {
                    @Override
                    public List<BookingSummary> findRecentSummaries(int limit) {
                        summaryQueries.incrementAndGet();
                        return bookings.findRecentSummaries(limit);
                    }
                },
                new InMemoryBookingSagaRepository() {
                    @Override
                    public Map<BookingId, BookingSagaStatus> findStatusesByBookingIds(Set<BookingId> bookingIds) {
                        statusQueries.incrementAndGet();
                        return sagas.findStatusesByBookingIds(bookingIds);
                    }
                },
                new QuotationBookability(request -> quotation),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(counted.recent().rows()).hasSize(3);
        assertThat(summaryQueries).hasValue(1);
        assertThat(statusQueries).hasValue(1);
    }

    @Test
    void 予約サガのない予約は不変条件の違反にする() {
        Booking booking = booked(1, "2026-10-07T00:00:00Z");
        sagas.clear();

        assertThatThrownBy(service::recent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(booking.id().value().toString());
    }

    private Booking booked(int sequence, String committedAt) {
        BookingTerms base = BookingFixture.terms();
        BookingTerms terms = new BookingTerms(
                UUID.randomUUID(),
                base.transportRequestVersionNo(),
                String.format("TR-2026-%04d", sequence),
                UUID.randomUUID(),
                1,
                base.shipperCompanyId(),
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

    private static Booking confirmed() {
        return Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        BookingFixture.terms(),
                        BookingFixture.TRACKING_NUMBER,
                        BookingFixture.SALES,
                        new UtcInstant(NOW))
                .booking();
    }

    private static BookableQuotationResult bookable() {
        var terms = BookingFixture.terms();
        return new BookableQuotationResult.Bookable(
                terms.transportRequestId(),
                terms.transportRequestVersionNo(),
                terms.transportRequestNumber(),
                terms.quotationId(),
                1,
                terms.shipperCompanyId(),
                terms.consigneeCompanyId(),
                terms.routingCaseNumber(),
                terms.routeVersionNo(),
                terms.cargoCategory(),
                terms.cargoSummary(),
                terms.shipperApproverId(),
                APPROVED_AT,
                EXPIRES_AT);
    }
}

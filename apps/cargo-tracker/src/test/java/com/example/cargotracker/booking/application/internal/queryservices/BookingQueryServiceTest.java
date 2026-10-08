package com.example.cargotracker.booking.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.quotation.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    void 予約がすでにある見積りは確定済みを返す() {
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES);

        assertThat(service.confirmation("TR-2026-0001", 1)).isEqualTo(new BookingConfirmationPage.AlreadyBooked());
    }

    @Test
    void 予約の詳細は予約と予約サガの状態を返しない追跡番号は空() {
        Booking booking = confirmed();
        bookings.save(booking, BookingFixture.SALES);
        sagas.save(BookingSaga.start(booking.id(), booking.trackingNumber(), new UtcInstant(NOW)));

        assertThat(service.detail(booking.trackingNumber()))
                .contains(new BookingDetail(booking, BookingSagaStatus.IN_PROGRESS));
        assertThat(service.detail(new TrackingNumber("CTZZZZZZZZZZZZ"))).isEmpty();
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

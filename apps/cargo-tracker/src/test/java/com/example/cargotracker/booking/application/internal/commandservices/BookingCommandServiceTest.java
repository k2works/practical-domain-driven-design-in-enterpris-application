package com.example.cargotracker.booking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.quotation.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 本予約の確定の入力ポート（US-04 AC1・AC2。Bolt 23 レビュー L-1・L-3）。見積りの公開 API と追跡番号の発行を差し替えて、
 * 受入テストで通らない分岐（見つからない・置換済み・貨物の要約が空・権限の先行）を確かめる。
 */
class BookingCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00.123456Z");
    private static final CompanyId A_COMPANY = new CompanyId(UUID.randomUUID());

    private final InMemoryBookingRepository repository = new InMemoryBookingRepository();
    private final InMemoryBookingSagaRepository sagaRepository = new InMemoryBookingSagaRepository();
    private final AtomicInteger issued = new AtomicInteger();
    private final List<BookableQuotationRequest> queries = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();
    private BookableQuotationResult quotation = bookable(BookingFixture.terms().cargoSummary());

    private final BookingCommandService service = new BookingCommandService(
            repository,
            sagaRepository,
            () -> {
                issued.incrementAndGet();
                return BookingFixture.TRACKING_NUMBER;
            },
            new QuotationBookability(request -> {
                queries.add(request);
                return quotation;
            }),
            published::add,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 確定すると照会と同じcommit時刻で予約と予約サガを保存しDE07を発行する() {
        BookingConfirmationOutcome outcome = service.confirm(command(Role.SALES, true));

        assertThat(outcome).isEqualTo(new BookingConfirmationOutcome.Confirmed(BookingFixture.TRACKING_NUMBER));
        assertThat(queries).extracting(BookableQuotationRequest::committedAt).containsExactly(new UtcInstant(NOW));
        Booking booking = repository.findAll().getFirst();
        assertThat(booking.currentVersion().committedAt()).isEqualTo(new UtcInstant(NOW));
        assertThat(sagaRepository.findByBookingId(booking.id()).orElseThrow().status())
                .isEqualTo(BookingSagaStatus.IN_PROGRESS);
        assertThat(published).singleElement().isInstanceOf(BookingConfirmed.class);
    }

    @Test
    void 見つからない見積りと置換済みの見積りは理由で返し追跡番号を発行しない() {
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND);
        assertThat(service.confirm(command(Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability.NOT_FOUND));

        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.REPLACED);
        assertThat(service.confirm(command(Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability.REPLACED));

        assertThat(issued).hasValue(0);
        assertThat(repository.findAll()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void 貨物の要約が空なら必須の貨物情報を不足条件で返し追跡番号を発行しない() {
        quotation = bookable(" ");

        assertThat(service.confirm(command(Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.MissingConditions(List.of(BookingCondition.REQUIRED_CARGO)));
        assertThat(issued).hasValue(0);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void 営業担当者でなければ見積りを照会せずに拒否する() {
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirm(command(Role.SHIPPER, false))).isEqualTo(new BookingConfirmationOutcome.Forbidden());
        assertThat(queries).isEmpty();
        assertThat(issued).hasValue(0);
    }

    private static ConfirmBookingCommand command(Role role, boolean staffConfirmed) {
        return new ConfirmBookingCommand(
                BookingFixture.QUOTATION,
                new AuthenticatedActor(new UserId(BookingFixture.SALES), A_COMPANY, Set.of(role), "担当", "A 社"),
                staffConfirmed);
    }

    private static BookableQuotationResult bookable(String cargoSummary) {
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
                cargoSummary,
                terms.shipperApproverId(),
                new UtcInstant(NOW.plusSeconds(3600)));
    }
}

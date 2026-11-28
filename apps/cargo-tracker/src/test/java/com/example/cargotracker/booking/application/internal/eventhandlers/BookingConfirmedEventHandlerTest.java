package com.example.cargotracker.booking.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookingNotifications;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.quotation.interfaces.api.BookingNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.BookingNotificationRequest;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * DE-07 を受けて見積りの公開 API で輸送要求を予約確定済みにする listener（ADR-014。Bolt 23）。業務の理由で進まなかったときは
 * 警告のログを残して終え、例外にしない（予約の確定は戻さない。結果整合。T-58）。
 */
class BookingConfirmedEventHandlerTest {

    private static final BookingConfirmed EVENT = new BookingConfirmed(
            UUID.randomUUID(),
            1,
            "CTABCDEFGH2345",
            UUID.randomUUID(),
            UUID.randomUUID(),
            2,
            "TR-2026-0001",
            "RC-2026-0001",
            1,
            new UtcInstant(Instant.parse("2026-10-08T08:59:00Z")),
            0,
            new CompanyId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()));

    private final List<BookingNotificationRequest> requests = new ArrayList<>();

    @Test
    void 輸送要求と版と見積りと予約を渡して予約確定済みにする() {
        handler(new BookingNotificationReceipt.Booked()).on(EVENT);

        assertThat(requests)
                .containsExactly(new BookingNotificationRequest(
                        EVENT.transportRequestId(), 2, EVENT.quotationId(), EVENT.bookingId()));
    }

    @Test
    void 進まなかったときは例外にせず終える() {
        assertThatCode(() -> handler(new BookingNotificationReceipt.NotBooked(
                                BookingNotificationReceipt.NotBooked.NOT_READY_TO_BOOK))
                        .on(EVENT))
                .doesNotThrowAnyException();
        assertThatCode(() ->
                        handler(new BookingNotificationReceipt.AlreadyBooked()).on(EVENT))
                .doesNotThrowAnyException();
        assertThat(requests).hasSize(2);
    }

    private BookingConfirmedEventHandler handler(BookingNotificationReceipt receipt) {
        return new BookingConfirmedEventHandler(new QuotationBookingNotifications(request -> {
            requests.add(request);
            return receipt;
        }));
    }
}

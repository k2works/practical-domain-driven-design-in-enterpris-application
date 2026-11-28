package com.example.cargotracker.quotation.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commandservices.BookingNotificationService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.quotation.interfaces.api.BookingNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.BookingNotificationRequest;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 見積りの公開 API の予約確定済みの通知（ADR-014、DE-07。Bolt 23）。予約の DE-07 の listener が呼び、輸送要求を予約確定済みにする。
 * 冪等で、業務の理由で進めないときは例外にせず結果で返す（T-58）。
 */
class BookingNotificationAdapterTest {

    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-05T01:00:00Z"));

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final BookingNotificationAdapter service =
            new BookingNotificationAdapter(new BookingNotificationService(repository));

    @Test
    void 予約待ちの輸送要求を予約確定済みにし二度目は何もしない() {
        TransportRequest request = readyToBook();

        assertThat(service.notifyBooked(request(request.id().value(), 1)))
                .isEqualTo(new BookingNotificationReceipt.Booked());
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(TransportRequestStatus.BOOKED);
        assertThat(service.notifyBooked(request(request.id().value(), 1)))
                .isEqualTo(new BookingNotificationReceipt.AlreadyBooked());
    }

    @Test
    void 荷主の承認の配信より先に届いても荷主承認待ちの輸送要求を予約確定済みにする() {
        TransportRequest request = awaitingApproval();

        assertThat(service.notifyBooked(request(request.id().value(), 1)))
                .isEqualTo(new BookingNotificationReceipt.Booked());
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(TransportRequestStatus.BOOKED);
    }

    @Test
    void 見積りの手前の輸送要求と見つからない輸送要求は理由で返す() {
        TransportRequest request = submitted();

        assertThat(service.notifyBooked(request(request.id().value(), 1)))
                .isEqualTo(new BookingNotificationReceipt.NotBooked(
                        BookingNotificationReceipt.NotBooked.NOT_READY_TO_BOOK));
        assertThat(repository.findById(request.id()).orElseThrow().status())
                .isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(service.notifyBooked(request(UUID.randomUUID(), 1)))
                .isEqualTo(new BookingNotificationReceipt.NotBooked(
                        BookingNotificationReceipt.NotBooked.TRANSPORT_REQUEST_NOT_FOUND));
    }

    private static BookingNotificationRequest request(UUID transportRequestId, int versionNo) {
        return new BookingNotificationRequest(transportRequestId, versionNo, UUID.randomUUID(), UUID.randomUUID());
    }

    private TransportRequest submitted() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                NOW);
        repository.save(request);
        return request;
    }

    private TransportRequest awaitingApproval() {
        TransportRequest request = advanceToAwaitingApproval();
        repository.update(request);
        return request;
    }

    private TransportRequest advanceToAwaitingApproval() {
        TransportRequest request = submitted();
        request.approve(1, STAFF, "根拠", NOW);
        request.markQuotationPresented(1);
        request.markRoutingRequested(1);
        request.markAwaitingApproval(1);
        return request;
    }

    private TransportRequest readyToBook() {
        TransportRequest request = advanceToAwaitingApproval();
        request.markReadyToBook(1);
        repository.update(request);
        return request;
    }
}

package com.example.cargotracker.quotation.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.events.QuotationDeclined;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DE-17 荷主が見積りを辞退した を受けて輸送要求を辞退で終了にする（別のトランザクション。冪等。US-24 AC2。W9 の Bolt 48-1）。 */
class QuotationDeclinedEventHandlerTest {

    private static final UtcInstant AT = new UtcInstant(Instant.parse("2026-10-06T02:00:00Z"));

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final QuotationDeclinedEventHandler handler = new QuotationDeclinedEventHandler(repository);

    private TransportRequest quoted() {
        UserId staff = new UserId(UUID.randomUUID());
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                staff,
                AT);
        repository.save(request);
        TransportRequest found = repository.findById(request.id()).orElseThrow();
        found.approve(1, staff, "根拠", AT);
        found.markQuotationPresented(1);
        repository.update(found);
        return found;
    }

    private static QuotationDeclined event(TransportRequest request, int versionNo) {
        return new QuotationDeclined(
                UUID.randomUUID(), 1, request.id().value(), versionNo, UUID.randomUUID(), "他社で進めるため", AT);
    }

    @Test
    void 辞退を受けると輸送要求を辞退で終了にする() {
        TransportRequest request = quoted();

        handler.on(event(request, 1));

        assertThat(repository.findById(request.id()).orElseThrow().status())
                .isEqualTo(TransportRequestStatus.WITHDRAWN);
    }

    @Test
    void 同じ辞退の再配信は何も変えない() {
        TransportRequest request = quoted();
        QuotationDeclined event = event(request, 1);

        handler.on(event);
        handler.on(event);

        assertThat(repository.findById(request.id()).orElseThrow().status())
                .isEqualTo(TransportRequestStatus.WITHDRAWN);
    }

    @Test
    void 版が違う辞退は輸送要求を変えない() {
        TransportRequest request = quoted();

        handler.on(event(request, 2));

        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(TransportRequestStatus.QUOTED);
    }
}

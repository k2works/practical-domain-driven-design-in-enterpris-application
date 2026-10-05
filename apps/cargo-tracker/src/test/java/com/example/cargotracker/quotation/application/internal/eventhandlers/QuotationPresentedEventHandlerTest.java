package com.example.cargotracker.quotation.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DE-03 を受けて輸送要求を見積提示済みにする（別のトランザクション。冪等。2026-10-05 の決定）。 */
class QuotationPresentedEventHandlerTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-05T04:00:00Z"));

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final QuotationPresentedEventHandler handler = new QuotationPresentedEventHandler(repository);

    private TransportRequest quoting() {
        UserId staff = new UserId(UUID.randomUUID());
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                staff,
                NOW);
        repository.save(request);
        TransportRequest found = repository.findById(request.id()).orElseThrow();
        found.approve(1, staff, "根拠", NOW);
        repository.update(found);
        return found;
    }

    private static QuotationPresented presented(TransportRequest request) {
        return new QuotationPresented(UUID.randomUUID(), 1, request.id().value(), 1, NOW, List.of(), NOW, NOW, NOW);
    }

    @Test
    void 見積りの提示を受けると輸送要求を見積提示済みにする() {
        TransportRequest request = quoting();

        handler.on(presented(request));

        assertThat(repository.findById(request.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(TransportRequestStatus.QUOTED));
    }

    @Test
    void 同じイベントが2回届いても輸送要求は変わらない() {
        TransportRequest request = quoting();
        QuotationPresented event = presented(request);
        handler.on(event);
        long versionAfterFirst = repository.findById(request.id()).orElseThrow().aggregateVersion();

        handler.on(event);

        assertThat(repository.findById(request.id()).orElseThrow().aggregateVersion()).isEqualTo(versionAfterFirst);
    }

    @Test
    void ない輸送要求の提示は何もしない() {
        handler.on(new QuotationPresented(UUID.randomUUID(), 1, UUID.randomUUID(), 1, NOW, List.of(), NOW, NOW, NOW));

        assertThat(repository.count()).isZero();
    }
}

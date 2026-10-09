package com.example.cargotracker.quotation.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.queryservices.RouteConditionQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.interfaces.api.RouteConditionView;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 見積りの公開 API の経路条件の照会（Bolt 17）。 */
class RouteConditionQueryAdapterTest {

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final RouteConditionQueryAdapter service =
            new RouteConditionQueryAdapter(new RouteConditionQueryService(repository));

    @Test
    void 輸送要求の現在の版の経路条件と業務番号を返す() {
        TransportRequest request = submitted();

        assertThat(service.find(request.id().value(), 1))
                .contains(new RouteConditionView(
                        "TR-2026-0007",
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        ShipmentTermsFixture.ARRIVAL_DEADLINE,
                        "GENERAL"));
    }

    @Test
    void 現在の版でない版や存在しない輸送要求は空() {
        TransportRequest request = submitted();

        assertThat(service.find(request.id().value(), 2)).isEmpty();
        assertThat(service.find(UUID.randomUUID(), 1)).isEmpty();
    }

    private TransportRequest submitted() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 7),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
        repository.save(request);
        return request;
    }
}

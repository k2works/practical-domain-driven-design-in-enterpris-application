package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.acceptance.InMemoryKpiObservationRepository;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KpiObservationEventHandlerTest {

    private final InMemoryKpiObservationRepository repository = new InMemoryKpiObservationRepository();
    private final KpiObservationEventHandler handler = new KpiObservationEventHandler(repository);

    @Test
    void 同じDE01が2回届いてもKPI計測記録は1件で最初の提出時刻のまま() {
        TransportRequestSubmitted event = new TransportRequestSubmitted(UUID.randomUUID(), 1,
                new CompanyId(UUID.randomUUID()), new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));

        handler.on(event);
        handler.on(event);

        assertThat(repository.findAll()).singleElement()
                .satisfies(observation -> assertThat(observation.submittedAt()).isEqualTo(event.submittedAt()));
    }
}

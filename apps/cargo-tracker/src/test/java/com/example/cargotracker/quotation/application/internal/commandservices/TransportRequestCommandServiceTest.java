package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransportRequestCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T01:00:00Z");

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final List<Object> published = new ArrayList<>();
    private final TransportRequestCommandService service = new TransportRequestCommandService(repository,
            published::add, Clock.fixed(NOW, ZoneOffset.UTC));

    private final SubmitTransportRequestCommand command = new SubmitTransportRequestCommand(
            new CompanyId(UUID.randomUUID()), new UserId(UUID.randomUUID()), new Location("JPTYO"),
            new Location("NLRTM"));

    @Test
    void 提出した輸送要求をClockの時刻で保存する() {
        TransportRequestId id = service.submit(command);

        assertThat(repository.findById(id)).hasValueSatisfying(request ->
                assertThat(request.currentVersion().submittedAt()).isEqualTo(new UtcInstant(NOW)));
    }

    @Test
    void 保存した輸送要求のDE01を1回だけ発行しイベントを残さない() {
        TransportRequestId id = service.submit(command);

        assertThat(published).containsExactly(
                new TransportRequestSubmitted(id.value(), 1, command.shipperCompanyId(), new UtcInstant(NOW)));
        assertThat(repository.findById(id)).hasValueSatisfying(request ->
                assertThat(request.domainEvents()).isEmpty());
    }
}

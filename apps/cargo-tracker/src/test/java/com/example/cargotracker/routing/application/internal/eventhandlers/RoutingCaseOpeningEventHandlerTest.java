package com.example.cargotracker.routing.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.interfaces.api.RouteConditionView;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseNumberIssuer;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteConditions;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DE-16 を受けて経路設計案件を作る（R-INV-10。冪等。US-06。Bolt 17）。 */
class RoutingCaseOpeningEventHandlerTest {

    static final UUID TRANSPORT_REQUEST_ID = UUID.randomUUID();
    static final UUID QUOTATION_ID = UUID.randomUUID();
    static final UtcInstant REQUESTED_AT = new UtcInstant(Instant.parse("2026-10-06T02:00:00Z"));
    static final UUID REQUESTER = UUID.randomUUID();
    static final UtcInstant DEADLINE = new UtcInstant(Instant.parse("2026-11-02T00:00:00Z"));
    static final RouteConditionView VIEW =
            new RouteConditionView("TR-2026-0001", new Location("JPTYO"), new Location("NLRTM"), DEADLINE, "GENERAL");

    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
    private final RoutingCaseOpeningEventHandler handler = new RoutingCaseOpeningEventHandler(
            repository,
            new InMemoryRoutingCaseNumberIssuer(),
            new QuotationRouteConditions((id, versionNo) ->
                    id.equals(TRANSPORT_REQUEST_ID) && versionNo == 1 ? Optional.of(VIEW) : Optional.empty()));

    @Test
    void 依頼を受けると経路条件を見積りから得て案件を作る() {
        handler.on(event(1));

        RoutingCase created =
                repository.findByNumber(new RoutingCaseNumber(2026, 1)).orElseThrow();
        assertThat(created.transportRequestId()).isEqualTo(TRANSPORT_REQUEST_ID);
        assertThat(created.transportRequestNumber()).isEqualTo("TR-2026-0001");
        assertThat(created.transportRequestVersionNo()).isEqualTo(1);
        assertThat(created.quotationId()).isEqualTo(QUOTATION_ID);
        assertThat(created.routePolicyVia()).containsExactly(new Location("SGSIN"));
        assertThat(created.specification())
                .isEqualTo(new RouteSpecification(new Location("JPTYO"), new Location("NLRTM"), DEADLINE, "GENERAL"));
        assertThat(created.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(created.quotationExpiresAt()).contains(new UtcInstant(Instant.parse("2026-10-08T09:00:00Z")));
        assertThat(created.requestedBy()).contains(REQUESTER);
        assertThat(created.routeVersion().status()).isEqualTo(RouteVersionStatus.DRAFT);
    }

    @Test
    void 同じ依頼がもう一度配信されても案件を重複して作らない() {
        handler.on(event(1));
        handler.on(event(1));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void 経路条件が得られなければ案件を作らない() {
        handler.on(event(2));

        assertThat(repository.count()).isZero();
    }

    static RouteDesignRequested event(int versionNo) {
        return new RouteDesignRequested(
                QUOTATION_ID,
                1,
                TRANSPORT_REQUEST_ID,
                versionNo,
                List.of("SGSIN"),
                new UtcInstant(Instant.parse("2026-10-10T00:00:00Z")),
                new UtcInstant(Instant.parse("2026-10-30T00:00:00Z")),
                new UtcInstant(Instant.parse("2026-10-08T09:00:00Z")),
                REQUESTER,
                REQUESTED_AT);
    }
}

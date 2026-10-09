package com.example.cargotracker.quotation.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteAssignmentService;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentLeg;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentRequest;
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

/** 見積りの公開 API の経路の割当て（ADR-014、R-INV-11。Bolt 20）。 */
class RouteAssignmentAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-07T05:01:00Z");
    private static final UserId STAFF = new UserId(UUID.randomUUID());

    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final List<Object> published = new ArrayList<>();
    private final RouteAssignmentAdapter service = new RouteAssignmentAdapter(
            new RouteAssignmentService(quotations, published::add, Clock.fixed(NOW, ZoneOffset.UTC)));

    private Quotation presented() {
        Quotation quotation =
                Quotation.create(new QuotationId(UUID.randomUUID()), new TransportRequestId(UUID.randomUUID()), 1, 1);
        UtcInstant at = new UtcInstant(Instant.parse("2026-10-05T04:00:00Z"));
        quotation.calculate(QuotationFixture.completeInput(), at);
        quotation.presentInternally(STAFF, at);
        quotation.clearDomainEvents();
        return quotation;
    }

    private Quotation routingRequested() {
        Quotation quotation = presented();
        quotation.requestRouteDesign(STAFF, new UtcInstant(Instant.parse("2026-10-06T02:00:00Z")));
        quotation.clearDomainEvents();
        quotations.save(quotation);
        return quotation;
    }

    private static RouteAssignmentRequest request(UUID quotationId, int routeVersionNo, List<RouteAssignmentLeg> legs) {
        return new RouteAssignmentRequest(
                quotationId,
                "RC-2026-0001",
                routeVersionNo,
                new UtcInstant(Instant.parse("2026-10-07T05:00:00Z")),
                legs);
    }

    private static List<RouteAssignmentLeg> legs() {
        return List.of(new RouteAssignmentLeg(
                "V-101",
                new Location("JPTYO"),
                new Location("NLRTM"),
                new UtcInstant(Instant.parse("2099-10-12T00:00:00Z")),
                new UtcInstant(Instant.parse("2099-10-30T09:00:00Z"))));
    }

    @Test
    void 詳細設計依頼済みの見積りに割り当てて保存しDE21を発行しもう一度は何もしない() {
        Quotation quotation = routingRequested();

        assertThat(service.assign(request(quotation.id().value(), 1, legs())))
                .isEqualTo(new RouteAssignmentReceipt.Assigned());
        assertThat(service.assign(request(quotation.id().value(), 1, legs())))
                .isEqualTo(new RouteAssignmentReceipt.AlreadyAssigned());

        assertThat(quotations.findById(quotation.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
            assertThat(found.assignedRoute())
                    .hasValueSatisfying(route -> assertThat(route.legs())
                            .singleElement()
                            .satisfies(leg -> assertThat(leg.voyageNumber()).isEqualTo("V-101")));
        });
        assertThat(published).singleElement().isInstanceOf(QuotationRouteAssigned.class);
    }

    @Test
    void 割り当てなかった理由を公開APIの理由の名前で返す() {
        Quotation presented = presented();
        quotations.save(presented);
        Quotation routingRequested = routingRequested();
        service.assign(request(routingRequested.id().value(), 1, legs()));
        published.clear();

        assertThat(service.assign(request(UUID.randomUUID(), 1, legs())))
                .isEqualTo(
                        new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.QUOTATION_NOT_FOUND));
        assertThat(service.assign(request(presented.id().value(), 1, legs())))
                .isEqualTo(new RouteAssignmentReceipt.NotAssigned(
                        RouteAssignmentReceipt.NotAssigned.NOT_ROUTING_REQUESTED));
        assertThat(service.assign(request(routingRequested.id().value(), 2, legs())))
                .isEqualTo(new RouteAssignmentReceipt.NotAssigned(
                        RouteAssignmentReceipt.NotAssigned.ANOTHER_ROUTE_VERSION_ASSIGNED));
        assertThat(published).isEmpty();
    }

    @Test
    void 区間のない依頼は例外にせず不正な依頼として返す() {
        Quotation quotation = routingRequested();

        assertThat(service.assign(request(quotation.id().value(), 1, List.of())))
                .isEqualTo(new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.INVALID_REQUEST));
        assertThat(quotations.findById(quotation.id()).orElseThrow().status())
                .isEqualTo(QuotationStatus.ROUTING_REQUESTED);
    }
}

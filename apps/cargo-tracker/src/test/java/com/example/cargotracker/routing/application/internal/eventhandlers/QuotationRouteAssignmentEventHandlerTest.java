package com.example.cargotracker.routing.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteAssignmentRequest;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteAssignments;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** DE-05 を受けて見積りの公開 API で経路版を割り当てる（R-INV-11、ADR-014。Bolt 20）。 */
class QuotationRouteAssignmentEventHandlerTest {

    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
    private final List<RouteAssignmentRequest> requests = new ArrayList<>();
    private RouteAssignmentReceipt receipt = new RouteAssignmentReceipt.Assigned();
    private final QuotationRouteAssignmentEventHandler handler =
            new QuotationRouteAssignmentEventHandler(repository, new QuotationRouteAssignments(request -> {
                requests.add(request);
                return receipt;
            }));
    private final Logger logger = (Logger) LoggerFactory.getLogger(QuotationRouteAssignmentEventHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void 確定した候補の区間と案件番号と経路版と確定の時刻を見積りに渡す() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());

        handler.on(confirmed.event());

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.quotationId()).isEqualTo(confirmed.routingCase().quotationId());
            assertThat(request.routingCaseNumber()).isEqualTo("RC-2026-0001");
            assertThat(request.routeVersionNo()).isEqualTo(1);
            assertThat(request.confirmedAt()).isEqualTo(confirmed.event().approvedAt());
            assertThat(request.legs()).singleElement().satisfies(leg -> {
                assertThat(leg.voyageNumber()).isEqualTo("V-EARLY");
                assertThat(leg.load().unLocode()).isEqualTo("JPTYO");
                assertThat(leg.discharge().unLocode()).isEqualTo("NLRTM");
            });
        });
        assertThat(logs.list).isEmpty();
    }

    @Test
    void すでに同じ経路版を割り当てていればログを残さない() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());
        receipt = new RouteAssignmentReceipt.AlreadyAssigned();

        handler.on(confirmed.event());

        assertThat(logs.list).isEmpty();
    }

    @Test
    void 見積りが割り当てなかったときは理由を警告のログに残す() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());
        receipt = new RouteAssignmentReceipt.NotAssigned(RouteAssignmentReceipt.NotAssigned.RETIRED);

        handler.on(confirmed.event());

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("RC-2026-0001").contains("RETIRED");
        });
    }

    @Test
    void 案件か確定した候補が見つからなければ見積りを呼ばず警告のログに残す() {
        RouteConfirmed event = RoutingCaseFixture.confirmed().event();

        handler.on(event);

        assertThat(requests).isEmpty();
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage())
                    .contains("RC-2026-0001")
                    .contains(event.quotationId().toString());
        });
    }

    @Test
    void 別の経路版のDE05では確定した候補が見つからない() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());
        RouteConfirmed event = confirmed.event();
        RouteConfirmed another = new RouteConfirmed(
                event.routingCaseId(),
                event.caseNumber(),
                2,
                event.quotationId(),
                event.transportRequestId(),
                event.transportRequestVersionNo(),
                UUID.randomUUID(),
                event.approvedAt(),
                event.referencedInfoVersions());

        handler.on(another);

        assertThat(requests).isEmpty();
        assertThat(logs.list).hasSize(1);
    }
}

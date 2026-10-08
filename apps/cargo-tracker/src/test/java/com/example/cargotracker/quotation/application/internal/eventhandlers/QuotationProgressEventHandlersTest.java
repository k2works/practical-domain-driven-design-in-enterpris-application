package com.example.cargotracker.quotation.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** DE-21 で輸送要求を荷主承認待ちに、DE-04 で予約待ちにする（別のトランザクション。冪等。US-24 AC4。Bolt 20）。 */
class QuotationProgressEventHandlersTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-07T05:00:00Z"));

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final QuotationRouteAssignedEventHandler assignedHandler =
            new QuotationRouteAssignedEventHandler(repository);
    private final QuotationApprovedByShipperEventHandler approvedHandler =
            new QuotationApprovedByShipperEventHandler(repository);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger assignedLogger = (Logger) LoggerFactory.getLogger(QuotationRouteAssignedEventHandler.class);
    private final Logger approvedLogger =
            (Logger) LoggerFactory.getLogger(QuotationApprovedByShipperEventHandler.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        assignedLogger.addAppender(logs);
        approvedLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        assignedLogger.detachAppender(logs);
        approvedLogger.detachAppender(logs);
    }

    private TransportRequest routing() {
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
        found.markRoutingRequested(1);
        repository.update(found);
        return repository.findById(request.id()).orElseThrow();
    }

    private static QuotationRouteAssigned assigned(UUID transportRequestId, int versionNo) {
        return new QuotationRouteAssigned(UUID.randomUUID(), 1, transportRequestId, versionNo, "RC-2026-0001", 1, NOW);
    }

    private static QuotationApprovedByShipper approved(UUID transportRequestId, int versionNo) {
        return new QuotationApprovedByShipper(
                UUID.randomUUID(), 1, transportRequestId, versionNo, "RC-2026-0001", 1, UUID.randomUUID(), NOW);
    }

    private TransportRequestStatus statusOf(TransportRequest request) {
        return repository.findById(request.id()).orElseThrow().status();
    }

    @Test
    void 割当てを受けると荷主承認待ちに承認を受けると予約待ちにする() {
        TransportRequest request = routing();

        assignedHandler.on(assigned(request.id().value(), 1));
        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);
        approvedHandler.on(approved(request.id().value(), 1));

        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 再配信と承認の後に遅れて届いた割当てでは輸送要求を変えずログも残さない() {
        TransportRequest request = routing();
        assignedHandler.on(assigned(request.id().value(), 1));
        approvedHandler.on(approved(request.id().value(), 1));
        long version = repository.findById(request.id()).orElseThrow().aggregateVersion();

        approvedHandler.on(approved(request.id().value(), 1));
        assignedHandler.on(assigned(request.id().value(), 1));

        assertThat(repository.findById(request.id()).orElseThrow().aggregateVersion())
                .isEqualTo(version);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 版が食い違ったときと輸送要求がないときは警告のログに残す() {
        TransportRequest request = routing();
        UUID missing = UUID.randomUUID();

        assignedHandler.on(assigned(request.id().value(), 2));
        approvedHandler.on(approved(missing, 1));

        assertThat(statusOf(request)).isEqualTo(TransportRequestStatus.ROUTING);
        assertThat(logs.list)
                .hasSize(2)
                .allSatisfy(log -> assertThat(log.getLevel()).isEqualTo(Level.WARN));
        assertThat(logs.list.get(0).getFormattedMessage()).contains("版 2").contains("ROUTING");
        assertThat(logs.list.get(1).getFormattedMessage()).contains(missing.toString());
    }
}

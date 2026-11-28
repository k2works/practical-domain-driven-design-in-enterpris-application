package com.example.cargotracker.quotation.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** DE-16 を受けて輸送要求を経路設計中にする（別のトランザクション。冪等。US-24 AC1。Bolt 12）。 */
class RouteDesignRequestedEventHandlerTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-06T02:00:00Z"));

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final RouteDesignRequestedEventHandler handler = new RouteDesignRequestedEventHandler(repository);
    private final Logger logger = (Logger) LoggerFactory.getLogger(RouteDesignRequestedEventHandler.class);
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

    private TransportRequest quoted() {
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
        found.markQuotationPresented(1);
        repository.update(found);
        return repository.findById(request.id()).orElseThrow();
    }

    private static RouteDesignRequested requested(UUID transportRequestId, int versionNo) {
        return new RouteDesignRequested(
                UUID.randomUUID(), 1, transportRequestId, versionNo, List.of(), NOW, NOW, NOW, UUID.randomUUID(), NOW);
    }

    @Test
    void 詳細経路設計の依頼を受けると輸送要求を経路設計中にする() {
        TransportRequest request = quoted();

        handler.on(requested(request.id().value(), 1));

        assertThat(repository.findById(request.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(TransportRequestStatus.ROUTING));
    }

    @Test
    void 再配信では輸送要求を変えずログも残さない() {
        TransportRequest request = quoted();
        RouteDesignRequested event = requested(request.id().value(), 1);
        handler.on(event);
        long versionAfterFirst = repository.findById(request.id()).orElseThrow().aggregateVersion();

        handler.on(event);

        assertThat(repository.findById(request.id()).orElseThrow().aggregateVersion())
                .isEqualTo(versionAfterFirst);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 版が食い違って状態を変えなかったときは輸送要求と版をログに残す() {
        TransportRequest request = quoted();

        handler.on(requested(request.id().value(), 2));

        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(TransportRequestStatus.QUOTED);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains(request.id().value().toString())
                    .contains("版 2")
                    .contains("現在の版 1")
                    .contains("QUOTED");
        });
    }

    @Test
    void ない輸送要求の依頼はログに残して何もしない() {
        UUID missing = UUID.randomUUID();

        handler.on(requested(missing, 1));

        assertThat(repository.count()).isZero();
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(missing.toString());
        });
    }

    @Test
    void 荷主承認待ちや予約待ちに進んだ後に遅れて届いた依頼は輸送要求を変えずログも残さない() {
        TransportRequest request = quoted();
        TransportRequest found = repository.findById(request.id()).orElseThrow();
        found.markRoutingRequested(1);
        found.markAwaitingApproval(1);
        repository.update(found);

        handler.on(requested(request.id().value(), 1));
        TransportRequest ready = repository.findById(request.id()).orElseThrow();
        ready.markReadyToBook(1);
        repository.update(ready);
        handler.on(requested(request.id().value(), 1));

        assertThat(repository.findById(request.id()).orElseThrow().status())
                .isEqualTo(TransportRequestStatus.READY_TO_BOOK);
        assertThat(logs.list).isEmpty();
    }
}

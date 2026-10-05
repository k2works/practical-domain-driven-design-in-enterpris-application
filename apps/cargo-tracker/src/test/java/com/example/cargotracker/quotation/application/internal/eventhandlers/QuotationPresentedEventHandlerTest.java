package com.example.cargotracker.quotation.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** DE-03 を受けて輸送要求を見積提示済みにする（別のトランザクション。冪等。2026-10-05 の決定）。 */
class QuotationPresentedEventHandlerTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-05T04:00:00Z"));

    private final InMemoryTransportRequestRepository repository = new InMemoryTransportRequestRepository();
    private final QuotationPresentedEventHandler handler = new QuotationPresentedEventHandler(repository);
    private final Logger logger = (Logger) LoggerFactory.getLogger(QuotationPresentedEventHandler.class);
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

        assertThat(repository.findById(request.id()).orElseThrow().aggregateVersion())
                .isEqualTo(versionAfterFirst);
    }

    @Test
    void ない輸送要求の提示は何もしない() {
        handler.on(new QuotationPresented(UUID.randomUUID(), 1, UUID.randomUUID(), 1, NOW, List.of(), NOW, NOW, NOW));

        assertThat(repository.count()).isZero();
    }

    @Test
    void 版が食い違って状態を変えなかったときは輸送要求と版をログに残す() {
        TransportRequest request = quoting();
        QuotationPresented staleVersion =
                new QuotationPresented(UUID.randomUUID(), 1, request.id().value(), 2, NOW, List.of(), NOW, NOW, NOW);

        handler.on(staleVersion);

        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(TransportRequestStatus.QUOTING);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains(request.id().value().toString())
                    .contains("版 2")
                    .contains("現在の版 1")
                    .contains("QUOTING");
        });
    }

    @Test
    void 再配信で見積提示済みのときはログを残さない() {
        TransportRequest request = quoting();
        QuotationPresented event = presented(request);
        handler.on(event);

        handler.on(event);

        assertThat(logs.list).isEmpty();
    }

    @Test
    void ない輸送要求の提示はログに残す() {
        UUID missing = UUID.randomUUID();

        handler.on(new QuotationPresented(UUID.randomUUID(), 1, missing, 1, NOW, List.of(), NOW, NOW, NOW));

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(missing.toString());
        });
    }
}

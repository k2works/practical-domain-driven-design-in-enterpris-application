package com.example.cargotracker.identity.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.identity.acceptance.InMemoryKpiObservationRepository;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class KpiObservationEventHandlerTest {

    private final InMemoryKpiObservationRepository repository = new InMemoryKpiObservationRepository();
    private final KpiObservationEventHandler handler = new KpiObservationEventHandler(repository);
    private final Logger logger = (Logger) LoggerFactory.getLogger(KpiObservationEventHandler.class);
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

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private UUID submitted(String submittedAt) {
        UUID transportRequestId = UUID.randomUUID();
        handler.on(new TransportRequestSubmitted(
                transportRequestId, 1, new CompanyId(UUID.randomUUID()), at(submittedAt), "TR-2026-0001"));
        return transportRequestId;
    }

    private static QuotationPresented presented(UUID transportRequestId, String presentedAt) {
        UtcInstant instant = at(presentedAt);
        return new QuotationPresented(
                UUID.randomUUID(), 1, transportRequestId, 1, instant, List.of(), instant, instant, instant);
    }

    @Test
    void DE03を受けると最初の提示時刻を記録する() {
        UUID transportRequestId = submitted("2026-10-05T01:00:00Z");

        handler.on(presented(transportRequestId, "2026-10-05T04:30:00Z"));

        assertThat(repository.findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(observation ->
                        assertThat(observation.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z")));
    }

    @Test
    void 後から遅いDE03が届いても最初の提示時刻は変わらない() {
        UUID transportRequestId = submitted("2026-10-05T01:00:00Z");
        handler.on(presented(transportRequestId, "2026-10-05T04:30:00Z"));

        handler.on(presented(transportRequestId, "2026-10-06T01:00:00Z"));

        assertThat(repository.findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(observation ->
                        assertThat(observation.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z")));
    }

    @Test
    void 届く順が入れ替わり早いDE03が後から届いたら書き換える() {
        UUID transportRequestId = submitted("2026-10-05T01:00:00Z");
        handler.on(presented(transportRequestId, "2026-10-06T01:00:00Z"));

        handler.on(presented(transportRequestId, "2026-10-05T04:30:00Z"));

        assertThat(repository.findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(observation ->
                        assertThat(observation.firstPresentedAt()).hasValue(at("2026-10-05T04:30:00Z")));
    }

    @Test
    void 提出の記録がないDE03は警告のログを残して例外を投げ再配信を待つ() {
        UUID transportRequestId = UUID.randomUUID();

        assertThatThrownBy(() -> handler.on(presented(transportRequestId, "2026-10-05T04:30:00Z")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.findByTransportRequestId(transportRequestId)).isEmpty();
        assertThat(logs.list).singleElement().satisfies(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage()).contains(transportRequestId.toString());
        });
    }

    @Test
    void 提出時刻より前の提示時刻のDE03は警告のログを残して例外を投げ記録を変えない() {
        UUID transportRequestId = submitted("2026-10-05T01:00:00Z");

        assertThatThrownBy(() -> handler.on(presented(transportRequestId, "2026-10-05T00:59:59Z")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.findByTransportRequestId(transportRequestId))
                .hasValueSatisfying(observation ->
                        assertThat(observation.firstPresentedAt()).isEmpty());
        assertThat(logs.list)
                .singleElement()
                .satisfies(log -> assertThat(log.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void 同じDE01が2回届いてもKPI計測記録は1件で最初の提出時刻のまま() {
        TransportRequestSubmitted event = new TransportRequestSubmitted(
                UUID.randomUUID(),
                1,
                new CompanyId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")),
                "TR-2026-0001");

        handler.on(event);
        handler.on(event);

        assertThat(repository.findAll())
                .singleElement()
                .satisfies(observation -> assertThat(observation.submittedAt()).isEqualTo(event.submittedAt()));
    }

    @Test
    void DE01の業務番号をKPI計測記録に写す() {
        TransportRequestSubmitted event = new TransportRequestSubmitted(
                UUID.randomUUID(),
                1,
                new CompanyId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")),
                "TR-2026-0001");

        handler.on(event);

        assertThat(repository.findAll())
                .singleElement()
                .satisfies(observation ->
                        assertThat(observation.transportRequestNumber()).isEqualTo("TR-2026-0001"));
    }

    @Test
    void 業務番号を持たない古いDE01でもKPI計測記録を残す() {
        TransportRequestSubmitted event = new TransportRequestSubmitted(
                UUID.randomUUID(),
                1,
                new CompanyId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")),
                null);

        handler.on(event);

        assertThat(repository.findAll())
                .singleElement()
                .satisfies(observation ->
                        assertThat(observation.transportRequestNumber()).isNull());
    }
}

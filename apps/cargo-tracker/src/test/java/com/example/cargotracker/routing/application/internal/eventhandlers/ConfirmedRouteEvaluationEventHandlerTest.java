package com.example.cargotracker.routing.application.internal.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.routing.acceptance.InMemoryConnectionRuleRepository;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.acceptance.InMemoryVoyageRepository;
import com.example.cargotracker.routing.application.internal.commands.EvaluateConfirmedRouteCommand;
import com.example.cargotracker.routing.application.internal.commandservices.ConfirmedRouteEvaluationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseScope;
import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.routing.domain.events.VoyageUpdated;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** DE-30 を受けて確定した経路を評価する listener（Bolt 39f の行 14〜18。単体。行 17b・26 は統合テスト）。 */
class ConfirmedRouteEvaluationEventHandlerTest {

    private static final VoyageUpdated EVENT = new VoyageUpdated("V-EARLY", "V-EARLY@2", at("2026-10-07T05:00:00Z"));
    private static final RoutingCaseNumber FIRST = new RoutingCaseNumber(2026, 1);
    private static final RoutingCaseNumber SECOND = new RoutingCaseNumber(2026, 2);
    private static final EvaluateConfirmedRouteCommand EVALUATE_FIRST = new EvaluateConfirmedRouteCommand(FIRST);
    private static final EvaluateConfirmedRouteCommand EVALUATE_SECOND = new EvaluateConfirmedRouteCommand(SECOND);

    private final RoutingCaseCommandService service = mock(RoutingCaseCommandService.class);
    private final List<String> queriedVoyages = new ArrayList<>();
    private final List<RoutingCaseNumber> confirmedOnVoyage = new ArrayList<>();
    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository() {
        @Override
        public List<RoutingCaseNumber> findCaseNumbersConfirmedOnVoyage(String voyageNumber) {
            queriedVoyages.add(voyageNumber);
            return List.copyOf(confirmedOnVoyage);
        }
    };
    private final ConfirmedRouteEvaluationEventHandler handler =
            new ConfirmedRouteEvaluationEventHandler(repository, service);

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }

    @Nested
    class Bolt39f_評価のlistener {

        @Test
        void 行14_確定の案件がなければ評価を呼ばない() {
            handler.on(EVENT);

            assertThat(queriedVoyages).containsExactly("V-EARLY");
            verifyNoInteractions(service);
        }

        @Test
        void 行15_確定の案件が2件なら2件とも評価する() {
            confirmedOnVoyage.addAll(List.of(FIRST, SECOND));
            when(service.evaluateConfirmedRoute(any())).thenReturn(new ConfirmedRouteEvaluationOutcome.Unchanged());

            handler.on(EVENT);

            verify(service).evaluateConfirmedRoute(EVALUATE_FIRST);
            verify(service).evaluateConfirmedRoute(EVALUATE_SECOND);
        }

        @Test
        void 行17_競合が2回でも3回目に終われば例外にしない() {
            confirmedOnVoyage.add(FIRST);
            when(service.evaluateConfirmedRoute(EVALUATE_FIRST))
                    .thenReturn(new ConfirmedRouteEvaluationOutcome.Conflict())
                    .thenReturn(new ConfirmedRouteEvaluationOutcome.Conflict())
                    .thenReturn(new ConfirmedRouteEvaluationOutcome.Unchanged());

            handler.on(EVENT);

            verify(service, times(3)).evaluateConfirmedRoute(EVALUATE_FIRST);
        }

        @Test
        void 行17_競合が3回続けば例外を上げて発行記録に残す() {
            confirmedOnVoyage.add(FIRST);
            when(service.evaluateConfirmedRoute(EVALUATE_FIRST))
                    .thenReturn(new ConfirmedRouteEvaluationOutcome.Conflict());

            assertThatThrownBy(() -> handler.on(EVENT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(FIRST.text());

            verify(service, times(3)).evaluateConfirmedRoute(EVALUATE_FIRST);
        }
    }

    @Nested
    class Bolt39f_評価のlistenerのログ {

        private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
        private final Logger logger = (Logger) LoggerFactory.getLogger(ConfirmedRouteEvaluationEventHandler.class);

        @BeforeEach
        void attachLogs() {
            logs.start();
            logger.addAppender(logs);
        }

        @AfterEach
        void detachLogs() {
            logger.detachAppender(logs);
        }

        @Test
        void 行18_照会の後に案件がなければWARNのログを残して次の案件へ進む() {
            confirmedOnVoyage.addAll(List.of(FIRST, SECOND));
            when(service.evaluateConfirmedRoute(EVALUATE_FIRST))
                    .thenReturn(new ConfirmedRouteEvaluationOutcome.NotFound());
            when(service.evaluateConfirmedRoute(EVALUATE_SECOND))
                    .thenReturn(new ConfirmedRouteEvaluationOutcome.Unchanged());

            handler.on(EVENT);

            verify(service).evaluateConfirmedRoute(EVALUATE_SECOND);
            assertThat(logs.list).singleElement().satisfies(log -> {
                assertThat(log.getLevel()).isEqualTo(Level.WARN);
                assertThat(log.getFormattedMessage()).contains(FIRST.text());
            });
        }
    }

    /** 本物の入力ポートで、読み込んだ直後に別の評価が再設計要にした順を作る（行 16）。 */
    @Nested
    class Bolt39f_競合の読み直し {

        private static final Instant EVALUATED_AT = Instant.parse("2026-10-07T06:00:00Z");

        private final List<Object> published = new ArrayList<>();
        private final InMemoryVoyageRepository voyages = new InMemoryVoyageRepository();
        private final InMemoryConnectionRuleRepository rules = new InMemoryConnectionRuleRepository();
        private RoutingCaseCommandService realService;

        private RoutingCaseCommandService serviceOn(InMemoryRoutingCaseRepository repository) {
            return new RoutingCaseCommandService(
                    repository,
                    voyages,
                    rules,
                    published::add,
                    Clock.fixed(EVALUATED_AT, ZoneOffset.UTC),
                    new InfoFreshnessPolicy(Duration.ofHours(24)),
                    new RoutingCaseScope(repository));
        }

        @Test
        void 行16_競合を1回受けたら読み直して評価し直し先の評価が再設計要ならDE06は重ならない() {
            RoutingCase confirmed = RoutingCaseFixture.confirmed().routingCase();
            rules.add(new ConnectionRule(
                    UUID.randomUUID(), new Location("SGSIN"), Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null));
            voyages.add(new Voyage(
                    "V-EARLY",
                    List.of(
                            new PortCall(new Location("JPTYO"), null, at("2026-10-08T00:00:00Z")),
                            new PortCall(new Location("NLRTM"), at("2026-11-05T00:00:00Z"), null)),
                    "V-EARLY@2",
                    at("2026-10-07T05:00:00Z")));
            InMemoryRoutingCaseRepository racing = new InMemoryRoutingCaseRepository() {
                private boolean raced;

                @Override
                public Optional<RoutingCase> findByNumber(RoutingCaseNumber number) {
                    Optional<RoutingCase> loaded = super.findByNumber(number);
                    if (!raced) {
                        raced = true;
                        realService.evaluateConfirmedRoute(new EvaluateConfirmedRouteCommand(number));
                    }
                    return loaded;
                }
            };
            racing.save(confirmed);
            realService = serviceOn(racing);

            new ConfirmedRouteEvaluationEventHandler(racing, realService).on(EVENT);

            RoutingCase saved = racing.findByNumber(confirmed.number()).orElseThrow();
            assertThat(saved.routeVersion().status()).isEqualTo(RouteVersionStatus.REDESIGN_REQUIRED);
            assertThat(saved.aggregateVersion()).isEqualTo(confirmed.aggregateVersion() + 1);
            assertThat(published).singleElement().isInstanceOf(RouteRedesignRequired.class);
        }
    }
}

package com.example.cargotracker.routing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated.AdoptedPortCall;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.eventhandlers.ConfirmedRouteEvaluationEventHandler;
import com.example.cargotracker.routing.application.internal.eventhandlers.VoyageUpdateEventHandler;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseScope;
import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.routing.domain.events.VoyageUpdated;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 同じ航海の DE-12 の同時（Bolt 39e の行 19・20）と、読みと書きの同時（行 45）を PostgreSQL 18 で確かめる。本体は H2 のテストと共有する。
 *
 * <p>クラスに {@code @Transactional} を付けない（別のトランザクションで確かめる。前例 {@code MyBatisConcurrentAdoptionIntegrationTest}）。コミットした
 * 航海は finally で消し、港は JPTYO → USLAX にする（共有の DB のほかのテストの候補につながらない）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisVoyageConcurrentUpdateIntegrationTest {

    private static final Instant ACQUIRED = Instant.parse("2026-11-09T00:00:00Z");
    private static final UtcInstant DEPARTURE = new UtcInstant(Instant.parse("2097-12-01T00:00:00Z"));
    private static final UtcInstant ARRIVAL = new UtcInstant(Instant.parse("2097-12-20T00:00:00Z"));
    private static final UtcInstant OLDER_ARRIVAL = new UtcInstant(Instant.parse("2097-12-18T00:00:00Z"));
    private static final UtcInstant NEWER_ARRIVAL = new UtcInstant(Instant.parse("2097-12-22T00:00:00Z"));
    private static final UtcInstant OLDER_PUBLISHED = new UtcInstant(ACQUIRED.plusSeconds(3600));
    private static final UtcInstant NEWER_PUBLISHED = new UtcInstant(ACQUIRED.plusSeconds(7200));
    private static final Location TOKYO = new Location("JPTYO");
    private static final Location LOS_ANGELES = new Location("USLAX");
    private static final Duration HOLD_WHILE_BLOCKED = Duration.ofMillis(300);
    /** DE-30 の評価まで動かさないテストの発行先（Bolt 39f の前の行）。 */
    private static final ApplicationEventPublisher NO_EVENTS = event -> {};

    private static final AtomicInteger CASE_SEQUENCE = new AtomicInteger();
    private static final Location TRANSSHIPMENT_PORT = new Location("AEJEA");

    @Autowired
    VoyageRepository voyages;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RoutingCaseRepository routingCases;

    @Autowired
    ConnectionRuleRepository connectionRules;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    Clock clock;

    @Autowired
    InfoFreshnessPolicy freshness;

    @Autowired
    RoutingCaseScope scope;

    /** 確定した経路の評価の同時（Bolt 39f の行 25・26）。本体は H2 のテストと共有する。 */
    @Nested
    class Bolt39f_同時 {

        private Evaluations evaluations() {
            return new Evaluations(
                    new Repositories(voyages, jdbc, transactionManager),
                    routingCases,
                    connectionRules,
                    events,
                    clock,
                    freshness,
                    scope);
        }

        @Test
        void 行25_別々の航海の2つのDE12のトランザクションが重なっても両方のコミットの後の評価で接続不足になる() throws Exception {
            assertBothVoyageUpdatesAreEvaluated(evaluations());
        }

        @Test
        void 行26_2つのDE30の評価が同じ案件で重なると遅れた方は読み直してDE06は1件() throws Exception {
            assertOverlappingEvaluationsRedesignOnce(evaluations());
        }
    }

    /** 確定した経路の評価の同時の入力（H2 と PostgreSQL のテストが共有する。Bolt 39f）。 */
    record Evaluations(
            Repositories repositories,
            RoutingCaseRepository cases,
            ConnectionRuleRepository rules,
            ApplicationEventPublisher events,
            Clock clock,
            InfoFreshnessPolicy freshness,
            RoutingCaseScope scope) {}

    /** 書いた後、相手も書き終えるまでコミットに進まないリポジトリ（2 つのトランザクションを重ねる。行 25）。 */
    private static final class WaitingForBothWrites extends DelegatingVoyageRepository {
        private final CyclicBarrier bothWrote;

        WaitingForBothWrites(VoyageRepository delegate, CyclicBarrier bothWrote) {
            super(delegate);
            this.bothWrote = bothWrote;
        }

        @Override
        public void update(Voyage voyage) {
            delegate.update(voyage);
            try {
                bothWrote.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("相手の DE-12 を待ちきれない", e);
            }
        }
    }

    private static Voyage adoptedVoyage(String number, List<PortCall> calls) {
        return Voyage.adoptedFrom(new VoyageAdoption(
                number,
                calls,
                "1",
                new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), new UtcInstant(ACQUIRED)),
                new UtcInstant(ACQUIRED)));
    }

    private static VoyageAdoptedValueUpdated adoptedEvent(String number, List<AdoptedPortCall> calls) {
        return new VoyageAdoptedValueUpdated(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), new UtcInstant(ACQUIRED)),
                number,
                calls,
                "1",
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                NEWER_PUBLISHED);
    }

    /** 候補 1 を確定した案件（希望到着期限は 2098-01-01）。 */
    private static RoutingCase confirmedCase(Evaluations evaluations, List<Voyage> routeVoyages) {
        Location origin = routeVoyages.getFirst().portCalls().getFirst().port();
        Location destination = routeVoyages.getLast().portCalls().getLast().port();
        RoutingCase opened = RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                new RoutingCaseNumber(2094, CASE_SEQUENCE.incrementAndGet()),
                UUID.randomUUID(),
                "TR-2094-0001",
                1,
                UUID.randomUUID(),
                List.of(),
                new RouteSpecification(
                        origin, destination, new UtcInstant(Instant.parse("2098-01-01T00:00:00Z")), "GENERAL"),
                new UtcInstant(ACQUIRED),
                new UtcInstant(Instant.parse("2097-11-01T00:00:00Z")),
                UUID.randomUUID(),
                new CompanyId(UUID.randomUUID()));
        ConstraintEvaluator evaluator = new ConstraintEvaluator();
        UtcInstant now = new UtcInstant(evaluations.clock().instant());
        TransactionTemplate transaction = evaluations.repositories().transaction();
        transaction.executeWithoutResult(status -> evaluations.cases().save(opened));
        transaction.executeWithoutResult(status -> {
            RoutingCase loaded =
                    evaluations.cases().findByNumber(opened.number()).orElseThrow();
            loaded.calculateCandidates(
                    routeVoyages,
                    evaluations.rules().findAll(),
                    now,
                    new RouteCandidateFinder(),
                    evaluator,
                    evaluations.freshness());
            evaluations.cases().update(loaded, UUID.randomUUID());
        });
        transaction.executeWithoutResult(status -> {
            RoutingCase loaded =
                    evaluations.cases().findByNumber(opened.number()).orElseThrow();
            loaded.confirm(
                    1,
                    "同時の確かめの根拠。",
                    new RouteApprover(UUID.randomUUID(), true),
                    routeVoyages,
                    evaluations.rules().findAll(),
                    now,
                    evaluator,
                    evaluations.freshness());
            evaluations.cases().update(loaded, UUID.randomUUID());
        });
        return evaluations.cases().findByNumber(opened.number()).orElseThrow();
    }

    private static Map<String, Object> routeVersionOf(Evaluations evaluations, RoutingCase routingCase) {
        return evaluations
                .repositories()
                .jdbc()
                .queryForMap(
                        "SELECT status, redesign_reasons FROM routing.route_version WHERE routing_case_id = ?",
                        routingCase.id().value());
    }

    private static int redesignPublications(Evaluations evaluations, RoutingCase routingCase) {
        Integer count = evaluations
                .repositories()
                .jdbc()
                .queryForObject(
                        "SELECT COUNT(*) FROM platform.event_publication WHERE event_type LIKE '%RouteRedesignRequired'"
                                + " AND listener_id LIKE '%QuotationRouteRedesignEventHandler%' AND serialized_event LIKE ?",
                        Integer.class, "%" + routingCase.number().text() + "%");
        return count == null ? 0 : count;
    }

    private static int completedEvaluations(Evaluations evaluations, String voyageNumber) {
        Integer count = evaluations
                .repositories()
                .jdbc()
                .queryForObject(
                        "SELECT COUNT(*) FROM platform.event_publication WHERE listener_id LIKE '%ConfirmedRouteEvaluationEventHandler%'"
                                + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                        Integer.class, "%" + voyageNumber + "%");
        return count == null ? 0 : count;
    }

    /**
     * 別々の航海 V（到着が遅れる）と W（出発が早まる）の DE-12 のトランザクションを、両方が書いた後にコミットさせる。片方だけなら接続 14 時間で適合、両方で接続 4 時間（規則 8 時間）。
     * 後にコミットした側の DE-30 の評価が両方の値を見て再設計要にする（1 段に戻す変異 V9 は、どちらのトランザクションも相手の値を読めないので落ちる）。
     */
    static void assertBothVoyageUpdatesAreEvaluated(Evaluations evaluations) throws Exception {
        Repositories repositories = evaluations.repositories();
        String feeder = "C-" + UUID.randomUUID().toString().substring(0, 12);
        String mainline = "C-" + UUID.randomUUID().toString().substring(0, 12);
        UUID ruleId = UUID.randomUUID();
        UtcInstant arrival = new UtcInstant(Instant.parse("2097-12-10T00:00:00Z"));
        UtcInstant lateArrival = new UtcInstant(Instant.parse("2097-12-10T10:00:00Z"));
        UtcInstant connecting = new UtcInstant(Instant.parse("2097-12-11T00:00:00Z"));
        UtcInstant earlyConnecting = new UtcInstant(Instant.parse("2097-12-10T14:00:00Z"));
        UtcInstant finalArrival = new UtcInstant(Instant.parse("2097-12-20T00:00:00Z"));
        try {
            repositories
                    .jdbc()
                    .update(
                            "INSERT INTO routing.connection_rule (id, route_scope, port_unlocode, min_connection_minutes, valid_from,"
                                    + " valid_to, version) VALUES (?, '*', ?, 480, ?, NULL, 0)",
                            ruleId,
                            TRANSSHIPMENT_PORT.unLocode(),
                            OffsetDateTime.parse("2026-01-01T00:00:00Z"));
            Voyage feederVoyage = adoptedVoyage(
                    feeder,
                    List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(TRANSSHIPMENT_PORT, arrival, null)));
            Voyage mainlineVoyage = adoptedVoyage(
                    mainline,
                    List.of(
                            new PortCall(TRANSSHIPMENT_PORT, null, connecting),
                            new PortCall(LOS_ANGELES, finalArrival, null)));
            repositories.transaction().executeWithoutResult(status -> {
                repositories.voyages().save(feederVoyage);
                repositories.voyages().save(mainlineVoyage);
            });
            RoutingCase confirmed = confirmedCase(evaluations, List.of(feederVoyage, mainlineVoyage));
            CyclicBarrier bothWrote = new CyclicBarrier(2);
            VoyageUpdateEventHandler feederHandler = new VoyageUpdateEventHandler(
                    new WaitingForBothWrites(repositories.voyages(), bothWrote), evaluations.events());
            VoyageUpdateEventHandler mainlineHandler = new VoyageUpdateEventHandler(
                    new WaitingForBothWrites(repositories.voyages(), bothWrote), evaluations.events());
            VoyageAdoptedValueUpdated feederUpdate = adoptedEvent(
                    feeder,
                    List.of(
                            new AdoptedPortCall(TOKYO, null, DEPARTURE),
                            new AdoptedPortCall(TRANSSHIPMENT_PORT, lateArrival, null)));
            VoyageAdoptedValueUpdated mainlineUpdate = adoptedEvent(
                    mainline,
                    List.of(
                            new AdoptedPortCall(TRANSSHIPMENT_PORT, null, earlyConnecting),
                            new AdoptedPortCall(LOS_ANGELES, finalArrival, null)));

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> first = executor.submit(() ->
                        repositories.transaction().executeWithoutResult(status -> feederHandler.on(feederUpdate)));
                Future<?> second = executor.submit(() ->
                        repositories.transaction().executeWithoutResult(status -> mainlineHandler.on(mainlineUpdate)));
                first.get(30, TimeUnit.SECONDS);
                second.get(30, TimeUnit.SECONDS);
            }

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(completedEvaluations(evaluations, feeder)).isEqualTo(1);
                assertThat(completedEvaluations(evaluations, mainline)).isEqualTo(1);
            });
            assertThat(routeVersionOf(evaluations, confirmed))
                    .containsEntry("status", "REDESIGN_REQUIRED")
                    .containsEntry("redesign_reasons", "CONNECTION_TOO_SHORT");
            assertThat(redesignPublications(evaluations, confirmed)).isEqualTo(1);
        } finally {
            repositories.delete(feeder);
            repositories.delete(mainline);
            repositories.jdbc().update("DELETE FROM routing.connection_rule WHERE id = ?", ruleId);
        }
    }

    /**
     * 負ける評価が案件を読んだ後に、先の評価をコミットさせる。遅れた方は 0 件の UPDATE で競合を受け、同じトランザクションで読み直して再設計要を見て終わる（DE-06 は重ならない）。
     * 入力ポートをテストで組み立て、発行したイベントを記録する（DE-30 の配信は通さない）。
     */
    static void assertOverlappingEvaluationsRedesignOnce(Evaluations evaluations) throws Exception {
        Repositories repositories = evaluations.repositories();
        String voyageNumber = "C-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            Voyage voyage = adoptedVoyage(
                    voyageNumber,
                    List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(LOS_ANGELES, ARRIVAL, null)));
            repositories
                    .transaction()
                    .executeWithoutResult(status -> repositories.voyages().save(voyage));
            RoutingCase confirmed = confirmedCase(evaluations, List.of(voyage));
            repositories.transaction().executeWithoutResult(status -> {
                Voyage updated =
                        repositories.voyages().findByVoyageNumber(voyageNumber).orElseThrow();
                updated.apply(new VoyageAdoption(
                        voyageNumber,
                        List.of(
                                new PortCall(TOKYO, null, DEPARTURE),
                                new PortCall(LOS_ANGELES, new UtcInstant(Instant.parse("2098-02-01T00:00:00Z")), null)),
                        "1",
                        new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), new UtcInstant(ACQUIRED)),
                        NEWER_PUBLISHED));
                repositories.voyages().update(updated);
            });
            List<Object> published = new CopyOnWriteArrayList<>();
            CountDownLatch loserRead = new CountDownLatch(1);
            CountDownLatch winnerCommitted = new CountDownLatch(1);
            AtomicBoolean paused = new AtomicBoolean();
            RoutingCaseRepository pausing =
                    Mockito.mock(RoutingCaseRepository.class, AdditionalAnswers.delegatesTo(evaluations.cases()));
            Mockito.doAnswer(invocation -> {
                        Optional<RoutingCase> found = evaluations.cases().findByNumber(invocation.getArgument(0));
                        if (paused.compareAndSet(false, true)) {
                            loserRead.countDown();
                            awaitLatch(winnerCommitted);
                        }
                        return found;
                    })
                    .when(pausing)
                    .findByNumber(Mockito.any());
            ConfirmedRouteEvaluationEventHandler loser =
                    new ConfirmedRouteEvaluationEventHandler(pausing, commandService(evaluations, pausing, published));
            ConfirmedRouteEvaluationEventHandler winner = new ConfirmedRouteEvaluationEventHandler(
                    evaluations.cases(), commandService(evaluations, evaluations.cases(), published));
            VoyageUpdated event = new VoyageUpdated(voyageNumber, "1", NEWER_PUBLISHED);

            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                Future<?> losing = executor.submit(
                        () -> repositories.transaction().executeWithoutResult(status -> loser.on(event)));
                awaitLatch(loserRead);
                repositories.transaction().executeWithoutResult(status -> winner.on(event));
                winnerCommitted.countDown();
                losing.get(30, TimeUnit.SECONDS);
            }

            assertThat(routeVersionOf(evaluations, confirmed)).containsEntry("status", "REDESIGN_REQUIRED");
            assertThat(published).singleElement().isInstanceOf(RouteRedesignRequired.class);
            assertThat(evaluations
                            .cases()
                            .findByNumber(confirmed.number())
                            .orElseThrow()
                            .aggregateVersion())
                    .isEqualTo(confirmed.aggregateVersion() + 1);
        } finally {
            repositories.delete(voyageNumber);
        }
    }

    private static RoutingCaseCommandService commandService(
            Evaluations evaluations, RoutingCaseRepository cases, List<Object> published) {
        return new RoutingCaseCommandService(
                cases,
                evaluations.repositories().voyages(),
                evaluations.rules(),
                published::add,
                evaluations.clock(),
                evaluations.freshness(),
                evaluations.scope());
    }

    @Test
    void Bolt39e_行19_既存の行で読んだ後に新しいDE12が先にコミットすると古いDE12は適用されず版は1() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), true, true);
    }

    @Test
    void Bolt39e_行19_既存の行で読んだ後に古いDE12が先にコミットすると読み直して新しいDE12が適用され版は2() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), true, false);
    }

    @Test
    void Bolt39e_行20_初めての航海で新しいDE12が先にコミットすると負けた古いDE12は重複から読み直して適用されない() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), false, true);
    }

    @Test
    void Bolt39e_行20_初めての航海で古いDE12が先にコミットすると負けた新しいDE12は重複から読み直して適用される() throws Exception {
        assertConcurrentAdoptions(new Repositories(voyages, jdbc, transactionManager), false, false);
    }

    @ParameterizedTest(name = "既存の行 {0}、先に書くのが新しい DE-12 {1}")
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    void Bolt39e_行19_行20_書きが重なっても待たされた側は読み直し公開した時刻の新しい方が残る(boolean existing, boolean winnerIsNewer) throws Exception {
        assertOverlappingWrites(new Repositories(voyages, jdbc, transactionManager), existing, winnerIsNewer);
    }

    @Test
    void Bolt39e_行45_読みの途中で更新がコミットされても寄港が空の航海を読まず例外にならない() throws Exception {
        assertReadsDuringWrites(new Repositories(voyages, jdbc, transactionManager));
    }

    /** H2 と PostgreSQL のテストが共有する入力。 */
    record Repositories(VoyageRepository voyages, JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {

        TransactionTemplate transaction() {
            return new TransactionTemplate(transactionManager);
        }

        void delete(String voyageNumber) {
            jdbc.update("DELETE FROM routing.port_call WHERE voyage_number = ?", voyageNumber);
            jdbc.update("DELETE FROM routing.voyage WHERE voyage_number = ?", voyageNumber);
        }

        Voyage read(String voyageNumber) {
            return transaction()
                    .execute(status -> voyages.findByVoyageNumber(voyageNumber).orElseThrow());
        }
    }

    /** 本物のリポジトリに任せる土台（同時の順を作るリポジトリが必要なメソッドだけを変える）。 */
    private static class DelegatingVoyageRepository implements VoyageRepository {
        final VoyageRepository delegate;

        DelegatingVoyageRepository(VoyageRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Voyage> findByVoyageNumber(String voyageNumber) {
            return delegate.findByVoyageNumber(voyageNumber);
        }

        @Override
        public List<Voyage> findByVoyageNumbers(Collection<String> voyageNumbers) {
            return delegate.findByVoyageNumbers(voyageNumbers);
        }

        @Override
        public List<Voyage> findCandidateVoyages(UtcInstant departingAfter) {
            return delegate.findCandidateVoyages(departingAfter);
        }

        @Override
        public void save(Voyage voyage) {
            delegate.save(voyage);
        }

        @Override
        public void update(Voyage voyage) {
            delegate.update(voyage);
        }
    }

    /** 最初の読みの直後で、先の DE-12 のコミットを待つリポジトリ（読んだ後に先を越される順を作る）。 */
    private static final class PausingAfterFirstRead extends DelegatingVoyageRepository {
        private final CountDownLatch read;
        private final CountDownLatch resume;
        private final AtomicBoolean paused = new AtomicBoolean();

        PausingAfterFirstRead(VoyageRepository delegate, CountDownLatch read, CountDownLatch resume) {
            super(delegate);
            this.read = read;
            this.resume = resume;
        }

        @Override
        public Optional<Voyage> findByVoyageNumber(String voyageNumber) {
            Optional<Voyage> found = delegate.findByVoyageNumber(voyageNumber);
            if (paused.compareAndSet(false, true)) {
                read.countDown();
                awaitLatch(resume);
            }
            return found;
        }
    }

    /** 書いた後、コミットの前で止まるリポジトリ（行のロックか一意の索引を持ったまま、後の DE-12 を待たせる）。 */
    private static final class HoldingAfterWrite extends DelegatingVoyageRepository {
        private final CountDownLatch wrote;
        private final CountDownLatch release;

        HoldingAfterWrite(VoyageRepository delegate, CountDownLatch wrote, CountDownLatch release) {
            super(delegate);
            this.wrote = wrote;
            this.release = release;
        }

        @Override
        public void save(Voyage voyage) {
            delegate.save(voyage);
            hold();
        }

        @Override
        public void update(Voyage voyage) {
            delegate.update(voyage);
            hold();
        }

        private void hold() {
            wrote.countDown();
            awaitLatch(release);
        }
    }

    /** 書く直前に知らせるリポジトリ（後の DE-12 が書きに入ったことを先の DE-12 に伝える）。 */
    private static final class AnnouncingBeforeWrite extends DelegatingVoyageRepository {
        private final CountDownLatch writing;

        AnnouncingBeforeWrite(VoyageRepository delegate, CountDownLatch writing) {
            super(delegate);
            this.writing = writing;
        }

        @Override
        public void save(Voyage voyage) {
            writing.countDown();
            delegate.save(voyage);
        }

        @Override
        public void update(Voyage voyage) {
            writing.countDown();
            delegate.update(voyage);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("相手の DE-12 を待ちきれない");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static VoyageAdoptedValueUpdated event(String voyageNumber, UtcInstant arrival, UtcInstant publishedAt) {
        return new VoyageAdoptedValueUpdated(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), new UtcInstant(ACQUIRED)),
                voyageNumber,
                List.of(new AdoptedPortCall(TOKYO, null, DEPARTURE), new AdoptedPortCall(LOS_ANGELES, arrival, null)),
                "1",
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                publishedAt);
    }

    /**
     * 負ける側のトランザクションが航海を読んだ後に、先の DE-12 をコミットさせ、それから負ける側を続ける。どちらの順でも公開した時刻の新しい方が残る。
     *
     * @param existing 既存の行があるとき真（行 19）、初めての航海のとき偽（行 20）
     * @param winnerIsNewer 先にコミットするのが新しい DE-12 のとき真
     */
    static void assertConcurrentAdoptions(Repositories repositories, boolean existing, boolean winnerIsNewer)
            throws Exception {
        String voyageNumber = "C-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            if (existing) {
                seed(repositories, voyageNumber);
            }
            VoyageAdoptedValueUpdated older = event(voyageNumber, OLDER_ARRIVAL, OLDER_PUBLISHED);
            VoyageAdoptedValueUpdated newer = event(voyageNumber, NEWER_ARRIVAL, NEWER_PUBLISHED);
            CountDownLatch loserRead = new CountDownLatch(1);
            CountDownLatch winnerCommitted = new CountDownLatch(1);
            VoyageUpdateEventHandler loser = new VoyageUpdateEventHandler(
                    new PausingAfterFirstRead(repositories.voyages(), loserRead, winnerCommitted), NO_EVENTS);
            VoyageUpdateEventHandler winner = new VoyageUpdateEventHandler(repositories.voyages(), NO_EVENTS);
            VoyageAdoptedValueUpdated winning = winnerIsNewer ? newer : older;
            VoyageAdoptedValueUpdated losing = winnerIsNewer ? older : newer;

            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                Future<?> losingDelivery = executor.submit(
                        () -> repositories.transaction().executeWithoutResult(status -> loser.on(losing)));
                assertThat(loserRead.await(20, TimeUnit.SECONDS)).isTrue();
                repositories.transaction().executeWithoutResult(status -> winner.on(winning));
                winnerCommitted.countDown();
                losingDelivery.get(30, TimeUnit.SECONDS);
            }

            assertNewerRemains(repositories, voyageNumber, existing, winnerIsNewer);
        } finally {
            repositories.delete(voyageNumber);
        }
    }

    /**
     * 先の DE-12 が書いてコミットする前に、後の DE-12 が書きに入る（UPDATE は行のロックで、INSERT は一意の索引で待たされ、先のコミットの後に 0 件か重複になる）。
     * どちらの順でも公開した時刻の新しい方が残る。
     *
     * @param existing 既存の行があるとき真（行 19）、初めての航海のとき偽（行 20）
     * @param winnerIsNewer 先に書くのが新しい DE-12 のとき真
     */
    static void assertOverlappingWrites(Repositories repositories, boolean existing, boolean winnerIsNewer)
            throws Exception {
        String voyageNumber = "C-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            if (existing) {
                seed(repositories, voyageNumber);
            }
            VoyageAdoptedValueUpdated older = event(voyageNumber, OLDER_ARRIVAL, OLDER_PUBLISHED);
            VoyageAdoptedValueUpdated newer = event(voyageNumber, NEWER_ARRIVAL, NEWER_PUBLISHED);
            CountDownLatch winnerWrote = new CountDownLatch(1);
            CountDownLatch winnerRelease = new CountDownLatch(1);
            CountDownLatch loserWriting = new CountDownLatch(1);
            VoyageUpdateEventHandler winner = new VoyageUpdateEventHandler(
                    new HoldingAfterWrite(repositories.voyages(), winnerWrote, winnerRelease), NO_EVENTS);
            VoyageUpdateEventHandler loser = new VoyageUpdateEventHandler(
                    new AnnouncingBeforeWrite(repositories.voyages(), loserWriting), NO_EVENTS);
            VoyageAdoptedValueUpdated winning = winnerIsNewer ? newer : older;
            VoyageAdoptedValueUpdated losing = winnerIsNewer ? older : newer;

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> winningDelivery = executor.submit(
                        () -> repositories.transaction().executeWithoutResult(status -> winner.on(winning)));
                awaitLatch(winnerWrote);
                Future<?> losingDelivery = executor.submit(
                        () -> repositories.transaction().executeWithoutResult(status -> loser.on(losing)));
                awaitLatch(loserWriting);
                // 後の DE-12 が書きの中で待たされる間を置く（間に合わなくても先のコミットの後に書く順になるだけで、期待は変わらない）
                await().pollDelay(HOLD_WHILE_BLOCKED)
                        .atMost(Duration.ofSeconds(5))
                        .until(() -> true);
                winnerRelease.countDown();
                winningDelivery.get(30, TimeUnit.SECONDS);
                losingDelivery.get(30, TimeUnit.SECONDS);
            }

            assertNewerRemains(repositories, voyageNumber, existing, winnerIsNewer);
        } finally {
            repositories.delete(voyageNumber);
        }
    }

    private static void seed(Repositories repositories, String voyageNumber) {
        repositories
                .transaction()
                .executeWithoutResult(status -> repositories
                        .voyages()
                        .save(Voyage.adoptedFrom(new VoyageAdoption(
                                voyageNumber,
                                List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(LOS_ANGELES, ARRIVAL, null)),
                                "1",
                                new Source(
                                        SourceKind.EXTERNAL_RECORD,
                                        UUID.randomUUID().toString(),
                                        new UtcInstant(ACQUIRED)),
                                new UtcInstant(ACQUIRED)))));
    }

    private static void assertNewerRemains(
            Repositories repositories, String voyageNumber, boolean existing, boolean winnerIsNewer) {
        Voyage result = repositories.read(voyageNumber);
        assertThat(result.publishedAt()).contains(NEWER_PUBLISHED);
        assertThat(result.portCalls().getLast().arrivalAt()).isEqualTo(NEWER_ARRIVAL);
        long expectedVersion = (existing ? 1 : 0) + (winnerIsNewer ? 0 : 1);
        assertThat(result.aggregateVersion()).isEqualTo(expectedVersion);
    }

    /** 書き手が航海を保存・更新している間に読み手が読み続け、例外が 1 つも出ない（読みは航海と寄港の 1 文）。 */
    static void assertReadsDuringWrites(Repositories repositories) throws Exception {
        String prefix = "C-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        List<String> written = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicBoolean writing = new AtomicBoolean(true);
        UtcInstant pastJudged = new UtcInstant(Instant.parse("2097-01-01T00:00:00Z"));
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<?> candidateReader = executor.submit(
                    () -> read(writing, failures, () -> repositories.voyages().findCandidateVoyages(pastJudged)));
            Future<?> numberReader = executor.submit(() ->
                    read(writing, failures, () -> repositories.voyages().findByVoyageNumbers(List.copyOf(written))));
            Future<?> writer = executor.submit(() -> {
                try {
                    for (int i = 0; i < 30; i++) {
                        String number = prefix + i;
                        written.add(number);
                        writeAndUpdate(repositories, number);
                    }
                } catch (RuntimeException e) {
                    failures.add(e);
                } finally {
                    writing.set(false);
                }
            });
            writer.get(60, TimeUnit.SECONDS);
            candidateReader.get(60, TimeUnit.SECONDS);
            numberReader.get(60, TimeUnit.SECONDS);
        } finally {
            written.forEach(repositories::delete);
        }
        assertThat(failures).isEmpty();
    }

    private static void read(AtomicBoolean writing, List<Throwable> failures, Runnable reader) {
        while (writing.get()) {
            try {
                reader.run();
            } catch (RuntimeException e) {
                failures.add(e);
                return;
            }
        }
    }

    private static void writeAndUpdate(Repositories repositories, String number) {
        repositories
                .transaction()
                .executeWithoutResult(status -> repositories
                        .voyages()
                        .save(Voyage.adoptedFrom(new VoyageAdoption(
                                number,
                                List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(LOS_ANGELES, ARRIVAL, null)),
                                "1",
                                new Source(
                                        SourceKind.EXTERNAL_RECORD,
                                        UUID.randomUUID().toString(),
                                        new UtcInstant(ACQUIRED)),
                                new UtcInstant(ACQUIRED)))));
        repositories.transaction().executeWithoutResult(status -> {
            Voyage voyage = repositories.voyages().findByVoyageNumber(number).orElseThrow();
            voyage.apply(new VoyageAdoption(
                    number,
                    List.of(new PortCall(TOKYO, null, DEPARTURE), new PortCall(LOS_ANGELES, NEWER_ARRIVAL, null)),
                    "1",
                    new Source(SourceKind.EXTERNAL_RECORD, UUID.randomUUID().toString(), new UtcInstant(ACQUIRED)),
                    NEWER_PUBLISHED));
            repositories.voyages().update(voyage);
        });
    }
}

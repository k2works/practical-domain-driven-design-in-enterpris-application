package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.RowResult;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.routing.application.internal.eventhandlers.ConfirmedRouteEvaluationEventHandler;
import com.example.cargotracker.routing.domain.events.VoyageUpdated;
import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentRoutingCaseUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 外部データの取込（DE-12）から確定した経路の評価（DE-30）、再設計要（DE-06）、見積りの印までの配信を PostgreSQL 18 で確かめる（US-07 AC4、US-14 AC1。Bolt 39f の行 17b・19〜24・27・28。T-184）。
 * ファイルを取り込んで航海を作り、その航海で案件の候補を算出して確定してから、次の DE-12 を流す。航海番号はテストごとに一意にし、港は JPTYO → USLAX にする（共有の DB の候補につながらない）。
 * 案件番号は 2096 年を使う。リポジトリは行 17b で競合を作るためにスパイにする（ほかの行は本物のまま動く）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConfirmedRouteEvaluationDeliveryIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String DE12_LISTENER = "VoyageUpdateEventHandler";
    private static final String DE30_LISTENER = ConfirmedRouteEvaluationEventHandler.class.getSimpleName();
    private static final String DE06_LISTENER = "QuotationRouteRedesignEventHandler";
    private static final Location TOKYO = new Location("JPTYO");
    private static final Location LOS_ANGELES = new Location("USLAX");
    private static final Location BUSAN = new Location("KRPUS");
    private static final Instant DEPARTURE = Instant.parse("2097-12-01T00:00:00Z");
    private static final Instant ARRIVAL = Instant.parse("2097-12-20T00:00:00Z");
    private static final Instant STILL_IN_TIME = Instant.parse("2097-12-22T00:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2097-12-25T00:00:00Z");
    private static final Instant TOO_LATE = Instant.parse("2097-12-28T00:00:00Z");
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());

    private final AuthenticatedActor steward = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.DATA_STEWARD),
            "データ責任者",
            "運営会社");
    private final String voyageNumber = "D-" + UUID.randomUUID().toString().substring(0, 12);
    private UUID sourceId;

    @Autowired
    IngestionService ingestionService;

    @Autowired
    ReceivedRecordDecisionService decisionService;

    @Autowired
    VoyageRepository voyages;

    @Autowired
    ConnectionRuleRepository connectionRules;

    @Autowired
    InfoFreshnessPolicy freshness;

    @MockitoSpyBean
    RoutingCaseRepository routingCases;

    @Autowired
    QuotationRepository quotations;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @BeforeEach
    void insertApprovedSource() {
        sourceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'VOYAGE', 'APPROVED', 0)",
                sourceId,
                "船会社-" + UUID.randomUUID());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
        jdbc.update("DELETE FROM routing.port_call WHERE voyage_number = ?", voyageNumber);
        jdbc.update("DELETE FROM routing.voyage WHERE voyage_number = ?", voyageNumber);
        // 行 17b の未完了の発行記録を、後のテストの起動の再配信に残さない
        jdbc.update(
                "DELETE FROM platform.event_publication WHERE serialized_event LIKE ? AND completion_date IS NULL",
                "%" + voyageNumber + "%");
    }

    @Nested
    class Bolt39f_配信 {

        @Test
        void 行19_情報版が変わり到着が期限を過ぎると再設計要になりDE06は1件で見積りに印が付き発行記録が完了する() {
            RoutingCase confirmed = confirmedOnIngestedVoyage(ARRIVAL);
            QuotationId quotationId = quotationIdOf(confirmed);

            UUID received = ingestVersion("2", TOO_LATE);

            awaitEvaluated(1);
            awaitCompleted(DE12_LISTENER, received);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");
            assertThat(redesignPublications(confirmed)).isEqualTo(1);
            await().atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(quotations
                                    .findById(quotationId)
                                    .orElseThrow()
                                    .assignedRoute()
                                    .orElseThrow()
                                    .redesignRequiredAt())
                            .isNotNull());
        }

        @Test
        void 行20_同じ情報版で隔離を採用して到着が期限を過ぎると再設計要になりDE06は1件() {
            Instant acquiredAt = acquiredAt();
            Ingested ingested = ingestRows(row(2, "1", acquiredAt, ARRIVAL), row(3, "1", acquiredAt, TOO_LATE));
            assertThat(ingested.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED, Classification.QUARANTINED);
            awaitCompleted(DE12_LISTENER, ingested.rows().getFirst().receivedRecordId());
            RoutingCase confirmed = confirmCase();

            decisionService.adopt(
                    steward,
                    new AdoptReceivedRecordCommand(ingested.rows().get(1).receivedRecordId(), null));

            awaitEvaluated(1);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");
            assertThat(redesignPublications(confirmed)).isEqualTo(1);
        }

        @Test
        void 行20b_誤った採用で再設計要になった経路は新しい外部版で元の予定に直しても確定に戻らない() {
            Instant acquiredAt = acquiredAt();
            Ingested ingested = ingestRows(row(2, "1", acquiredAt, ARRIVAL), row(3, "1", acquiredAt, TOO_LATE));
            awaitCompleted(DE12_LISTENER, ingested.rows().getFirst().receivedRecordId());
            RoutingCase confirmed = confirmCase();
            decisionService.adopt(
                    steward,
                    new AdoptReceivedRecordCommand(ingested.rows().get(1).receivedRecordId(), null));
            awaitEvaluated(1);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");

            ingestVersion("2", ARRIVAL);

            awaitEvaluated(2);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");
            assertThat(redesignPublications(confirmed)).isEqualTo(1);
        }

        @Test
        void 行21_同じ情報版で予定も同じ原本を採用するとDE30は1件で評価に入らず確定のまま() {
            Instant acquiredAt = acquiredAt();
            Ingested ingested =
                    ingestRows(row(2, "1", acquiredAt, ARRIVAL), row(3, "1", acquiredAt.plusSeconds(1), ARRIVAL));
            assertThat(ingested.rows())
                    .extracting(RowResult::classification)
                    .containsExactly(Classification.ADOPTED, Classification.QUARANTINED);
            awaitCompleted(DE12_LISTENER, ingested.rows().getFirst().receivedRecordId());
            RoutingCase confirmed = confirmCase();
            long versionBefore = confirmed.aggregateVersion();

            decisionService.adopt(
                    steward,
                    new AdoptReceivedRecordCommand(ingested.rows().get(1).receivedRecordId(), null));

            awaitEvaluated(1);
            assertStillConfirmed(confirmed, versionBefore);
        }

        @Test
        void 行22_情報版が変わり予定が同じなら評価で適合し確定のままで区間も確定のとき() {
            RoutingCase confirmed = confirmedOnIngestedVoyage(ARRIVAL);

            ingestVersion("2", ARRIVAL);

            awaitEvaluated(1);
            assertStillConfirmed(confirmed, confirmed.aggregateVersion());
            assertThat(confirmedLegInfoVersion(confirmed)).isEqualTo("1");
        }

        @Test
        void 行22b_予定が変わっても今の予定で適合すれば確定のままで区間は確定のとき() {
            RoutingCase confirmed = confirmedOnIngestedVoyage(ARRIVAL);

            ingestVersion("2", STILL_IN_TIME);

            awaitEvaluated(1);
            assertStillConfirmed(confirmed, confirmed.aggregateVersion());
            assertThat(confirmedLegInfoVersion(confirmed)).isEqualTo("1");
            assertThat(routingCases
                            .findByNumber(confirmed.number())
                            .orElseThrow()
                            .confirmedCandidateOf(1)
                            .orElseThrow()
                            .legs()
                            .getFirst()
                            .arrivalAt())
                    .isEqualTo(new UtcInstant(ARRIVAL));
        }

        @Test
        void 行23_2度寄る経路で情報版だけが上がっても確定のままでDE06は0件() {
            Instant acquiredAt = acquiredAt();
            List<Stop> stops = List.of(
                    new Stop(TOKYO, null, DEPARTURE),
                    new Stop(BUSAN, Instant.parse("2097-12-03T00:00:00Z"), Instant.parse("2097-12-04T00:00:00Z")),
                    new Stop(TOKYO, Instant.parse("2097-12-06T00:00:00Z"), Instant.parse("2097-12-07T00:00:00Z")),
                    new Stop(LOS_ANGELES, ARRIVAL, null));
            Ingested ingested = ingestRows(row(2, "1", acquiredAt, stops));
            awaitCompleted(DE12_LISTENER, ingested.rows().getFirst().receivedRecordId());
            RoutingCase confirmed = confirmCandidate(routingCase -> routingCase.routeVersion().candidates().stream()
                    .filter(candidate -> Integer.valueOf(3)
                            .equals(candidate.legs().getFirst().loadCallNo()))
                    .map(RouteCandidate::candidateNo)
                    .findFirst()
                    .orElseThrow());

            UUID received = ingest(row(2, "2", acquiredAt(), stops));

            awaitCompleted(DE12_LISTENER, received);
            awaitEvaluated(1);
            assertStillConfirmed(confirmed, confirmed.aggregateVersion());
        }

        @Test
        void 行24_DE30の再配信では再設計要は1回でDE06は1件() {
            RoutingCase confirmed = confirmedOnIngestedVoyage(ARRIVAL);
            ingestVersion("2", TOO_LATE);
            awaitEvaluated(1);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");
            Object requiredAt = redesignRequiredAt(confirmed);

            transactionTemplate.executeWithoutResult(status ->
                    events.publishEvent(new VoyageUpdated(voyageNumber, "2", new UtcInstant(clock.instant()))));

            awaitEvaluated(2);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");
            assertThat(redesignRequiredAt(confirmed)).isEqualTo(requiredAt);
            assertThat(redesignPublications(confirmed)).isEqualTo(1);
        }

        @Test
        void 行27_再設計要の案件に後からDE12が届いても再設計要のままでDE06は増えない() {
            RoutingCase confirmed = confirmedOnIngestedVoyage(ARRIVAL);
            ingestVersion("2", TOO_LATE);
            awaitEvaluated(1);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");

            ingestVersion("3", Instant.parse("2097-12-29T00:00:00Z"));

            awaitEvaluated(2);
            assertRedesignRequired(confirmed, "DEADLINE_EXCEEDED");
            assertThat(redesignPublications(confirmed)).isEqualTo(1);
        }

        @Test
        void 行28_候補提示済みの案件はDE12が届いても変わらない() {
            UUID received = ingest(row(2, "1", acquiredAt(), ARRIVAL));
            awaitCompleted(DE12_LISTENER, received);
            RoutingCase calculated = calculated();

            ingestVersion("2", TOO_LATE);

            awaitEvaluated(1);
            assertThat(status(calculated)).isEqualTo("CANDIDATES_PRESENTED");
            assertThat(redesignPublications(calculated)).isZero();
        }

        @Test
        void 行17b_2件目が競合を3回受けると1件目の再設計要とDE06も戻りDE30の発行記録が未完了で残る() {
            UUID received = ingest(row(2, "1", acquiredAt(), ARRIVAL));
            awaitCompleted(DE12_LISTENER, received);
            RoutingCase first = confirmCase();
            RoutingCase second = confirmCase();
            doThrow(new ConcurrentRoutingCaseUpdateException(second.number(), 0))
                    .when(routingCases)
                    .updateBySystem(argThat(routingCase ->
                            routingCase != null && routingCase.number().equals(second.number())));

            ingestVersion("2", TOO_LATE);

            await().atMost(TIMEOUT)
                    .untilAsserted(() -> verify(routingCases, atLeast(3))
                            .updateBySystem(argThat(routingCase ->
                                    routingCase != null && routingCase.number().equals(second.number()))));
            // 例外の後のロールバックの間を置く（途中で 1 件目がコミットされる誤りを見逃さない）
            await().pollDelay(Duration.ofMillis(800))
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> true);
            assertThat(status(first)).isEqualTo("CONFIRMED");
            assertThat(status(second)).isEqualTo("CONFIRMED");
            assertThat(redesignPublications(first)).isZero();
            assertThat(redesignPublications(second)).isZero();
            assertThat(publications(DE30_LISTENER, true)).isZero();
            assertThat(publications(DE30_LISTENER, false)).isEqualTo(1);
        }
    }

    /** Bolt 39e2 の開発レビューから送られた通しの確かめ（【業務の判断】(s)）。 */
    @Nested
    class Bolt39e2からの申し送り {

        @Test
        void 候補の算出の後に予定が同じ原本を採用しても配信の後の航海で確定できる() {
            Instant acquiredAt = acquiredAt();
            Ingested ingested =
                    ingestRows(row(2, "1", acquiredAt, ARRIVAL), row(3, "1", acquiredAt.plusSeconds(1), ARRIVAL));
            UUID quarantined = ingested.rows().get(1).receivedRecordId();
            awaitCompleted(DE12_LISTENER, ingested.rows().getFirst().receivedRecordId());
            RoutingCaseNumber number = calculated().number();

            decisionService.adopt(steward, new AdoptReceivedRecordCommand(quarantined, null));
            awaitCompleted(DE12_LISTENER, quarantined);
            assertThat(jdbc.queryForObject(
                            "SELECT source_ref FROM routing.voyage WHERE voyage_number = ?",
                            String.class,
                            voyageNumber))
                    .isEqualTo(quarantined.toString());

            RoutingCase confirmed = confirm(number, routingCase -> 1);

            assertThat(status(confirmed)).isEqualTo("CONFIRMED");
        }
    }

    /** 寄港 1 つの値（CSV とドメインの値の両方を作る）。 */
    private record Stop(Location port, Instant arrival, Instant departure) {}

    private interface CandidateChoice {
        int candidateNo(RoutingCase routingCase);
    }

    private static List<Stop> direct(Instant arrival) {
        return List.of(new Stop(TOKYO, null, DEPARTURE), new Stop(LOS_ANGELES, arrival, null));
    }

    private IngestedRow row(int line, String version, Instant acquiredAt, Instant arrival) {
        return row(line, version, acquiredAt, direct(arrival));
    }

    private IngestedRow row(int line, String version, Instant acquiredAt, List<Stop> stops) {
        VoyageValues values = new VoyageValues(
                voyageNumber,
                stops.stream()
                        .map(stop -> new PortCallValue(stop.port(), utc(stop.arrival()), utc(stop.departure())))
                        .toList());
        String calls = stops.stream()
                .map(stop -> stop.port().unLocode() + ";" + text(stop.arrival()) + ";" + text(stop.departure()))
                .collect(Collectors.joining("|"));
        String text = voyageNumber + "," + version + "," + acquiredAt + "," + calls;
        return new IngestedRow(
                line, line, voyageNumber, version, acquiredAt, values, text.getBytes(StandardCharsets.UTF_8));
    }

    private static UtcInstant utc(Instant instant) {
        return instant == null ? null : new UtcInstant(instant);
    }

    private static String text(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private Ingested ingestRows(IngestedRow... rows) {
        StringBuilder content = new StringBuilder("external_id,external_version,acquired_at,port_calls");
        for (IngestedRow row : rows) {
            content.append('\n').append(new String(row.payload(), StandardCharsets.UTF_8));
        }
        IngestionOutcome outcome = ingestionService.ingest(
                steward,
                new IngestFileCommand(
                        sourceId,
                        content.toString().getBytes(StandardCharsets.UTF_8),
                        rows.length,
                        List.of(rows),
                        List.of(),
                        null));
        assertThat(outcome).isInstanceOf(Ingested.class);
        return (Ingested) outcome;
    }

    private UUID ingest(IngestedRow row) {
        return ingestRows(row).rows().getFirst().receivedRecordId();
    }

    private UUID ingestVersion(String version, Instant arrival) {
        return ingest(row(2, version, acquiredAt(), arrival));
    }

    private Instant acquiredAt() {
        return clock.instant().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
    }

    private RoutingCase confirmedOnIngestedVoyage(Instant arrival) {
        UUID received = ingest(row(2, "1", acquiredAt(), arrival));
        awaitCompleted(DE12_LISTENER, received);
        return confirmCase();
    }

    private RoutingCase confirmCase() {
        return confirmCandidate(routingCase -> 1);
    }

    /** 取り込んだ航海だけで候補を算出した案件（経路版は候補提示済み。見積りは荷主承認待ちで案件の経路版 1 を割り当てる）。 */
    private RoutingCase calculated() {
        List<Voyage> current = voyages.findByVoyageNumbers(List.of(voyageNumber));
        RoutingCaseNumber number = new RoutingCaseNumber(2096, SEQUENCE.incrementAndGet());
        QuotationId quotationId = transactionTemplate.execute(status -> awaitingApproval(number));
        RoutingCase opened = RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                number,
                UUID.randomUUID(),
                "TR-2096-0001",
                1,
                quotationId.value(),
                List.of(),
                new RouteSpecification(TOKYO, LOS_ANGELES, new UtcInstant(DEADLINE), RouteCargoCategory.GENERAL),
                new UtcInstant(clock.instant()),
                new UtcInstant(Instant.parse("2097-11-01T00:00:00Z")),
                UUID.randomUUID(),
                new CompanyId(UUID.randomUUID()));
        transactionTemplate.executeWithoutResult(status -> routingCases.save(opened));
        transactionTemplate.executeWithoutResult(status -> {
            RoutingCase loaded = routingCases.findByNumber(number).orElseThrow();
            loaded.calculateCandidates(
                    current,
                    connectionRules.findAll(),
                    new UtcInstant(clock.instant()),
                    new RouteCandidateFinder(),
                    new ConstraintEvaluator(),
                    freshness);
            routingCases.update(loaded, UUID.randomUUID());
        });
        return routingCases.findByNumber(number).orElseThrow();
    }

    private RoutingCase confirmCandidate(CandidateChoice choice) {
        return confirm(calculated().number(), choice);
    }

    /** 今の航海を読み直して確定する（確定のときの照合を本物の航海で通す）。 */
    private RoutingCase confirm(RoutingCaseNumber number, CandidateChoice choice) {
        List<Voyage> current = voyages.findByVoyageNumbers(List.of(voyageNumber));
        transactionTemplate.executeWithoutResult(status -> {
            RoutingCase loaded = routingCases.findByNumber(number).orElseThrow();
            loaded.confirm(
                    choice.candidateNo(loaded),
                    "配信の確かめの根拠。",
                    new RouteApprover(UUID.randomUUID(), true),
                    current,
                    connectionRules.findAll(),
                    new UtcInstant(clock.instant()),
                    new ConstraintEvaluator(),
                    freshness);
            routingCases.update(loaded, UUID.randomUUID());
        });
        return routingCases.findByNumber(number).orElseThrow();
    }

    private QuotationId awaitingApproval(RoutingCaseNumber number) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2096, number.sequence()),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                at("2096-01-05T01:00:00Z"));
        transportRequests.save(request);
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2096-01-05T04:00:00Z"));
        quotation.presentInternally(STAFF, at("2096-01-05T04:30:00Z"));
        quotation.requestRouteDesign(SHIPPER_USER, at("2096-01-06T02:00:00Z"));
        quotation.assignRoute(
                new AssignedRoute(
                        number.text(),
                        1,
                        at("2096-01-07T05:00:00Z"),
                        List.of(new AssignedRouteLeg(
                                voyageNumber, TOKYO, LOS_ANGELES, new UtcInstant(DEPARTURE), new UtcInstant(ARRIVAL)))),
                at("2096-01-07T05:00:00Z"));
        quotation.clearDomainEvents();
        quotations.save(quotation);
        return quotation.id();
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    private QuotationId quotationIdOf(RoutingCase routingCase) {
        return new QuotationId(routingCase.quotationId());
    }

    private void awaitCompleted(String listener, UUID receivedRecordId) {
        await().atMost(TIMEOUT)
                .untilAsserted(() ->
                        assertThat(completedDe12(listener, receivedRecordId)).isEqualTo(1));
    }

    private int completedDe12(String listener, UUID receivedRecordId) {
        Integer completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ? AND serialized_event LIKE ?"
                        + " AND listener_id LIKE ? AND completion_date IS NOT NULL",
                Integer.class,
                "%" + receivedRecordId + "%",
                "%" + voyageNumber + "%",
                "%" + listener + "%");
        return completed == null ? 0 : completed;
    }

    private void awaitEvaluated(int count) {
        await().atMost(TIMEOUT)
                .untilAsserted(
                        () -> assertThat(publications(DE30_LISTENER, true)).isEqualTo(count));
    }

    private int publications(String listener, boolean completed) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ? AND listener_id LIKE ?"
                        + " AND completion_date IS " + (completed ? "NOT NULL" : "NULL"),
                Integer.class,
                "%" + voyageNumber + "%",
                "%" + listener + "%");
        return count == null ? 0 : count;
    }

    private int redesignPublications(RoutingCase routingCase) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE event_type LIKE '%RouteRedesignRequired'"
                        + " AND listener_id LIKE ? AND serialized_event LIKE ?",
                Integer.class,
                "%" + DE06_LISTENER + "%",
                "%" + routingCase.number().text() + "%");
        return count == null ? 0 : count;
    }

    private String status(RoutingCase routingCase) {
        return jdbc.queryForObject(
                "SELECT status FROM routing.route_version WHERE routing_case_id = ?",
                String.class,
                routingCase.id().value());
    }

    private Object redesignRequiredAt(RoutingCase routingCase) {
        return jdbc.queryForObject(
                "SELECT redesign_required_at FROM routing.route_version WHERE routing_case_id = ?",
                Object.class,
                routingCase.id().value());
    }

    private void assertRedesignRequired(RoutingCase routingCase, String reasons) {
        Map<String, Object> version = jdbc.queryForMap(
                "SELECT status, redesign_reasons FROM routing.route_version WHERE routing_case_id = ?",
                routingCase.id().value());
        assertThat(version).containsEntry("status", "REDESIGN_REQUIRED").containsEntry("redesign_reasons", reasons);
    }

    private void assertStillConfirmed(RoutingCase routingCase, long aggregateVersion) {
        assertThat(status(routingCase)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                        "SELECT version FROM routing.routing_case WHERE id = ?",
                        Long.class,
                        routingCase.id().value()))
                .isEqualTo(aggregateVersion);
        assertThat(redesignPublications(routingCase)).isZero();
    }

    private String confirmedLegInfoVersion(RoutingCase routingCase) {
        List<String> versions = new ArrayList<>(jdbc.queryForList(
                "SELECT l.info_version FROM routing.candidate_leg l JOIN routing.route_version v"
                        + " ON v.routing_case_id = l.routing_case_id AND v.route_version_no = l.route_version_no"
                        + " AND v.selected_candidate_no = l.candidate_no WHERE l.routing_case_id = ?",
                String.class,
                routingCase.id().value()));
        assertThat(versions).hasSize(1);
        return versions.getFirst();
    }
}

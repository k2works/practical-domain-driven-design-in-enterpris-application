package com.example.cargotracker.routing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentRoutingCaseUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateRoutingCaseException;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 経路設計（`routing`、R-INV-01・02・10）の永続化を PostgreSQL で確かめる（Bolt 17）。
 * 案件番号はほかのテストとぶつからないよう 2083 年を使う。航海の番号はこのテストだけのもの（IT- で始まる）を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisRoutingRepositoriesIntegrationTest {

    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location HONG_KONG = new Location("HKHKG");
    static final Location ROTTERDAM = new Location("NLRTM");
    static final UtcInstant DEADLINE = at("2083-11-02T00:00:00Z");
    static final UtcInstant JUDGED_AT = at("2083-10-07T03:00:00Z");

    @Autowired
    RoutingCaseRepository repository;

    @Autowired
    RoutingCaseNumberIssuer numberIssuer;

    @Autowired
    VoyageRepository voyageRepository;

    @Autowired
    ConnectionRuleRepository connectionRuleRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 案件を保存して案件番号で読み出せ輸送要求版の案件があると分かる() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");

        repository.save(routingCase);

        RoutingCase found = repository.findByNumber(routingCase.number()).orElseThrow();
        assertThat(found.id()).isEqualTo(routingCase.id());
        assertThat(found.transportRequestNumber()).isEqualTo("TR-2083-0001");
        assertThat(found.routePolicyVia()).containsExactly(SINGAPORE);
        assertThat(found.specification()).isEqualTo(routingCase.specification());
        assertThat(found.requestedAt()).isEqualTo(routingCase.requestedAt());
        assertThat(found.routeVersion().status()).isEqualTo(RouteVersionStatus.DRAFT);
        assertThat(repository.existsForTransportRequestVersion(routingCase.transportRequestId(), 1))
                .isTrue();
        assertThat(repository.existsForTransportRequestVersion(routingCase.transportRequestId(), 2))
                .isFalse();
    }

    @Test
    void 同じ輸送要求版の案件は2つ保存できずトランザクションは続けられる() {
        UUID transportRequestId = UUID.randomUUID();
        repository.save(open(transportRequestId, "2083-10-06T02:00:00Z"));

        assertThatThrownBy(() -> repository.save(open(transportRequestId, "2083-10-06T02:00:01Z")))
                .isInstanceOf(DuplicateRoutingCaseException.class);
        assertThat(repository.existsForTransportRequestVersion(transportRequestId, 1))
                .isTrue();
    }

    @Test
    void 候補を算出した案件を更新すると候補と区間と除外理由が読み出せ再算出で置き換わる() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.calculateCandidates(
                voyages(), rules(), JUDGED_AT, new RouteCandidateFinder(), new ConstraintEvaluator());

        repository.update(loaded);

        RoutingCase found = repository.findByNumber(routingCase.number()).orElseThrow();
        assertThat(found.routeVersion()).isEqualTo(loaded.routeVersion());
        assertThat(found.routeVersion().candidates())
                .flatExtracting(candidate -> candidate.evaluation().reasons())
                .extracting(reason -> reason.code())
                .containsExactlyInAnyOrder(
                        ExclusionReasonCode.CONNECTION_TOO_SHORT,
                        ExclusionReasonCode.NOT_CONNECTABLE,
                        ExclusionReasonCode.DEADLINE_EXCEEDED);
        assertThat(found.aggregateVersion()).isEqualTo(1);

        found.calculateCandidates(
                List.of(voyages().getFirst()),
                rules(),
                at("2083-10-07T04:00:00Z"),
                new RouteCandidateFinder(),
                new ConstraintEvaluator());
        repository.update(found);

        assertThat(repository.findByNumber(routingCase.number()).orElseThrow().routeVersion())
                .isEqualTo(found.routeVersion());
    }

    @Test
    void 読み込んだ後に更新された案件は更新できない() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase first = repository.findByNumber(routingCase.number()).orElseThrow();
        RoutingCase second = repository.findByNumber(routingCase.number()).orElseThrow();
        first.calculateCandidates(voyages(), rules(), JUDGED_AT, new RouteCandidateFinder(), new ConstraintEvaluator());
        repository.update(first);
        second.calculateCandidates(
                voyages(), rules(), JUDGED_AT, new RouteCandidateFinder(), new ConstraintEvaluator());

        assertThatThrownBy(() -> repository.update(second)).isInstanceOf(ConcurrentRoutingCaseUpdateException.class);
    }

    @Test
    void 案件一覧は依頼時刻の新しい順() {
        RoutingCase older = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        RoutingCase newer = open(UUID.randomUUID(), "2083-10-06T03:00:00Z");
        repository.save(older);
        repository.save(newer);

        List<RoutingCaseSummary> summaries = repository.findSummaries().stream()
                .filter(summary -> summary.number().year() == 2083)
                .toList();

        assertThat(summaries)
                .extracting(RoutingCaseSummary::number)
                .containsSubsequence(newer.number(), older.number());
        assertThat(summaries.getFirst())
                .isEqualTo(new RoutingCaseSummary(
                        newer.number(),
                        "TR-2083-0001",
                        1,
                        TOKYO,
                        ROTTERDAM,
                        DEADLINE,
                        newer.requestedAt(),
                        RouteVersionStatus.DRAFT));
    }

    @Test
    void 航海は寄港の順に規則は期間とともに読み出せる() {
        jdbc.update("INSERT INTO routing.voyage (voyage_number, adopted_info_version, source_kind, source_ref,"
                + " acquired_at, version, updated_at) VALUES ('IT-001', 'IT@3', 'MANUAL_ENTRY', 'テスト',"
                + " TIMESTAMP WITH TIME ZONE '2083-10-01 06:10:00+00', 0, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO routing.port_call VALUES ('IT-001', 2, 'NLRTM',"
                + " TIMESTAMP WITH TIME ZONE '2083-10-30 00:00:00+00', NULL)");
        jdbc.update("INSERT INTO routing.port_call VALUES ('IT-001', 1, 'JPTYO', NULL,"
                + " TIMESTAMP WITH TIME ZONE '2083-10-08 00:00:00+00')");
        UUID ruleId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO routing.connection_rule VALUES (?, '*', 'SGSIN', 720,"
                        + " TIMESTAMP WITH TIME ZONE '2083-01-01 00:00:00+00', TIMESTAMP WITH TIME ZONE '2084-01-01 00:00:00+00', 0)",
                ruleId);

        assertThat(voyageRepository.findAll())
                .filteredOn(voyage -> voyage.voyageNumber().equals("IT-001"))
                .containsExactly(new Voyage(
                        "IT-001",
                        List.of(
                                new PortCall(TOKYO, null, at("2083-10-08T00:00:00Z")),
                                new PortCall(ROTTERDAM, at("2083-10-30T00:00:00Z"), null)),
                        "IT@3",
                        at("2083-10-01T06:10:00Z")));
        assertThat(connectionRuleRepository.findAll())
                .filteredOn(rule -> rule.id().equals(ruleId))
                .containsExactly(new ConnectionRule(
                        ruleId,
                        SINGAPORE,
                        Duration.ofHours(12),
                        at("2083-01-01T00:00:00Z"),
                        at("2084-01-01T00:00:00Z")));
    }

    @Test
    void 経路版の状態はデータモデルの値だけを受け付ける() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE routing.route_version SET status = 'UNKNOWN' WHERE routing_case_id = ?",
                        routingCase.id().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 除外理由の区分はデータモデルの値だけを受け付ける() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.calculateCandidates(
                voyages(), rules(), JUDGED_AT, new RouteCandidateFinder(), new ConstraintEvaluator());
        repository.update(loaded);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE routing.exclusion_reason SET reason_code = 'UNKNOWN' WHERE routing_case_id = ?",
                        routingCase.id().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 案件番号は年ごとに1つずつ振る() {
        RoutingCaseNumber first = numberIssuer.next(2083);
        RoutingCaseNumber second = numberIssuer.next(2083);

        assertThat(second.sequence()).isEqualTo(first.sequence() + 1);
        assertThat(second.year()).isEqualTo(2083);
    }

    private RoutingCase open(UUID transportRequestId, String requestedAt) {
        return RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                numberIssuer.next(2083),
                transportRequestId,
                "TR-2083-0001",
                1,
                UUID.randomUUID(),
                List.of(SINGAPORE),
                new RouteSpecification(TOKYO, ROTTERDAM, DEADLINE, "GENERAL"),
                at(requestedAt));
    }

    /** 直行（適合）、シンガポールで接続不足、香港で規則なし、期限超過の組合せ。 */
    static List<Voyage> voyages() {
        return List.of(
                voyage(
                        "IT-DIRECT",
                        call(TOKYO, null, "2083-10-08T00:00:00Z"),
                        call(ROTTERDAM, "2083-10-30T00:00:00Z", null)),
                voyage(
                        "IT-FEED-SG",
                        call(TOKYO, null, "2083-10-10T00:00:00Z"),
                        call(SINGAPORE, "2083-10-20T00:00:00Z", null)),
                voyage(
                        "IT-SG-SHORT",
                        call(SINGAPORE, null, "2083-10-20T04:00:00Z"),
                        call(ROTTERDAM, "2083-10-31T00:00:00Z", null)),
                voyage(
                        "IT-FEED-HK",
                        call(TOKYO, null, "2083-10-11T00:00:00Z"),
                        call(HONG_KONG, "2083-10-15T00:00:00Z", null)),
                voyage(
                        "IT-HK",
                        call(HONG_KONG, null, "2083-10-16T00:00:00Z"),
                        call(ROTTERDAM, "2083-10-29T00:00:00Z", null)),
                voyage(
                        "IT-LATE",
                        call(TOKYO, null, "2083-10-09T00:00:00Z"),
                        call(ROTTERDAM, "2083-11-03T12:00:00Z", null)));
    }

    static List<ConnectionRule> rules() {
        return List.of(new ConnectionRule(
                UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2083-01-01T00:00:00Z"), null));
    }

    static Voyage voyage(String number, PortCall... calls) {
        return new Voyage(number, List.of(calls), number + "@1", at("2083-10-01T06:10:00Z"));
    }

    static PortCall call(Location port, String arrival, String departure) {
        return new PortCall(port, arrival == null ? null : at(arrival), departure == null ? null : at(departure));
    }

    static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

package com.example.cargotracker.routing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentRoutingCaseUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateRoutingCaseException;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.transaction.annotation.Transactional;

/**
 * 経路設計（`routing`、R-INV-01・02・05・06・10）の永続化を PostgreSQL で確かめる（Bolt 17・19）。
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
    static final String EXPIRES_AT = "2083-10-08T09:00:00Z";
    static final UUID OPERATOR = UUID.randomUUID();
    static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());
    static final RouteApprover APPROVER = new RouteApprover(UUID.randomUUID(), true);

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
        RoutingCase duplicate = open(transportRequestId, "2083-10-06T02:00:01Z");

        assertThatThrownBy(() -> repository.save(duplicate)).isInstanceOf(DuplicateRoutingCaseException.class);
        assertThat(repository.existsForTransportRequestVersion(transportRequestId, 1))
                .isTrue();
    }

    @Test
    void 候補を算出した案件を更新すると候補と区間と除外理由が読み出せ再算出で置き換わる() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.calculateCandidates(
                voyages(),
                rules(),
                JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));

        repository.update(loaded, OPERATOR);

        RoutingCase found = repository.findByNumber(routingCase.number()).orElseThrow();
        assertThat(found.routeVersion()).isEqualTo(loaded.routeVersion());
        assertThat(found.routeVersion().candidates())
                .flatExtracting(candidate -> candidate.evaluation().reasons())
                .extracting(ExclusionReason::code)
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
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(found, OPERATOR);

        assertThat(repository.findByNumber(routingCase.number()).orElseThrow().routeVersion())
                .isEqualTo(found.routeVersion());
    }

    @Test
    void 情報不足の候補は理由と情報不足の印を保存して読み出せ空白を含む航海番号も読み戻せる() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        Voyage stale = new Voyage(
                "IT STALE 1",
                List.of(call(TOKYO, null, "2083-10-09T00:00:00Z"), call(ROTTERDAM, "2083-10-31T00:00:00Z", null)),
                "IT STALE 1@4",
                at("2083-10-05T00:00:00Z"));
        loaded.calculateCandidates(
                List.of(voyages().getFirst(), stale, voyages().getLast()),
                rules(),
                JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));

        repository.update(loaded, OPERATOR);

        RoutingCase found = repository.findByNumber(routingCase.number()).orElseThrow();
        assertThat(found.routeVersion()).isEqualTo(loaded.routeVersion());
        assertThat(found.routeVersion().candidates().get(1).evaluation().reasons())
                .containsExactly(ExclusionReason.infoInsufficient(
                        "IT STALE 1", at("2083-10-05T00:00:00Z"), Duration.ofHours(24), "IT STALE 1@4"));
        assertThat(jdbc.queryForList(
                        "SELECT info_insufficient FROM routing.route_candidate WHERE routing_case_id = ?"
                                + " ORDER BY candidate_no",
                        Boolean.class,
                        routingCase.id().value()))
                .containsExactly(false, true, false);
        assertThat(jdbc.queryForObject(
                        "SELECT threshold FROM routing.exclusion_reason WHERE routing_case_id = ? AND candidate_no = 2",
                        String.class,
                        routingCase.id().value()))
                .isEqualTo("IT STALE 1 PT24H");
    }

    @Test
    void 読み込んだ後に更新された案件は更新できない() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase first = repository.findByNumber(routingCase.number()).orElseThrow();
        RoutingCase second = repository.findByNumber(routingCase.number()).orElseThrow();
        first.calculateCandidates(
                voyages(),
                rules(),
                JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(first, OPERATOR);
        second.calculateCandidates(
                voyages(),
                rules(),
                JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));

        assertThatThrownBy(() -> repository.update(second, OPERATOR))
                .isInstanceOf(ConcurrentRoutingCaseUpdateException.class);
    }

    @Test
    void 案件一覧は見積有効期限の近い順で同じなら依頼の古い順で期限のない案件は後ろ() {
        RoutingCase later = open(UUID.randomUUID(), "2083-10-06T01:00:00Z", "2083-10-09T09:00:00Z");
        RoutingCase soonNewer = open(UUID.randomUUID(), "2083-10-06T03:00:00Z", "2083-10-08T09:00:00Z");
        RoutingCase soonOlder = open(UUID.randomUUID(), "2083-10-06T02:00:00Z", "2083-10-08T09:00:00Z");
        repository.save(later);
        repository.save(soonNewer);
        repository.save(soonOlder);
        RoutingCase withoutExpiry = open(UUID.randomUUID(), "2083-10-06T00:00:00Z", "2083-10-10T09:00:00Z");
        repository.save(withoutExpiry);
        jdbc.update(
                "UPDATE routing.routing_case SET quotation_expires_at = NULL WHERE id = ?",
                withoutExpiry.id().value());

        List<RoutingCaseSummary> summaries = repository.findSummaries(Set.of(SHIPPER)).stream()
                .filter(summary -> summary.number().year() == 2083)
                .toList();

        assertThat(summaries)
                .extracting(RoutingCaseSummary::number)
                .containsSubsequence(soonOlder.number(), soonNewer.number(), later.number(), withoutExpiry.number());
        assertThat(summaries)
                .filteredOn(summary -> summary.number().equals(soonOlder.number()))
                .singleElement()
                .isEqualTo(new RoutingCaseSummary(
                        soonOlder.number(),
                        "TR-2083-0001",
                        1,
                        TOKYO,
                        ROTTERDAM,
                        DEADLINE,
                        soonOlder.requestedAt(),
                        RouteVersionStatus.DRAFT,
                        at("2083-10-08T09:00:00Z")));
        assertThat(summaries)
                .filteredOn(summary -> summary.number().equals(withoutExpiry.number()))
                .singleElement()
                .satisfies(summary -> assertThat(summary.expiresAt()).isEmpty());
    }

    @Test
    void 案件一覧は荷主企業の集合の案件だけを返し空の集合なら何も返さない() {
        // 行 1a・1b（社内の一覧は担当企業の集合で絞る。Bolt 38g）
        CompanyId other = new CompanyId(UUID.randomUUID());
        CompanyId third = new CompanyId(UUID.randomUUID());
        RoutingCase ofShipper = open(UUID.randomUUID(), "2083-10-06T01:00:00Z", EXPIRES_AT, SHIPPER);
        RoutingCase ofOther = open(UUID.randomUUID(), "2083-10-06T02:00:00Z", EXPIRES_AT, other);
        RoutingCase ofThird = open(UUID.randomUUID(), "2083-10-06T03:00:00Z", EXPIRES_AT, third);
        repository.save(ofShipper);
        repository.save(ofOther);
        repository.save(ofThird);

        assertThat(repository.findSummaries(Set.of(SHIPPER, other)))
                .extracting(RoutingCaseSummary::number)
                .contains(ofShipper.number(), ofOther.number())
                .doesNotContain(ofThird.number());
        assertThat(repository.findSummaries(Set.of(new CompanyId(UUID.randomUUID()))))
                .isEmpty();
        assertThat(repository.findSummaries(Set.of())).isEmpty();
    }

    @Test
    void 依頼の見積有効期限と依頼者と荷主企業を保存して読み出せる() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");

        repository.save(routingCase);

        RoutingCase found = repository.findByNumber(routingCase.number()).orElseThrow();
        assertThat(found.quotationExpiresAt()).contains(at(EXPIRES_AT));
        assertThat(found.requestedBy()).isEqualTo(routingCase.requestedBy());
        assertThat(found.shipperCompanyId()).isEqualTo(SHIPPER);
        assertThat(jdbc.queryForObject(
                        "SELECT shipper_company_id FROM routing.routing_case WHERE id = ?",
                        UUID.class,
                        routingCase.id().value()))
                .isEqualTo(SHIPPER.value());
        assertThat(jdbc.queryForObject(
                        "SELECT created_by FROM routing.routing_case WHERE id = ?",
                        UUID.class,
                        routingCase.id().value()))
                .isEqualTo(routingCase.requestedBy().orElseThrow());
    }

    @Test
    void 確定した案件を更新すると確定の記録と操作者が読み出せる() {
        RoutingCase routingCase = calculatedAndSaved();
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        UtcInstant commitAt = at("2083-10-07T04:00:00Z");
        loaded.confirm(
                1,
                "直行で期限まで 3 日ある。",
                APPROVER,
                voyages(),
                rules(),
                commitAt,
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));

        repository.update(loaded, APPROVER.userId());

        RoutingCase found = repository.findByNumber(routingCase.number()).orElseThrow();
        assertThat(found.routeVersions()).isEqualTo(loaded.routeVersions());
        assertThat(found.routeVersion().confirmation())
                .contains(
                        new RouteConfirmation(1, new DecisionRationale("直行で期限まで 3 日ある。"), APPROVER.userId(), commitAt));
        assertThat(found.confirmedRouteVersion())
                .map(RouteVersion::routeVersionNo)
                .contains(1);
        // Bolt 37d: 確定した経路版の番号は案件のヘッダに持たない（経路版の状態で表す）
        assertThat(jdbc.queryForObject(
                        "SELECT updated_by FROM routing.routing_case WHERE id = ?",
                        UUID.class,
                        routingCase.id().value()))
                .isEqualTo(APPROVER.userId());
    }

    @Test
    void 確定した経路版の候補は案件をもう一度更新しても変わらない() {
        RoutingCase routingCase = calculatedAndSaved();
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.confirm(
                1,
                "根拠",
                APPROVER,
                voyages(),
                rules(),
                at("2083-10-07T04:00:00Z"),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(loaded, APPROVER.userId());
        RoutingCase confirmed = repository.findByNumber(routingCase.number()).orElseThrow();

        repository.update(confirmed, OPERATOR);

        assertThat(repository.findByNumber(routingCase.number()).orElseThrow().routeVersions())
                .isEqualTo(confirmed.routeVersions());
    }

    @Test
    void 確定した経路版の根拠と候補を書き換えた集約で更新しても表は変わらない() {
        RoutingCase routingCase = calculatedAndSaved();
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.confirm(
                1,
                "元の根拠",
                APPROVER,
                voyages(),
                rules(),
                at("2083-10-07T04:00:00Z"),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(loaded, APPROVER.userId());
        RoutingCase confirmed = repository.findByNumber(routingCase.number()).orElseThrow();
        RouteVersion original = confirmed.routeVersion();
        RouteVersion tampered = new RouteVersion(
                1,
                RouteVersionStatus.CONFIRMED,
                original.candidates().subList(0, 1),
                original.candidatesEvaluatedAt(),
                original.candidatesFound(),
                new RouteConfirmation(1, new DecisionRationale("書き換えた根拠"), OPERATOR, at("2083-10-07T05:00:00Z")));

        repository.update(withVersions(confirmed, List.of(tampered)), OPERATOR);

        assertThat(repository.findByNumber(routingCase.number()).orElseThrow().routeVersions())
                .containsExactly(original);
    }

    @Test
    void 経路版の一覧を版ごとに組み立て一覧は最新の経路版の状態を示す() {
        RoutingCase routingCase = calculatedAndSaved();
        UUID id = routingCase.id().value();
        // 版 1 を旧版（確定の記録あり）にし、版 2 を候補提示済みで足す（US-08 の再設計の後の形）
        jdbc.update(
                "UPDATE routing.route_version SET status = 'SUPERSEDED', selected_candidate_no = 1, rationale = '旧い根拠',"
                        + " approved_by = ?, approved_at = TIMESTAMP WITH TIME ZONE '2083-10-07 04:00:00+00'"
                        + " WHERE routing_case_id = ?",
                OPERATOR,
                id);
        jdbc.update(
                "INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_evaluated_at,"
                        + " candidates_found, created_at) VALUES (?, 2, 'CANDIDATES_PRESENTED',"
                        + " TIMESTAMP WITH TIME ZONE '2083-10-08 00:00:00+00', 0, CURRENT_TIMESTAMP)",
                id);

        RoutingCase found = repository.findByNumber(routingCase.number()).orElseThrow();

        assertThat(found.routeVersions())
                .extracting(RouteVersion::routeVersionNo, RouteVersion::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, RouteVersionStatus.SUPERSEDED),
                        org.assertj.core.groups.Tuple.tuple(2, RouteVersionStatus.CANDIDATES_PRESENTED));
        assertThat(found.routeVersions().getFirst().candidates())
                .hasSize(routingCase.routeVersion().candidates().size());
        assertThat(found.routeVersions().getLast().candidates()).isEmpty();
        assertThat(repository.findSummaries(Set.of(SHIPPER)))
                .filteredOn(summary -> summary.number().equals(routingCase.number()))
                .singleElement()
                .satisfies(summary -> assertThat(summary.status()).isEqualTo(RouteVersionStatus.CANDIDATES_PRESENTED));
    }

    @Test
    void 判断根拠は4000文字をコードポイントで保存して読み出せる() {
        RoutingCase routingCase = calculatedAndSaved();
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        String rationale = "\uD867\uDE3D".repeat(DecisionRationale.MAX_LENGTH);
        loaded.confirm(
                1,
                rationale,
                APPROVER,
                voyages(),
                rules(),
                at("2083-10-07T04:00:00Z"),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));

        repository.update(loaded, APPROVER.userId());

        assertThat(repository
                        .findByNumber(routingCase.number())
                        .orElseThrow()
                        .routeVersion()
                        .confirmation())
                .map(confirmation -> confirmation.rationale().text())
                .contains(rationale);
    }

    @Test
    void 確定した候補は消せない() {
        RoutingCase routingCase = calculatedAndSaved();
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.confirm(
                1,
                "根拠",
                APPROVER,
                voyages(),
                rules(),
                at("2083-10-07T04:00:00Z"),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(loaded, APPROVER.userId());
        UUID id = routingCase.id().value();
        jdbc.update("DELETE FROM routing.exclusion_reason WHERE routing_case_id = ? AND candidate_no = 1", id);
        jdbc.update("DELETE FROM routing.candidate_leg WHERE routing_case_id = ? AND candidate_no = 1", id);

        assertThatThrownBy(() -> jdbc.update(
                        "DELETE FROM routing.route_candidate WHERE routing_case_id = ? AND candidate_no = 1", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 確定の記録と再設計要の記録の表の守り（CHECK `ck_route_version_confirmed`。Bolt 19。CHECK `ck_route_version_redesign`。
     * Bolt 37d）。PostgreSQL は制約違反でトランザクションを中断するため、1 つの例に 1 つの違反だけを書く。
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "UPDATE routing.route_version SET rationale = '根拠' WHERE routing_case_id = ?",
                "UPDATE routing.route_version SET status = 'CONFIRMED' WHERE routing_case_id = ?",
                "UPDATE routing.route_version SET redesign_required_at = CURRENT_TIMESTAMP,"
                        + " redesign_reasons = 'DEADLINE_EXCEEDED' WHERE routing_case_id = ?",
                "UPDATE routing.route_version SET status = 'REDESIGN_REQUIRED' WHERE routing_case_id = ?",
                "UPDATE routing.route_version SET status = 'SUPERSEDED', redesign_required_at = CURRENT_TIMESTAMP"
                        + " WHERE routing_case_id = ?",
                "UPDATE routing.route_version SET status = 'REDESIGN_REQUIRED', redesign_required_at = CURRENT_TIMESTAMP,"
                        + " redesign_reasons = 'DEADLINE_EXCEEDED' WHERE routing_case_id = ?"
            })
    void 確定の記録と再設計要の記録は表が守る(String violation) {
        UUID id = calculatedAndSaved().id().value();

        assertThatThrownBy(() -> jdbc.update(violation, id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 確定した経路版を再設計要にして更新すると状態と記録だけが変わり候補と確定の記録が残る() {
        RoutingCase confirmed = confirmedAndSaved();
        RoutingCase loaded = repository.findByNumber(confirmed.number()).orElseThrow();
        RouteVersion version = loaded.routeVersion();
        RedesignRecord redesign = new RedesignRecord(
                at("2083-10-08T00:00:00Z"),
                List.of(RedesignReasonCode.CONNECTION_TOO_SHORT, RedesignReasonCode.DEADLINE_EXCEEDED));
        RoutingCase redesigned = RoutingCaseFixture.withRouteVersions(
                loaded,
                List.of(new RouteVersion(
                        version.routeVersionNo(),
                        RouteVersionStatus.REDESIGN_REQUIRED,
                        version.candidates(),
                        version.evaluatedAt().orElseThrow(),
                        version.candidatesFound(),
                        version.confirmation().orElseThrow(),
                        redesign)));

        repository.update(redesigned, OPERATOR);

        RoutingCase found = repository.findByNumber(confirmed.number()).orElseThrow();
        assertThat(found.routeVersions()).isEqualTo(redesigned.routeVersions());
        assertThat(found.routeVersion().status()).isEqualTo(RouteVersionStatus.REDESIGN_REQUIRED);
        assertThat(found.routeVersion().redesign()).contains(redesign);
        assertThat(found.routeVersion().candidates()).isEqualTo(version.candidates());
        assertThat(found.confirmedCandidateOf(1)).isEqualTo(loaded.confirmedCandidateOf(1));
        assertThat(jdbc.queryForObject(
                        "SELECT redesign_reasons FROM routing.route_version WHERE routing_case_id = ?",
                        String.class,
                        confirmed.id().value()))
                .isEqualTo("CONNECTION_TOO_SHORT DEADLINE_EXCEEDED");
    }

    /** 確定の航海の照会、寄港の位置、システムの更新（Bolt 39f の行 1〜3・29）。 */
    @Nested
    class Bolt39f_照会と寄港の位置とシステムの更新 {

        @Autowired
        SqlSessionTemplate sqlSession;

        /** 積替え 1 回の航海（候補は IT-39F-FEED → IT-39F-MAIN の 1 つ）。 */
        private static List<Voyage> transshipment() {
            return List.of(
                    voyage(
                            "IT-39F-FEED",
                            call(TOKYO, null, "2083-10-10T00:00:00Z"),
                            call(SINGAPORE, "2083-10-20T00:00:00Z", null)),
                    voyage(
                            "IT-39F-MAIN",
                            call(SINGAPORE, null, "2083-10-21T00:00:00Z"),
                            call(ROTTERDAM, "2083-10-30T00:00:00Z", null)));
        }

        private RoutingCase calculatedOn(List<Voyage> voyages) {
            RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
            repository.save(routingCase);
            RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
            loaded.calculateCandidates(
                    voyages,
                    rules(),
                    JUDGED_AT,
                    new RouteCandidateFinder(),
                    new ConstraintEvaluator(),
                    new InfoFreshnessPolicy(Duration.ofHours(24)));
            repository.update(loaded, OPERATOR);
            return repository.findByNumber(routingCase.number()).orElseThrow();
        }

        private RoutingCase confirmedOn(List<Voyage> voyages) {
            RoutingCase loaded = calculatedOn(voyages);
            loaded.confirm(
                    1,
                    "積替えの余裕がある。",
                    APPROVER,
                    voyages,
                    rules(),
                    at("2083-10-07T04:00:00Z"),
                    new ConstraintEvaluator(),
                    new InfoFreshnessPolicy(Duration.ofHours(24)));
            repository.update(loaded, APPROVER.userId());
            return repository.findByNumber(loaded.number()).orElseThrow();
        }

        private RoutingCase redesigned(RoutingCase loaded) {
            RouteVersion version = loaded.routeVersion();
            return RoutingCaseFixture.withRouteVersions(
                    loaded,
                    List.of(new RouteVersion(
                            version.routeVersionNo(),
                            RouteVersionStatus.REDESIGN_REQUIRED,
                            version.candidates(),
                            version.evaluatedAt().orElseThrow(),
                            version.candidatesFound(),
                            version.confirmation().orElseThrow(),
                            new RedesignRecord(
                                    at("2083-10-08T00:00:00Z"), List.of(RedesignReasonCode.VOYAGE_UNAVAILABLE)))));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"1 区間目の航海, IT-39F-FEED", "2 区間目の航海, IT-39F-MAIN"})
        void 行1_確定の候補の区間にその航海がある案件を返す(String description, String voyageNumber) {
            RoutingCase confirmed = confirmedOn(transshipment());

            assertThat(repository.findCaseNumbersConfirmedOnVoyage(voyageNumber))
                    .as(description)
                    .containsExactly(confirmed.number());
        }

        @Test
        void 行1_確定でない候補の区間だけにある航海では返さない() {
            confirmedAndSaved();

            // IT-DIRECT の候補を確定した案件の、確定していない候補の区間の航海
            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-LATE")).isEmpty();
            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-DIRECT")).hasSize(1);
        }

        @Test
        void 行1_候補提示済みの案件は返さない() {
            calculatedAndSaved();

            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-DIRECT")).isEmpty();
        }

        @Test
        void 行1_再設計要の案件は返さない() {
            RoutingCase loaded = confirmedOn(transshipment());
            repository.updateBySystem(redesigned(loaded));

            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-39F-FEED"))
                    .isEmpty();
        }

        @Test
        void 行1_最新でない経路版が確定でも返さない() {
            RoutingCase confirmed = confirmedOn(transshipment());
            jdbc.update(
                    "INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_found, created_at)"
                            + " VALUES (?, 2, 'DRAFT', 0, CURRENT_TIMESTAMP)",
                    confirmed.id().value());
            sqlSession.clearCache();

            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-39F-FEED"))
                    .isEmpty();
        }

        @Test
        void 行1_最新の経路版が確定でも旧版の区間にだけある航海では返さない() {
            // 再設計（US-08）の形を表に直接作る: 経路版 1 は再設計要、経路版 2 の同じ候補番号を確定して別の航海に載せる
            RoutingCase loaded = confirmedOn(transshipment());
            repository.updateBySystem(redesigned(loaded));
            UUID caseId = loaded.id().value();
            jdbc.update(
                    "INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_found, created_at)"
                            + " VALUES (?, 2, 'DRAFT', 1, CURRENT_TIMESTAMP)",
                    caseId);
            jdbc.update(
                    "INSERT INTO routing.route_candidate (routing_case_id, route_version_no, candidate_no, conforming,"
                            + " estimated_arrival_at, min_connection_slack_minutes, evaluated_at, oldest_info_acquired_at, info_insufficient)"
                            + " SELECT routing_case_id, 2, candidate_no, conforming, estimated_arrival_at, min_connection_slack_minutes,"
                            + " evaluated_at, oldest_info_acquired_at, info_insufficient"
                            + " FROM routing.route_candidate WHERE routing_case_id = ? AND route_version_no = 1 AND candidate_no = 1",
                    caseId);
            jdbc.update(
                    "INSERT INTO routing.candidate_leg (routing_case_id, route_version_no, candidate_no, leg_no, voyage_number,"
                            + " load_unlocode, discharge_unlocode, departure_at, arrival_at, info_version, info_acquired_at, executed)"
                            + " SELECT routing_case_id, 2, candidate_no, leg_no, 'IT-39F-NEW', load_unlocode, discharge_unlocode,"
                            + " departure_at, arrival_at, info_version, info_acquired_at, executed"
                            + " FROM routing.candidate_leg WHERE routing_case_id = ? AND route_version_no = 1 AND candidate_no = 1",
                    caseId);
            jdbc.update(
                    "UPDATE routing.route_version SET status = 'CONFIRMED', candidates_evaluated_at = CURRENT_TIMESTAMP,"
                            + " selected_candidate_no = 1, rationale = '再設計の根拠。', approved_by = ?, approved_at = CURRENT_TIMESTAMP"
                            + " WHERE routing_case_id = ? AND route_version_no = 2",
                    APPROVER.userId(),
                    caseId);
            sqlSession.clearCache();

            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-39F-FEED"))
                    .isEmpty();
            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-39F-NEW"))
                    .containsExactly(loaded.number());
        }

        @Test
        void 行1_ほかの航海では返さない() {
            confirmedOn(transshipment());

            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-39F-OTHER"))
                    .isEmpty();
        }

        @Test
        void 行1_確定の候補の2つの区間に同じ航海があっても案件は1件() {
            RoutingCase confirmed = confirmedOn(transshipment());
            jdbc.update(
                    "INSERT INTO routing.candidate_leg (routing_case_id, route_version_no, candidate_no, leg_no, voyage_number,"
                            + " load_unlocode, discharge_unlocode, departure_at, arrival_at, info_version, info_acquired_at, executed)"
                            + " SELECT routing_case_id, route_version_no, candidate_no, 3, voyage_number, load_unlocode,"
                            + " discharge_unlocode, departure_at, arrival_at, info_version, info_acquired_at, executed"
                            + " FROM routing.candidate_leg WHERE routing_case_id = ? AND leg_no = 1",
                    confirmed.id().value());

            assertThat(repository.findCaseNumbersConfirmedOnVoyage("IT-39F-FEED"))
                    .containsExactly(confirmed.number());
        }

        @Test
        void 行2_区間の航海番号の索引がある() {
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'routing' AND tablename = 'candidate_leg'"
                                    + " AND indexname = 'ix_candidate_leg_voyage'",
                            Integer.class))
                    .isEqualTo(1);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"load_call_no", "discharge_call_no"})
        void 行2_寄港の位置の列に日本語のコメントがある(String column) {
            assertThat(jdbc.queryForObject(
                            "SELECT col_description('routing.candidate_leg'::regclass, attnum) FROM pg_attribute"
                                    + " WHERE attrelid = 'routing.candidate_leg'::regclass AND attname = ?",
                            String.class,
                            column))
                    .contains("寄港の位置");
        }

        @ParameterizedTest(name = "積地 {0}・揚地 {1}")
        @CsvSource(
                nullValues = "null",
                value = {"1, null", "null, 2", "2, 2", "3, 2"})
        void 行2_片方だけの位置と積地が揚地より後の位置はCHECKで拒否する(Integer loadCallNo, Integer dischargeCallNo) {
            RoutingCase calculated = calculatedAndSaved();
            SqlParameterValue load = new SqlParameterValue(Types.INTEGER, loadCallNo);
            SqlParameterValue discharge = new SqlParameterValue(Types.INTEGER, dischargeCallNo);
            UUID id = calculated.id().value();

            assertThatThrownBy(() -> jdbc.update(
                            "UPDATE routing.candidate_leg SET load_call_no = ?, discharge_call_no = ? WHERE routing_case_id = ?",
                            load,
                            discharge,
                            id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 行3_候補の算出で書いた寄港の位置を読み直して同じ値になる() {
            RoutingCase calculated = calculatedOn(List.of(voyage(
                    "IT-39F-THREE",
                    call(HONG_KONG, null, "2083-10-08T00:00:00Z"),
                    call(TOKYO, "2083-10-09T00:00:00Z", "2083-10-10T00:00:00Z"),
                    call(ROTTERDAM, "2083-10-30T00:00:00Z", null))));

            assertThat(calculated.routeVersion().candidates().getFirst().legs())
                    .singleElement()
                    .satisfies(leg -> {
                        assertThat(leg.loadCallNo()).isEqualTo(2);
                        assertThat(leg.dischargeCallNo()).isEqualTo(3);
                    });
        }

        @Test
        void 行3_Bolt39fより前の行のNULLはNULLで読む() {
            RoutingCase calculated = calculatedAndSaved();
            jdbc.update(
                    "UPDATE routing.candidate_leg SET load_call_no = NULL, discharge_call_no = NULL WHERE routing_case_id = ?",
                    calculated.id().value());
            sqlSession.clearCache();

            RoutingCase found = repository.findByNumber(calculated.number()).orElseThrow();

            assertThat(found.routeVersion().candidates())
                    .flatExtracting(RouteCandidate::legs)
                    .isNotEmpty()
                    .allSatisfy(leg -> {
                        assertThat(leg.loadCallNo()).isNull();
                        assertThat(leg.dischargeCallNo()).isNull();
                    });
        }

        @Test
        void 行29_システムの更新は案件の版を上げ最終更新者を変えない() {
            // 確定した経路の評価は外部情報の更新を受けたシステムの操作なので、最後に確定した経路設計者を残す（Bolt 37d2）
            RoutingCase confirmed = confirmedAndSaved();
            RoutingCase loaded = repository.findByNumber(confirmed.number()).orElseThrow();

            repository.updateBySystem(redesigned(loaded));

            RoutingCase found = repository.findByNumber(confirmed.number()).orElseThrow();
            assertThat(found.aggregateVersion()).isEqualTo(loaded.aggregateVersion() + 1);
            assertThat(found.routeVersion().status()).isEqualTo(RouteVersionStatus.REDESIGN_REQUIRED);
            assertThat(jdbc.queryForObject(
                            "SELECT updated_by FROM routing.routing_case WHERE id = ?",
                            UUID.class,
                            confirmed.id().value()))
                    .isEqualTo(APPROVER.userId());
        }

        @Test
        void 行29_updateに操作者nullを渡すと例外で案件の版を進めない() {
            RoutingCase confirmed = confirmedAndSaved();
            RoutingCase loaded = repository.findByNumber(confirmed.number()).orElseThrow();
            long versionBefore = loaded.aggregateVersion();

            assertThatThrownBy(() -> repository.update(loaded, null)).isInstanceOf(NullPointerException.class);

            assertThat(jdbc.queryForObject(
                            "SELECT version FROM routing.routing_case WHERE id = ?",
                            Long.class,
                            confirmed.id().value()))
                    .isEqualTo(versionBefore);
        }
    }

    @Test
    void 再設計要への更新は確定の行だけを書き確定していない行には書けない() {
        // 表の条件は最後の守り（集約は確定の経路版だけを再設計要にする。Bolt 37d2）。書けなければ例外にする（Bolt 37d の開発レビュー）
        RoutingCase calculated = calculatedAndSaved();
        RoutingCase loaded = repository.findByNumber(calculated.number()).orElseThrow();
        RouteVersion version = loaded.routeVersion();
        RoutingCase redesigned = RoutingCaseFixture.withRouteVersions(
                loaded,
                List.of(new RouteVersion(
                        version.routeVersionNo(),
                        RouteVersionStatus.REDESIGN_REQUIRED,
                        version.candidates(),
                        version.evaluatedAt().orElseThrow(),
                        version.candidatesFound(),
                        new RouteConfirmation(
                                1,
                                new DecisionRationale("直行で期限まで 3 日ある。"),
                                APPROVER.userId(),
                                at("2083-10-07T04:00:00Z")),
                        new RedesignRecord(
                                at("2083-10-08T00:00:00Z"), List.of(RedesignReasonCode.VOYAGE_UNAVAILABLE)))));

        assertThatThrownBy(() -> repository.update(redesigned, OPERATOR)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM routing.route_version WHERE routing_case_id = ?",
                        String.class,
                        calculated.id().value()))
                .isEqualTo("CANDIDATES_PRESENTED");
    }

    @Test
    void 同じ案件に確定の経路版を二つ持つ行は部分一意インデックスが拒む() {
        // 確定は案件に 1 つ（R-INV-05。Bolt 37d で confirmed_route_version_no の代わりに PostgreSQL の部分一意インデックスで守る）
        UUID id = confirmedAndSaved().id().value();
        jdbc.update(
                "INSERT INTO routing.route_version (routing_case_id, route_version_no, status, candidates_evaluated_at,"
                        + " candidates_found, created_at) SELECT routing_case_id, 2, 'CANDIDATES_PRESENTED',"
                        + " candidates_evaluated_at, candidates_found, created_at FROM routing.route_version"
                        + " WHERE routing_case_id = ? AND route_version_no = 1",
                id);
        jdbc.update(
                "INSERT INTO routing.route_candidate (routing_case_id, route_version_no, candidate_no, conforming,"
                        + " estimated_arrival_at, min_connection_slack_minutes, evaluated_at, oldest_info_acquired_at,"
                        + " info_insufficient) SELECT routing_case_id, 2, candidate_no, conforming, estimated_arrival_at,"
                        + " min_connection_slack_minutes, evaluated_at, oldest_info_acquired_at, info_insufficient"
                        + " FROM routing.route_candidate WHERE routing_case_id = ? AND route_version_no = 1",
                id);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE routing.route_version v2 SET status = 'CONFIRMED',"
                                + " selected_candidate_no = v1.selected_candidate_no, rationale = v1.rationale,"
                                + " approved_by = v1.approved_by, approved_at = v1.approved_at"
                                + " FROM routing.route_version v1 WHERE v2.routing_case_id = ? AND v2.route_version_no = 2"
                                + " AND v1.routing_case_id = v2.routing_case_id AND v1.route_version_no = 1",
                        id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_route_version_confirmed");
    }

    @Test
    void 見つけた候補の数を残し上限で示さなかった候補の数が読み出せる() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        List<Voyage> voyages = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            voyages.add(voyage(
                    "IT-MANY-%02d".formatted(i),
                    call(TOKYO, null, "2083-10-08T00:00:00Z"),
                    call(ROTTERDAM, "2083-10-%02dT00:00:00Z".formatted(10 + i), null)));
        }
        loaded.calculateCandidates(
                voyages,
                rules(),
                JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));

        repository.update(loaded, OPERATOR);

        RouteVersion found =
                repository.findByNumber(routingCase.number()).orElseThrow().routeVersion();
        assertThat(found.candidatesFound()).isEqualTo(21);
        assertThat(found.omittedCandidates()).isEqualTo(1);
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

        assertThat(voyageRepository.findByVoyageNumbers(List.of("IT-001")))
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

        UUID id = routingCase.id().value();

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE routing.route_version SET status = 'UNKNOWN' WHERE routing_case_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 除外理由の区分はデータモデルの値だけを受け付ける() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.calculateCandidates(
                voyages(),
                rules(),
                JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(loaded, OPERATOR);

        UUID id = routingCase.id().value();

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE routing.exclusion_reason SET reason_code = 'UNKNOWN' WHERE routing_case_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 案件番号は年ごとに1つずつ振る() {
        RoutingCaseNumber first = numberIssuer.next(2083);
        RoutingCaseNumber second = numberIssuer.next(2083);

        assertThat(second.sequence()).isEqualTo(first.sequence() + 1);
        assertThat(second.year()).isEqualTo(2083);
    }

    private static RoutingCase withVersions(RoutingCase source, List<RouteVersion> versions) {
        return RoutingCase.reconstitute(
                source.id(),
                source.number(),
                source.transportRequestId(),
                source.transportRequestNumber(),
                source.transportRequestVersionNo(),
                source.quotationId(),
                source.routePolicyVia(),
                source.specification(),
                source.requestedAt(),
                source.quotationExpiresAt().orElse(null),
                source.requestedBy().orElse(null),
                source.shipperCompanyId(),
                versions,
                source.aggregateVersion());
    }

    /** 候補 1 を確定して保存した案件（Bolt 37d）。 */
    private RoutingCase confirmedAndSaved() {
        RoutingCase routingCase = calculatedAndSaved();
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.confirm(
                1,
                "直行で期限まで 3 日ある。",
                APPROVER,
                voyages(),
                rules(),
                at("2083-10-07T04:00:00Z"),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(loaded, APPROVER.userId());
        return loaded;
    }

    private RoutingCase calculatedAndSaved() {
        RoutingCase routingCase = open(UUID.randomUUID(), "2083-10-06T02:00:00Z");
        repository.save(routingCase);
        RoutingCase loaded = repository.findByNumber(routingCase.number()).orElseThrow();
        loaded.calculateCandidates(
                voyages(),
                rules(),
                JUDGED_AT,
                new RouteCandidateFinder(),
                new ConstraintEvaluator(),
                new InfoFreshnessPolicy(Duration.ofHours(24)));
        repository.update(loaded, OPERATOR);
        return loaded;
    }

    private RoutingCase open(UUID transportRequestId, String requestedAt) {
        return open(transportRequestId, requestedAt, EXPIRES_AT);
    }

    private RoutingCase open(UUID transportRequestId, String requestedAt, String expiresAt) {
        return open(transportRequestId, requestedAt, expiresAt, SHIPPER);
    }

    private RoutingCase open(UUID transportRequestId, String requestedAt, String expiresAt, CompanyId shipper) {
        return RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                numberIssuer.next(2083),
                transportRequestId,
                "TR-2083-0001",
                1,
                UUID.randomUUID(),
                List.of(SINGAPORE),
                new RouteSpecification(TOKYO, ROTTERDAM, DEADLINE, "GENERAL"),
                at(requestedAt),
                at(expiresAt),
                UUID.randomUUID(),
                shipper);
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
        return new Voyage(number, List.of(calls), number + "@1", at("2083-10-07T01:00:00Z"));
    }

    static PortCall call(Location port, String arrival, String departure) {
        return new PortCall(port, arrival == null ? null : at(arrival), departure == null ? null : at(departure));
    }

    static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

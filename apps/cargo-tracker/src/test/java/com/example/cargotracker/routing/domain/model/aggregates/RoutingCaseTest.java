package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 経路設計案件（R-INV-10、US-06 AC1。Bolt 17）。情報不足（US-06 AC4、R-INV-09。Bolt 37b2）。
 */
class RoutingCaseTest {

    static final Location TOKYO = new Location("JPTYO");
    static final Location SINGAPORE = new Location("SGSIN");
    static final Location ROTTERDAM = new Location("NLRTM");
    static final UtcInstant DEADLINE = at("2026-11-02T00:00:00Z");
    static final UtcInstant JUDGED_AT = at("2026-10-07T03:00:00Z");
    static final UtcInstant QUOTATION_EXPIRES_AT = at("2026-10-08T09:00:00Z");
    static final UUID REQUESTED_BY = UUID.randomUUID();
    static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());
    static final List<ConnectionRule> RULES = List.of(
            new ConnectionRule(UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null));

    private final RouteCandidateFinder finder = new RouteCandidateFinder();
    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();
    private final InfoFreshnessPolicy freshness = new InfoFreshnessPolicy(Duration.ofHours(24));

    @Test
    void 依頼を受けて作った案件は経路版1を作成中で持つ() {
        RoutingCase routingCase = open();

        assertThat(routingCase.routeVersion().routeVersionNo()).isEqualTo(1);
        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.DRAFT);
        assertThat(routingCase.routeVersion().candidates()).isEmpty();
        assertThat(routingCase.routeVersion().evaluatedAt()).isEmpty();
        assertThat(routingCase.shipperCompanyId()).isEqualTo(SHIPPER);
    }

    @Test
    void 荷主企業のない案件は作れない() {
        // 行 6c（荷主企業は担当範囲の判定に使うので null を許さない。Bolt 38g）
        RouteSpecification specification =
                new RouteSpecification(TOKYO, ROTTERDAM, DEADLINE, RouteCargoCategory.GENERAL);
        UtcInstant requestedAt = at("2026-10-06T02:00:00Z");
        RoutingCaseId id = new RoutingCaseId(UUID.randomUUID());
        RoutingCaseNumber number = new RoutingCaseNumber(2026, 1);
        UUID transportRequestId = UUID.randomUUID();
        UUID quotationId = UUID.randomUUID();
        List<Location> routePolicyVia = List.of(SINGAPORE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RoutingCase.open(
                        id,
                        number,
                        transportRequestId,
                        "TR-2026-0001",
                        1,
                        quotationId,
                        routePolicyVia,
                        specification,
                        requestedAt,
                        QUOTATION_EXPIRES_AT,
                        REQUESTED_BY,
                        null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("shipperCompanyId");
    }

    @Test
    void 候補を算出すると候補提示済みになり適合を先に到着予定の早い順で番号を振る() {
        RoutingCase routingCase = open();
        List<Voyage> voyages = List.of(
                direct("V-LATE", "2026-11-03T12:00:00Z"), // 期限超過
                direct("V-EARLY", "2026-10-29T00:00:00Z"),
                direct("V-ONTIME", "2026-11-02T00:00:00Z"));

        CandidateCalculation calculation =
                routingCase.calculateCandidates(voyages, RULES, JUDGED_AT, finder, evaluator, freshness);

        assertThat(calculation).isEqualTo(new CandidateCalculation(3, 3, 2, JUDGED_AT));
        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CANDIDATES_PRESENTED);
        assertThat(routingCase.routeVersion().evaluatedAt()).contains(JUDGED_AT);
        assertThat(routingCase.routeVersion().candidates())
                .extracting(
                        RouteCandidate::candidateNo,
                        candidate -> candidate.legs().getFirst().voyageNumber())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "V-EARLY"),
                        org.assertj.core.groups.Tuple.tuple(2, "V-ONTIME"),
                        org.assertj.core.groups.Tuple.tuple(3, "V-LATE"));
        assertThat(routingCase.routeVersion().candidates())
                .allSatisfy(candidate -> assertThat(candidate.evaluatedAt()).isEqualTo(JUDGED_AT));
    }

    @Test
    void 候補は20件までで適合を残し見つけた数を返す() {
        RoutingCase routingCase = open();
        List<Voyage> voyages = new ArrayList<>();
        // 除外の 2 本は適合より先に到着する（区間 2 で、シンガポールの接続が足りない）。それでも適合が残ることを確かめる
        // （Bolt 17 レビュー D-63。到着順だけでは除外が残る並び）
        voyages.add(feeder("V-FEED", "2026-10-09T00:00:00Z"));
        voyages.add(fromSingapore("V-SHORT-1", "2026-10-09T01:00:00Z", "2026-10-09T12:00:00Z"));
        voyages.add(fromSingapore("V-SHORT-2", "2026-10-09T02:00:00Z", "2026-10-09T13:00:00Z"));
        for (int i = 0; i < 20; i++) {
            voyages.add(direct("V-%03d".formatted(i), "2026-10-%02dT00:00:00Z".formatted(11 + i)));
        }

        CandidateCalculation calculation =
                routingCase.calculateCandidates(voyages, RULES, JUDGED_AT, finder, evaluator, freshness);

        assertThat(calculation.found()).isEqualTo(22);
        assertThat(calculation.kept()).isEqualTo(RoutingCase.MAX_CANDIDATES);
        assertThat(calculation.omitted()).isEqualTo(2);
        assertThat(routingCase.routeVersion().candidates())
                .hasSize(20)
                .allSatisfy(candidate ->
                        assertThat(candidate.evaluation().conforming()).isTrue());
    }

    @Test
    void 積替え2回の候補も見つけた数と20件の上限に入る() {
        // Bolt 37: 区間 3 の候補も区間 1〜2 と同じく数え、上限で切る（確認ポイント 3）
        Location colombo = new Location("LKCMB");
        List<ConnectionRule> rules = List.of(
                RULES.getFirst(),
                new ConnectionRule(UUID.randomUUID(), colombo, Duration.ofHours(6), at("2026-01-01T00:00:00Z"), null));
        List<Voyage> voyages = new ArrayList<>();
        voyages.add(feeder("V-FEED", "2026-10-09T00:00:00Z"));
        voyages.add(new Voyage(
                "V-MID",
                List.of(
                        new PortCall(SINGAPORE, null, at("2026-10-09T08:00:00Z")),
                        new PortCall(colombo, at("2026-10-10T00:00:00Z"), null)),
                "V-MID@1",
                at("2026-10-07T01:00:00Z")));
        for (int i = 0; i < 21; i++) {
            voyages.add(new Voyage(
                    "V-%03d".formatted(i),
                    List.of(
                            new PortCall(colombo, null, at("2026-10-10T12:00:00Z")),
                            new PortCall(ROTTERDAM, at("2026-10-%02dT00:00:00Z".formatted(11 + i)), null)),
                    "V-%03d@1".formatted(i),
                    at("2026-10-07T01:00:00Z")));
        }
        RoutingCase routingCase = open();

        CandidateCalculation calculation =
                routingCase.calculateCandidates(voyages, rules, JUDGED_AT, finder, evaluator, freshness);

        assertThat(calculation.found()).isEqualTo(21);
        assertThat(calculation.kept()).isEqualTo(RoutingCase.MAX_CANDIDATES);
        assertThat(calculation.omitted()).isEqualTo(1);
        assertThat(routingCase.routeVersion().candidates())
                .hasSize(20)
                .allSatisfy(candidate -> assertThat(candidate.legs()).hasSize(3));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "見つけた候補が {0} 件なら {1} 件を残し {2} 件を示さない")
    @org.junit.jupiter.params.provider.CsvSource({"19, 19, 0", "20, 20, 0", "21, 20, 1"})
    void 候補の上限は20件(int found, int kept, int omitted) {
        RoutingCase routingCase = open();
        List<Voyage> voyages = new ArrayList<>();
        for (int i = 0; i < found; i++) {
            voyages.add(direct("V-%03d".formatted(i), "2026-10-%02dT00:00:00Z".formatted(10 + i)));
        }

        CandidateCalculation calculation =
                routingCase.calculateCandidates(voyages, RULES, JUDGED_AT, finder, evaluator, freshness);

        assertThat(calculation.kept()).isEqualTo(kept);
        assertThat(calculation.omitted()).isEqualTo(omitted);
        assertThat(routingCase.routeVersion().candidates()).hasSize(kept);
    }

    @Test
    void 候補を算出できない状態の経路版では算出できない() {
        RoutingCase superseded = RoutingCase.reconstitute(
                new RoutingCaseId(UUID.randomUUID()),
                new RoutingCaseNumber(2026, 1),
                UUID.randomUUID(),
                "TR-2026-0001",
                1,
                UUID.randomUUID(),
                List.of(),
                new RouteSpecification(TOKYO, ROTTERDAM, DEADLINE, RouteCargoCategory.GENERAL),
                at("2026-10-06T02:00:00Z"),
                null,
                null,
                SHIPPER,
                List.of(new com.example.cargotracker.routing.domain.model.entities.RouteVersion(
                        1, RouteVersionStatus.SUPERSEDED, List.of(), JUDGED_AT)),
                3);

        List<Voyage> noVoyages = List.of();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> superseded.calculateCandidates(noVoyages, RULES, JUDGED_AT, finder, evaluator, freshness))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 再算出すると候補を置き換え判定時刻を更新する() {
        RoutingCase routingCase = open();
        routingCase.calculateCandidates(
                List.of(direct("V-OLD", "2026-10-29T00:00:00Z")), RULES, JUDGED_AT, finder, evaluator, freshness);
        UtcInstant later = at("2026-10-07T04:00:00Z");

        CandidateCalculation calculation = routingCase.calculateCandidates(
                List.of(direct("V-NEW", "2026-10-30T00:00:00Z")), RULES, later, finder, evaluator, freshness);

        assertThat(calculation).isEqualTo(new CandidateCalculation(1, 1, 1, later));
        assertThat(routingCase.routeVersion().evaluatedAt()).contains(later);
        assertThat(routingCase.routeVersion().candidates())
                .singleElement()
                .satisfies(candidate ->
                        assertThat(candidate.legs().getFirst().voyageNumber()).isEqualTo("V-NEW"));
    }

    @Test
    void 情報不足の候補は除外され適合の候補の後ろに並ぶ() {
        RoutingCase routingCase = open();
        UtcInstant stale = minusHours(JUDGED_AT, 30);
        List<Voyage> voyages =
                List.of(direct("V-STALE", "2026-10-29T00:00:00Z", stale), direct("V-FRESH", "2026-10-30T00:00:00Z"));

        CandidateCalculation calculation =
                routingCase.calculateCandidates(voyages, RULES, JUDGED_AT, finder, evaluator, freshness);

        assertThat(calculation).isEqualTo(new CandidateCalculation(2, 2, 1, JUDGED_AT));
        List<RouteCandidate> candidates = routingCase.routeVersion().candidates();
        assertThat(candidates)
                .extracting(candidate -> candidate.legs().getFirst().voyageNumber())
                .containsExactly("V-FRESH", "V-STALE");
        assertThat(candidates.get(0).evaluation().infoInsufficient()).isFalse();
        assertThat(candidates.get(1).evaluation().infoInsufficient()).isTrue();
        assertThat(candidates.get(1).evaluation().reasons())
                .containsExactly(ExclusionReason.infoInsufficient("V-STALE", stale, Duration.ofHours(24), "V-STALE@1"));
    }

    @Test
    void 情報不足の理由は制約適合判定の理由の後ろに足す() {
        RoutingCase routingCase = open();
        List<Voyage> voyages = List.of(direct("V-LATE", "2026-11-03T12:00:00Z", minusHours(JUDGED_AT, 30)));

        routingCase.calculateCandidates(voyages, RULES, JUDGED_AT, finder, evaluator, freshness);

        assertThat(routingCase
                        .routeVersion()
                        .candidates()
                        .getFirst()
                        .evaluation()
                        .reasons())
                .extracting(ExclusionReason::code)
                .containsExactly(ExclusionReasonCode.DEADLINE_EXCEEDED, ExclusionReasonCode.INFO_INSUFFICIENT);
    }

    @Test
    void 期限超過だけで除外した候補は情報不足ではない() {
        RoutingCase routingCase = open();

        routingCase.calculateCandidates(
                List.of(direct("V-LATE", "2026-11-03T12:00:00Z")), RULES, JUDGED_AT, finder, evaluator, freshness);

        RouteCandidate candidate = routingCase.routeVersion().candidates().getFirst();
        assertThat(candidate.evaluation().conforming()).isFalse();
        assertThat(candidate.evaluation().infoInsufficient()).isFalse();
    }

    @Test
    void 取得時刻の違う区間では古い航海だけに理由が出て最も古い取得時刻を示す() {
        RoutingCase routingCase = open();
        UtcInstant fresh = minusHours(JUDGED_AT, 2);
        UtcInstant stale = minusHours(JUDGED_AT, 30);
        List<Voyage> voyages = List.of(
                feeder("V-F", "2026-10-20T00:00:00Z", stale),
                fromSingapore("V-S", "2026-10-20T12:00:00Z", "2026-10-31T00:00:00Z", fresh));

        routingCase.calculateCandidates(voyages, RULES, JUDGED_AT, finder, evaluator, freshness);

        RouteCandidate candidate = routingCase.routeVersion().candidates().getFirst();
        assertThat(candidate.oldestInfoAcquiredAt()).isEqualTo(stale);
        assertThat(candidate.evaluation().reasons())
                .extracting(reason -> ((ExclusionThreshold.StaleVoyageInfo) reason.threshold()).voyageNumber())
                .containsExactly("V-F");
        assertThat(candidate.evaluation().connectionSlack()).contains(Duration.ofHours(4));
    }

    @Test
    void 航海がなければ候補0件で候補提示済みになる() {
        RoutingCase routingCase = open();

        CandidateCalculation calculation =
                routingCase.calculateCandidates(List.of(), RULES, JUDGED_AT, finder, evaluator, freshness);

        assertThat(calculation).isEqualTo(new CandidateCalculation(0, 0, 0, JUDGED_AT));
        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CANDIDATES_PRESENTED);
    }

    /** 貨物種別で除外する（BR-02、BR-03、R-INV-02。Bolt 39i の行 5〜8）。 */
    @org.junit.jupiter.api.Nested
    class Bolt39i_貨物種別の除外 {

        private static final Location HONG_KONG = new Location("HKHKG");
        private final List<ConnectionRule> rules = List.of(
                RULES.getFirst(),
                new ConnectionRule(
                        UUID.randomUUID(), HONG_KONG, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null));

        /** 直行、積替え 1 回（V-A + V-S）、積替え 2 回（V-A + V-B + V-C）の 3 つの候補ができる航海。 */
        private List<Voyage> threeCandidateVoyages() {
            return List.of(
                    direct("V-D", "2026-10-31T00:00:00Z"),
                    voyage("V-A", TOKYO, "2026-10-08T00:00:00Z", SINGAPORE, "2026-10-09T00:00:00Z"),
                    fromSingapore("V-S", "2026-10-09T10:00:00Z", "2026-10-30T00:00:00Z"),
                    voyage("V-B", SINGAPORE, "2026-10-09T10:00:00Z", HONG_KONG, "2026-10-12T00:00:00Z"),
                    voyage("V-C", HONG_KONG, "2026-10-12T10:00:00Z", ROTTERDAM, "2026-10-29T00:00:00Z"));
        }

        private Voyage voyage(String number, Location from, String departure, Location to, String arrival) {
            return new Voyage(
                    number,
                    List.of(new PortCall(from, null, at(departure)), new PortCall(to, at(arrival), null)),
                    number + "@1",
                    at("2026-10-07T01:00:00Z"));
        }

        @ParameterizedTest(name = "{0} の案件は候補がすべて貨物種別で除外される")
        @EnumSource(
                value = RouteCargoCategory.class,
                names = {"DANGEROUS", "REEFER", "OTHER_SPECIAL", "UNRECOGNIZED"})
        void 特殊貨物の案件は三つの候補がすべて除外され数えは算出した件数のまま到着の早い順(RouteCargoCategory category) {
            RoutingCase routingCase = open(category);

            CandidateCalculation calculation = routingCase.calculateCandidates(
                    threeCandidateVoyages(), rules, JUDGED_AT, finder, evaluator, freshness);

            assertThat(calculation).isEqualTo(new CandidateCalculation(3, 3, 0, JUDGED_AT));
            assertThat(routingCase.routeVersion().candidates())
                    .allSatisfy(candidate -> assertThat(candidate.evaluation().reasons())
                            .extracting(ExclusionReason::threshold)
                            .containsExactly(new ExclusionThreshold.UnsupportedCargo(category)));
            assertThat(routingCase.routeVersion().candidates())
                    .extracting(candidate ->
                            candidate.legs().stream().map(Leg::voyageNumber).toList())
                    .containsExactly(List.of("V-A", "V-B", "V-C"), List.of("V-A", "V-S"), List.of("V-D"));
        }

        @Test
        void 理由の順は接続不足と期限超過と情報不足と貨物種別() {
            RoutingCase routingCase = open(RouteCargoCategory.REEFER);
            List<Voyage> voyages = List.of(
                    // 取得が 30 時間前（情報不足）、シンガポールで 5 時間の接続（接続不足）、期限の後に着く（期限超過）
                    new Voyage(
                            "V-F",
                            List.of(
                                    new PortCall(TOKYO, null, at("2026-10-08T00:00:00Z")),
                                    new PortCall(SINGAPORE, at("2026-10-09T00:00:00Z"), null)),
                            "V-F@1",
                            minusHours(JUDGED_AT, 30)),
                    fromSingapore("V-L", "2026-10-09T05:00:00Z", "2026-11-03T12:00:00Z"));

            routingCase.calculateCandidates(voyages, rules, JUDGED_AT, finder, evaluator, freshness);

            assertThat(routingCase.routeVersion().candidates()).hasSize(1);
            assertThat(routingCase
                            .routeVersion()
                            .candidates()
                            .getFirst()
                            .evaluation()
                            .reasons())
                    .extracting(ExclusionReason::code)
                    .containsExactly(
                            ExclusionReasonCode.CONNECTION_TOO_SHORT,
                            ExclusionReasonCode.DEADLINE_EXCEEDED,
                            ExclusionReasonCode.INFO_INSUFFICIENT,
                            ExclusionReasonCode.CARGO_NOT_SUPPORTED);
        }

        @Test
        void 候補が0件の算出は特殊貨物でも理由を作らない() {
            RoutingCase routingCase = open(RouteCargoCategory.DANGEROUS);

            CandidateCalculation calculation =
                    routingCase.calculateCandidates(List.of(), rules, JUDGED_AT, finder, evaluator, freshness);

            assertThat(calculation).isEqualTo(new CandidateCalculation(0, 0, 0, JUDGED_AT));
            assertThat(routingCase.routeVersion().candidates()).isEmpty();
        }

        @Test
        void 再算出は候補を入れ直し同じ理由になる() {
            RoutingCase routingCase = open(RouteCargoCategory.DANGEROUS);
            routingCase.calculateCandidates(
                    List.of(direct("V-D", "2026-10-31T00:00:00Z")), rules, JUDGED_AT, finder, evaluator, freshness);

            routingCase.calculateCandidates(
                    List.of(direct("V-E", "2026-10-30T00:00:00Z")), rules, JUDGED_AT, finder, evaluator, freshness);

            assertThat(routingCase.routeVersion().candidates()).hasSize(1);
            assertThat(routingCase
                            .routeVersion()
                            .candidates()
                            .getFirst()
                            .legs()
                            .getFirst()
                            .voyageNumber())
                    .isEqualTo("V-E");
            assertThat(routingCase
                            .routeVersion()
                            .candidates()
                            .getFirst()
                            .evaluation()
                            .reasons())
                    .extracting(ExclusionReason::code)
                    .containsExactly(ExclusionReasonCode.CARGO_NOT_SUPPORTED);
        }
    }

    static RoutingCase open() {
        return open(RouteCargoCategory.GENERAL);
    }

    static RoutingCase open(RouteCargoCategory category) {
        return RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                new RoutingCaseNumber(2026, 1),
                UUID.randomUUID(),
                "TR-2026-0001",
                1,
                UUID.randomUUID(),
                List.of(SINGAPORE),
                new RouteSpecification(TOKYO, ROTTERDAM, DEADLINE, category),
                at("2026-10-06T02:00:00Z"),
                QUOTATION_EXPIRES_AT,
                REQUESTED_BY,
                SHIPPER);
    }

    static Voyage direct(String number, String arrival) {
        return direct(number, arrival, at("2026-10-07T01:00:00Z"));
    }

    static Voyage direct(String number, String arrival, UtcInstant acquiredAt) {
        return new Voyage(
                number,
                List.of(
                        new PortCall(TOKYO, null, at("2026-10-08T00:00:00Z")),
                        new PortCall(ROTTERDAM, at(arrival), null)),
                number + "@1",
                acquiredAt);
    }

    static Voyage feeder(String number, String arrivalAtSingapore) {
        return feeder(number, arrivalAtSingapore, at("2026-10-07T01:00:00Z"));
    }

    static Voyage feeder(String number, String arrivalAtSingapore, UtcInstant acquiredAt) {
        return new Voyage(
                number,
                List.of(
                        new PortCall(TOKYO, null, at("2026-10-08T00:00:00Z")),
                        new PortCall(SINGAPORE, at(arrivalAtSingapore), null)),
                number + "@1",
                acquiredAt);
    }

    static Voyage fromSingapore(String number, String departure, String arrival) {
        return fromSingapore(number, departure, arrival, at("2026-10-07T01:00:00Z"));
    }

    static Voyage fromSingapore(String number, String departure, String arrival, UtcInstant acquiredAt) {
        return new Voyage(
                number,
                List.of(new PortCall(SINGAPORE, null, at(departure)), new PortCall(ROTTERDAM, at(arrival), null)),
                number + "@1",
                acquiredAt);
    }

    static UtcInstant minusHours(UtcInstant instant, long hours) {
        return new UtcInstant(instant.instant().minus(Duration.ofHours(hours)));
    }

    static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

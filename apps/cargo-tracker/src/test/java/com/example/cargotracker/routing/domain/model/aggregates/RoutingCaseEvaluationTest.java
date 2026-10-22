package com.example.cargotracker.routing.domain.model.aggregates;

import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseConfirmationTest.COMMIT_AT;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseConfirmationTest.FRESHNESS;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseConfirmationTest.JUDGED_AT;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseConfirmationTest.ROUTE_DESIGNER;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseConfirmationTest.SINGAPORE_8H;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseConfirmationTest.VOYAGES;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.ROTTERDAM;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.SINGAPORE;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.TOKYO;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.at;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 確定した経路版の評価（US-07 AC4、R-INV-07。Bolt 37d2）。状態の組み合わせの表の行ごとに 1 本（T-163）。
 *
 * <p>確定するのは候補 2（V-FEED で SGSIN に 10-09T00:00Z に着き、V-CONN で 10-09T10:00Z に出て 10-30T00:00Z に着く。接続 10 時間、
 * 規則 8 時間、希望到着期限 11-02T00:00Z）。評価の時刻は確定の 2 時間後。区間の航海の情報版が変わったときだけ評価し、区間を今の航海で
 * 引き直して判定し直す（保存した判定は使わない）。
 */
class RoutingCaseEvaluationTest {

    static final UtcInstant EVALUATED_AT = at("2026-10-07T06:00:00Z");

    private final RouteCandidateFinder finder = new RouteCandidateFinder();
    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();

    @Test
    void 区間の情報版が変わらなければ規則だけが変わっても評価せず確定のまま() {
        // 行 1
        RoutingCase routingCase = confirmed(List.of(SINGAPORE_8H));
        List<Voyage> otherUpdated = replaced(VOYAGES, direct("V-EARLY", "V-EARLY@2", "2026-10-29T00:00:00Z"));
        ConnectionRule stricter = rule(Duration.ofHours(12), null);

        Optional<RouteRedesignRequired> event =
                routingCase.evaluateConfirmedRoute(otherUpdated, List.of(stricter), EVALUATED_AT, finder, evaluator);

        assertThat(event).isEmpty();
        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
    }

    @Test
    void 情報版が変わっても今の予定で期限と接続を満たせば確定のままで区間も変えない() {
        // 行 2
        RoutingCase routingCase = confirmed(List.of(SINGAPORE_8H));
        RouteVersion before = routingCase.routeVersion();

        Optional<RouteRedesignRequired> event = routingCase.evaluateConfirmedRoute(
                replaced(VOYAGES, connection("V-CONN@2", "2026-10-09T10:00:00Z", "2026-11-01T00:00:00Z")),
                List.of(SINGAPORE_8H),
                EVALUATED_AT,
                finder,
                evaluator);

        assertThat(event).isEmpty();
        assertThat(routingCase.routeVersion()).isEqualTo(before);
    }

    @Test
    void 到着が期限を過ぎると再設計要になり旧情報版と確定の記録を残してDE06を返す() {
        // 行 3
        RoutingCase routingCase = confirmed(List.of(SINGAPORE_8H));
        RouteVersion before = routingCase.routeVersion();

        Optional<RouteRedesignRequired> event = routingCase.evaluateConfirmedRoute(
                replaced(VOYAGES, connection("V-CONN@2", "2026-10-09T10:00:00Z", "2026-11-03T00:00:00Z")),
                List.of(SINGAPORE_8H),
                EVALUATED_AT,
                finder,
                evaluator);

        RouteVersion after = routingCase.routeVersion();
        assertThat(after.status()).isEqualTo(RouteVersionStatus.REDESIGN_REQUIRED);
        assertThat(after.redesign())
                .contains(new RedesignRecord(EVALUATED_AT, List.of(RedesignReasonCode.DEADLINE_EXCEEDED)));
        assertThat(after.candidates()).isEqualTo(before.candidates());
        assertThat(after.confirmation()).isEqualTo(before.confirmation());
        assertThat(after.evaluatedAt()).isEqualTo(before.evaluatedAt());
        assertThat(routingCase.confirmedCandidateOf(1).orElseThrow().legs())
                .extracting(Leg::infoVersion)
                .containsExactly("V-FEED@1", "V-CONN@1");
        assertThat(event)
                .contains(new RouteRedesignRequired(
                        routingCase.id().value(),
                        "RC-2026-0001",
                        1,
                        routingCase.quotationId(),
                        routingCase.transportRequestId(),
                        1,
                        EVALUATED_AT,
                        List.of("DEADLINE_EXCEEDED"),
                        List.of("V-FEED@1", "V-CONN@1")));
    }

    @Test
    void 接続時間が足りなくなると接続不足で再設計要() {
        // 行 4
        RoutingCase routingCase = confirmed(List.of(SINGAPORE_8H));

        evaluate(routingCase, replaced(VOYAGES, feeder("V-FEED@2", "2026-10-09T04:00:00Z")), List.of(SINGAPORE_8H));

        assertReasons(routingCase, RedesignReasonCode.CONNECTION_TOO_SHORT);
    }

    @Test
    void 接続の時刻が動いて有効な規則がなくなると接続できないで再設計要() {
        // 行 5
        List<ConnectionRule> untilOctober10 = List.of(rule(Duration.ofHours(8), at("2026-10-10T00:00:00Z")));
        RoutingCase routingCase = confirmed(untilOctober10);

        evaluate(
                routingCase,
                replaced(VOYAGES, connection("V-CONN@2", "2026-10-11T00:00:00Z", "2026-10-30T00:00:00Z")),
                untilOctober10);

        assertReasons(routingCase, RedesignReasonCode.NOT_CONNECTABLE);
    }

    @Test
    void 接続不足と期限超過が重なると制約適合判定の理由の順に持つ() {
        // 行 6
        RoutingCase routingCase = confirmed(List.of(SINGAPORE_8H));
        List<Voyage> updated = replaced(
                replaced(VOYAGES, feeder("V-FEED@2", "2026-10-09T04:00:00Z")),
                connection("V-CONN@2", "2026-10-09T10:00:00Z", "2026-11-03T00:00:00Z"));

        evaluate(routingCase, updated, List.of(SINGAPORE_8H));

        assertReasons(routingCase, RedesignReasonCode.CONNECTION_TOO_SHORT, RedesignReasonCode.DEADLINE_EXCEEDED);
    }

    @Test
    void 区間3の2つの接続がどちらも足りなければ接続不足を1つにまとめる() {
        // 行 6b
        Location hongKong = new Location("HKHKG");
        Leg first = leg("V-A", TOKYO, hongKong, "2026-10-08T00:00:00Z", "2026-10-10T00:00:00Z");
        Leg second = leg("V-B", hongKong, SINGAPORE, "2026-10-11T00:00:00Z", "2026-10-13T00:00:00Z");
        Leg third = leg("V-C", SINGAPORE, ROTTERDAM, "2026-10-14T00:00:00Z", "2026-10-30T00:00:00Z");
        RoutingCase routingCase = confirmedWith(List.of(first, second, third));
        List<ConnectionRule> rules = List.of(
                SINGAPORE_8H,
                new ConnectionRule(UUID.randomUUID(), hongKong, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null));
        List<Voyage> updated = List.of(
                voyage("V-A", "V-A@2", TOKYO, "2026-10-08T00:00:00Z", hongKong, "2026-10-10T20:00:00Z"),
                voyage("V-B", "V-B@1", hongKong, "2026-10-11T00:00:00Z", SINGAPORE, "2026-10-13T20:00:00Z"),
                voyage("V-C", "V-C@2", SINGAPORE, "2026-10-14T00:00:00Z", ROTTERDAM, "2026-10-30T00:00:00Z"));

        evaluate(routingCase, updated, rules);

        assertReasons(routingCase, RedesignReasonCode.CONNECTION_TOO_SHORT);
    }

    @Test
    void 区間の航海がなくなると航海を引き直せないで再設計要() {
        // 行 7
        RoutingCase routingCase = confirmed(List.of(SINGAPORE_8H));
        List<Voyage> withoutConnection = VOYAGES.stream()
                .filter(voyage -> !voyage.voyageNumber().equals("V-CONN"))
                .toList();

        evaluate(routingCase, withoutConnection, List.of(SINGAPORE_8H));

        assertReasons(routingCase, RedesignReasonCode.VOYAGE_UNAVAILABLE);
    }

    @Test
    void 区間の航海の寄る順が逆になると航海を引き直せないで再設計要() {
        // 行 8（ほかの引き直せない形は RouteCandidateFinderTest）
        RoutingCase routingCase = confirmed(List.of(SINGAPORE_8H));
        Voyage reversed =
                voyage("V-CONN", "V-CONN@2", ROTTERDAM, "2026-10-09T10:00:00Z", SINGAPORE, "2026-10-30T00:00:00Z");

        evaluate(routingCase, replaced(VOYAGES, reversed), List.of(SINGAPORE_8H));

        assertReasons(routingCase, RedesignReasonCode.VOYAGE_UNAVAILABLE);
    }

    @Test
    void 再設計要の経路版はもう評価せず原因が解消しても戻さない() {
        // 行 9（UC-08 1a）。原因が解消した入力（期限に間に合う）と、まだ不適合の入力（期限を過ぎる）の両方で何もしない
        RedesignRecord redesign =
                new RedesignRecord(at("2026-10-07T05:00:00Z"), List.of(RedesignReasonCode.DEADLINE_EXCEEDED));
        for (String arrival : List.of("2026-10-29T00:00:00Z", "2026-11-05T00:00:00Z")) {
            RoutingCase routingCase = RoutingCaseFixture.redesignRequired(redesign);
            RouteVersion before = routingCase.routeVersion();

            Optional<RouteRedesignRequired> event = routingCase.evaluateConfirmedRoute(
                    List.of(direct("V-EARLY", "V-EARLY@3", arrival)),
                    List.of(SINGAPORE_8H),
                    EVALUATED_AT,
                    finder,
                    evaluator);

            assertThat(event).as(arrival).isEmpty();
            assertThat(routingCase.routeVersion()).as(arrival).isEqualTo(before);
        }
    }

    @Test
    void 確定していない経路版は評価しない() {
        // 行 10
        RoutingCase draft = RoutingCaseTest.open();
        RoutingCase presented = presented();
        List<Voyage> updated =
                replaced(VOYAGES, connection("V-CONN@2", "2026-10-09T10:00:00Z", "2026-11-03T00:00:00Z"));

        assertThat(draft.evaluateConfirmedRoute(updated, List.of(SINGAPORE_8H), EVALUATED_AT, finder, evaluator))
                .isEmpty();
        assertThat(presented.evaluateConfirmedRoute(updated, List.of(SINGAPORE_8H), EVALUATED_AT, finder, evaluator))
                .isEmpty();
        assertThat(draft.routeVersion().status()).isEqualTo(RouteVersionStatus.DRAFT);
        assertThat(presented.routeVersion().status()).isEqualTo(RouteVersionStatus.CANDIDATES_PRESENTED);
        RouteVersion candidates = presented.routeVersion();
        RoutingCase expertReview = RoutingCaseFixture.withRouteVersions(
                presented,
                List.of(new RouteVersion(
                        1,
                        RouteVersionStatus.EXPERT_REVIEW,
                        candidates.candidates(),
                        candidates.evaluatedAt().orElseThrow())));
        assertThat(expertReview.evaluateConfirmedRoute(updated, List.of(SINGAPORE_8H), EVALUATED_AT, finder, evaluator))
                .isEmpty();
        assertThat(expertReview.routeVersion().status()).isEqualTo(RouteVersionStatus.EXPERT_REVIEW);
    }

    @Test
    void 旧版の後の新しい経路版があれば旧版は評価しない() {
        // 行 11
        RoutingCase confirmed = RoutingCaseFixture.confirmed().routingCase();
        RouteVersion v1 = confirmed.routeVersion();
        RouteVersion superseded = new RouteVersion(
                1,
                RouteVersionStatus.SUPERSEDED,
                v1.candidates(),
                v1.evaluatedAt().orElseThrow(),
                v1.candidatesFound(),
                v1.confirmation().orElseThrow(),
                null);
        RoutingCase routingCase =
                RoutingCaseFixture.withRouteVersions(confirmed, List.of(superseded, RouteVersion.draft(2)));

        Optional<RouteRedesignRequired> event = routingCase.evaluateConfirmedRoute(
                List.of(direct("V-EARLY", "V-EARLY@2", "2026-11-05T00:00:00Z")),
                List.of(SINGAPORE_8H),
                EVALUATED_AT,
                finder,
                evaluator);

        assertThat(event).isEmpty();
        assertThat(routingCase.routeVersions())
                .extracting(RouteVersion::status)
                .containsExactly(RouteVersionStatus.SUPERSEDED, RouteVersionStatus.DRAFT);
    }

    private void evaluate(RoutingCase routingCase, List<Voyage> voyages, List<ConnectionRule> rules) {
        assertThat(routingCase.evaluateConfirmedRoute(voyages, rules, EVALUATED_AT, finder, evaluator))
                .isPresent();
    }

    private static void assertReasons(RoutingCase routingCase, RedesignReasonCode... reasons) {
        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.REDESIGN_REQUIRED);
        assertThat(routingCase.routeVersion().redesign()).contains(new RedesignRecord(EVALUATED_AT, List.of(reasons)));
    }

    /** 候補 3 件（適合 2、除外 1）を算出した案件。 */
    private RoutingCase presented(List<ConnectionRule> rules) {
        RoutingCase routingCase = RoutingCaseTest.open();
        routingCase.calculateCandidates(VOYAGES, rules, JUDGED_AT, finder, evaluator, FRESHNESS);
        return routingCase;
    }

    private RoutingCase presented() {
        return presented(List.of(SINGAPORE_8H));
    }

    /** 候補 2（積替え V-FEED → V-CONN）を確定した案件。 */
    private RoutingCase confirmed(List<ConnectionRule> rules) {
        RoutingCase routingCase = presented(rules);
        routingCase.confirm(
                2, "SGSIN の接続に 2 時間の余裕がある。", ROUTE_DESIGNER, VOYAGES, rules, COMMIT_AT, evaluator, FRESHNESS);
        return routingCase;
    }

    /** 区間の列の候補 1 を確定した経路版を持つ案件（保存されている状態からの再構成）。 */
    private static RoutingCase confirmedWith(List<Leg> legs) {
        RouteCandidate candidate = new RouteCandidate(
                1, legs, new ConstraintEvaluation(legs.getLast().arrivalAt(), List.of(), Duration.ZERO), JUDGED_AT);
        RouteVersion version = new RouteVersion(
                1,
                RouteVersionStatus.CONFIRMED,
                List.of(candidate),
                JUDGED_AT,
                new RouteConfirmation(1, new DecisionRationale("根拠"), ROUTE_DESIGNER.userId(), COMMIT_AT));
        return RoutingCaseFixture.withRouteVersions(RoutingCaseTest.open(), List.of(version));
    }

    private static List<Voyage> replaced(List<Voyage> voyages, Voyage updated) {
        List<Voyage> result = new ArrayList<>();
        for (Voyage voyage : voyages) {
            result.add(voyage.voyageNumber().equals(updated.voyageNumber()) ? updated : voyage);
        }
        return result;
    }

    private static Voyage feeder(String infoVersion, String arrivalAtSingapore) {
        return voyage("V-FEED", infoVersion, TOKYO, "2026-10-08T00:00:00Z", SINGAPORE, arrivalAtSingapore);
    }

    private static Voyage connection(String infoVersion, String departure, String arrival) {
        return voyage("V-CONN", infoVersion, SINGAPORE, departure, ROTTERDAM, arrival);
    }

    private static Voyage direct(String number, String infoVersion, String arrival) {
        return voyage(number, infoVersion, TOKYO, "2026-10-08T00:00:00Z", ROTTERDAM, arrival);
    }

    private static Voyage voyage(
            String number, String infoVersion, Location load, String departure, Location discharge, String arrival) {
        return new Voyage(
                number,
                List.of(new PortCall(load, null, at(departure)), new PortCall(discharge, at(arrival), null)),
                infoVersion,
                at("2026-10-07T05:30:00Z"));
    }

    private static Leg leg(String number, Location load, Location discharge, String departure, String arrival) {
        return new Leg(number, load, discharge, at(departure), at(arrival), number + "@1", at("2026-10-07T01:00:00Z"));
    }

    private static ConnectionRule rule(Duration minimum, UtcInstant validTo) {
        return new ConnectionRule(UUID.randomUUID(), SINGAPORE, minimum, at("2026-01-01T00:00:00Z"), validTo);
    }
}

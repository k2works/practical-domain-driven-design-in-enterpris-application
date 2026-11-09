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
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.direct;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.fromSingapore;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.StaleLeg;
import com.example.cargotracker.routing.domain.model.valueobjects.StaleLegReason;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 古くなった候補の判定（UC-06 4a、【業務の判断】(w)・(y)・(z)。Bolt 39f2）。状態の組み合わせの表の行ごとに 1 本（T-182）。
 *
 * <p>候補は {@link RoutingCaseConfirmationTest} と同じ航海で算出する。候補 1 は直行 V-EARLY、候補 2 は積替え V-FEED → V-CONN、候補 3 は期限超過の直行 V-LATE（除外）。
 * 航海の取得時刻は 2026-10-07T01:00Z（許容時間 24 時間）。
 */
class RoutingCaseStaleLegsTest {

    private static final UtcInstant ACQUIRED_AT = at("2026-10-07T01:00:00Z");
    /** 算出した航海の取得から許容時間を過ぎた判定時刻。 */
    private static final UtcInstant LONG_AFTER = at("2026-10-09T00:00:00Z");
    /** 取り込み直した航海の取得時刻（{@link #LONG_AFTER} の許容時間の内）。 */
    private static final UtcInstant REACQUIRED_AT = at("2026-10-08T23:00:00Z");

    private static final Location HONG_KONG = new Location("HKHKG");
    private static final ConnectionRule HONG_KONG_8H =
            new ConnectionRule(UUID.randomUUID(), HONG_KONG, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null);

    private static final RouteCandidateFinder FINDER = new RouteCandidateFinder();
    private static final ConstraintEvaluator EVALUATOR = new ConstraintEvaluator();

    private static RoutingCase calculated(List<Voyage> voyages, List<ConnectionRule> rules) {
        RoutingCase routingCase = RoutingCaseTest.open();
        routingCase.calculateCandidates(voyages, rules, JUDGED_AT, FINDER, EVALUATOR, FRESHNESS);
        return routingCase;
    }

    private static RoutingCase presented() {
        return calculated(VOYAGES, List.of(SINGAPORE_8H));
    }

    /** 1 本を置き換えた今の航海の一覧。 */
    private static List<Voyage> replaced(List<Voyage> voyages, Voyage changed) {
        return voyages.stream()
                .map(voyage -> voyage.voyageNumber().equals(changed.voyageNumber()) ? changed : voyage)
                .toList();
    }

    private static List<Voyage> with(Voyage changed) {
        return replaced(VOYAGES, changed);
    }

    /** 同じ値を取り込み直した航海（情報版と寄港は同じで、取得時刻だけが新しい）。 */
    private static Voyage reacquired(Voyage voyage, UtcInstant acquiredAt) {
        return new Voyage(voyage.voyageNumber(), voyage.portCalls(), voyage.adoptedInfoVersion(), acquiredAt);
    }

    private static List<Voyage> allReacquiredAt(UtcInstant acquiredAt) {
        return VOYAGES.stream().map(voyage -> reacquired(voyage, acquiredAt)).toList();
    }

    private static Voyage newVersion(Voyage voyage, String infoVersion) {
        return new Voyage(voyage.voyageNumber(), voyage.portCalls(), infoVersion, voyage.acquiredAt());
    }

    private static Voyage earliest() {
        return direct("V-EARLY", "2026-10-29T00:00:00Z");
    }

    private static Voyage connection() {
        return fromSingapore("V-CONN", "2026-10-09T10:00:00Z", "2026-10-30T00:00:00Z");
    }

    private static Voyage late() {
        return direct("V-LATE", "2026-11-03T12:00:00Z");
    }

    /** 積替え 2 回（SGSIN と HKHKG。接続はどちらも 1 日）の航海。 */
    private static List<Voyage> threeLegVoyages() {
        return List.of(
                RoutingCaseTest.feeder("V-A", "2026-10-10T00:00:00Z"),
                new Voyage(
                        "V-B",
                        List.of(
                                new PortCall(SINGAPORE, null, at("2026-10-11T00:00:00Z")),
                                new PortCall(HONG_KONG, at("2026-10-14T00:00:00Z"), null)),
                        "V-B@1",
                        ACQUIRED_AT),
                new Voyage(
                        "V-C",
                        List.of(
                                new PortCall(HONG_KONG, null, at("2026-10-15T00:00:00Z")),
                                new PortCall(ROTTERDAM, at("2026-10-30T00:00:00Z"), null)),
                        "V-C@1",
                        ACQUIRED_AT));
    }

    @Nested
    class Bolt39f2_古くなった候補の判定 {

        @Test
        void 行1_すべての区間が今の航海と合えば空() {
            assertThat(presented().staleLegs(VOYAGES, FRESHNESS, COMMIT_AT)).isEmpty();
        }

        @Test
        void 行2_区間の情報版が違えば予定が同じでも情報版の変更() {
            List<StaleLeg> stale =
                    presented().staleLegs(with(newVersion(earliest(), "V-EARLY@2")), FRESHNESS, COMMIT_AT);

            assertThat(stale).containsExactly(new StaleLeg(1, 1, StaleLegReason.INFO_VERSION_CHANGED));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "出発だけが違う, 2026-10-09T11:00:00Z, 2026-10-30T00:00:00Z",
            "到着だけが違う, 2026-10-09T10:00:00Z, 2026-10-30T01:00:00Z"
        })
        void 行3_同じ情報版で区間の予定が違えば予定の変更(String description, String departure, String arrival) {
            List<StaleLeg> stale =
                    presented().staleLegs(with(fromSingapore("V-CONN", departure, arrival)), FRESHNESS, COMMIT_AT);

            assertThat(stale).as(description).containsExactly(new StaleLeg(2, 2, StaleLegReason.SCHEDULE_CHANGED));
        }

        @Test
        void 行4_区間の航海がなければ航海なし() {
            List<Voyage> withoutEarliest = VOYAGES.stream()
                    .filter(voyage -> !voyage.voyageNumber().equals("V-EARLY"))
                    .toList();

            assertThat(presented().staleLegs(withoutEarliest, FRESHNESS, COMMIT_AT))
                    .containsExactly(new StaleLeg(1, 1, StaleLegReason.VOYAGE_MISSING));
        }

        @Test
        void 行5_同じ情報版で元の値に戻ると状態を持たないので空に戻る() {
            RoutingCase routingCase = presented();
            List<Voyage> changed = with(fromSingapore("V-CONN", "2026-10-09T10:00:00Z", "2026-10-31T00:00:00Z"));
            assertThat(routingCase.staleLegs(changed, FRESHNESS, COMMIT_AT)).hasSize(1);

            assertThat(routingCase.staleLegs(VOYAGES, FRESHNESS, COMMIT_AT)).isEmpty();
        }

        @Test
        void 行5b_新しい外部版で元の予定に戻ると情報版が違うので再算出が要る() {
            RoutingCase routingCase = presented();

            assertThat(routingCase.staleLegs(with(newVersion(connection(), "V-CONN@2")), FRESHNESS, COMMIT_AT))
                    .containsExactly(new StaleLeg(2, 2, StaleLegReason.INFO_VERSION_CHANGED));
        }

        @Test
        void 行6_3区間の候補の2区間目だけが古いと候補番号と位置と理由の1件だけ() {
            RoutingCase routingCase = calculated(threeLegVoyages(), List.of(SINGAPORE_8H, HONG_KONG_8H));
            RouteCandidate candidate = routingCase.routeVersion().candidates().stream()
                    .filter(each -> each.legs().size() == 3)
                    .findFirst()
                    .orElseThrow();
            List<Voyage> current =
                    replaced(threeLegVoyages(), newVersion(threeLegVoyages().get(1), "V-B@2"));

            assertThat(routingCase.staleLegs(current, FRESHNESS, COMMIT_AT))
                    .containsExactly(new StaleLeg(candidate.candidateNo(), 2, StaleLegReason.INFO_VERSION_CHANGED));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "許容時間の直前, 2026-10-08T00:59:59Z, false",
            "許容時間と同時刻, 2026-10-08T01:00:00Z, false",
            "許容時間の直後, 2026-10-08T01:00:01Z, true"
        })
        void 行7_適合の候補は今の航海の取得から許容時間を過ぎた直後だけ情報不足(String description, String judgedAt, boolean expected) {
            List<Voyage> onlyEarliestOld = replaced(allReacquiredAt(at("2026-10-07T23:00:00Z")), earliest());

            List<StaleLeg> stale = presented().staleLegs(onlyEarliestOld, FRESHNESS, at(judgedAt));

            assertThat(stale)
                    .as(description)
                    .isEqualTo(expected ? List.of(new StaleLeg(1, 1, StaleLegReason.INFO_INSUFFICIENT)) : List.of());
        }

        @Test
        void 行8_区間の写しの取得時刻が古くても同じ情報版を取り込み直して今の航海が新しければ空() {
            assertThat(presented().staleLegs(allReacquiredAt(REACQUIRED_AT), FRESHNESS, LONG_AFTER))
                    .isEmpty();
        }

        /** 判断根拠と出発は通る値にした確定（出発は許容時間より後）。拒否の理由を返す。 */
        private Optional<RouteConfirmationRejectionReason> rejectionOf(
                RoutingCase routingCase, List<Voyage> current, UtcInstant commitAt) {
            try {
                routingCase.confirm(
                        1, "根拠", ROUTE_DESIGNER, current, List.of(SINGAPORE_8H), commitAt, EVALUATOR, FRESHNESS);
                return Optional.empty();
            } catch (RouteConfirmationRejected rejected) {
                return Optional.of(rejected.reason());
            }
        }

        private static Voyage leavingLater(String infoVersion, String arrival, UtcInstant acquiredAt) {
            return new Voyage(
                    "V-LATER",
                    List.of(
                            new PortCall(TOKYO, null, at("2026-10-20T00:00:00Z")),
                            new PortCall(ROTTERDAM, at(arrival), null)),
                    infoVersion,
                    acquiredAt);
        }

        static Stream<Arguments> equivalentToConfirmation() {
            String arrival = "2026-10-30T00:00:00Z";
            return Stream.of(
                    Arguments.of(
                            "行 2 の入力: 情報版が違う",
                            List.of(leavingLater("V-LATER@2", arrival, ACQUIRED_AT)),
                            COMMIT_AT,
                            RouteConfirmationRejectionReason.INFO_VERSION_CHANGED),
                    Arguments.of(
                            "行 3 の入力: 同じ情報版で到着が違う",
                            List.of(leavingLater("V-LATER@1", "2026-10-31T00:00:00Z", ACQUIRED_AT)),
                            COMMIT_AT,
                            RouteConfirmationRejectionReason.INFO_VERSION_CHANGED),
                    Arguments.of(
                            "行 4 の入力: 航海がない",
                            List.of(),
                            COMMIT_AT,
                            RouteConfirmationRejectionReason.INFO_VERSION_CHANGED),
                    Arguments.of(
                            "行 7 の入力: 取得から許容時間の直後",
                            List.of(leavingLater("V-LATER@1", arrival, ACQUIRED_AT)),
                            at("2026-10-08T01:00:01Z"),
                            RouteConfirmationRejectionReason.INFO_INSUFFICIENT),
                    Arguments.of(
                            "行 8 の入力: 取り込み直して今の航海が新しい",
                            List.of(leavingLater("V-LATER@1", arrival, at("2026-10-08T23:00:00Z"))),
                            LONG_AFTER,
                            null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("equivalentToConfirmation")
        void 行8b_古い区間が出ることと確定が情報版の更新か情報不足で拒否することは同値(
                String description,
                List<Voyage> current,
                UtcInstant at,
                RouteConfirmationRejectionReason expectedRejection) {
            RoutingCase routingCase =
                    calculated(List.of(leavingLater("V-LATER@1", "2026-10-30T00:00:00Z", ACQUIRED_AT)), List.of());
            boolean stale = !routingCase.staleLegs(current, FRESHNESS, at).isEmpty();

            Optional<RouteConfirmationRejectionReason> rejection = rejectionOf(routingCase, current, at);

            assertThat(rejection).as(description).isEqualTo(Optional.ofNullable(expectedRejection));
            assertThat(stale).as(description).isEqualTo(rejection.isPresent());
        }

        static Stream<Arguments> reasonOrder() {
            return Stream.of(
                    Arguments.of(
                            "情報版が違い、かつ取得から許容時間を過ぎている",
                            newVersion(earliest(), "V-EARLY@2"),
                            StaleLegReason.INFO_VERSION_CHANGED),
                    Arguments.of(
                            "同じ情報版で予定が違い、かつ取得から許容時間を過ぎている",
                            direct("V-EARLY", "2026-10-29T01:00:00Z"),
                            StaleLegReason.SCHEDULE_CHANGED));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("reasonOrder")
        void 行9_不一致と鮮度が重なると1区間に不一致の理由を1件だけ返す(String description, Voyage changed, StaleLegReason reason) {
            List<Voyage> current = replaced(allReacquiredAt(REACQUIRED_AT), changed);

            assertThat(presented().staleLegs(current, FRESHNESS, LONG_AFTER))
                    .as(description)
                    .containsExactly(new StaleLeg(1, 1, reason));
        }

        static Stream<Arguments> excludedMismatches() {
            return Stream.of(
                    Arguments.of("情報版が違う", with(newVersion(late(), "V-LATE@2")), StaleLegReason.INFO_VERSION_CHANGED),
                    Arguments.of(
                            "航海がない",
                            VOYAGES.stream()
                                    .filter(voyage -> !voyage.voyageNumber().equals("V-LATE"))
                                    .toList(),
                            StaleLegReason.VOYAGE_MISSING));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("excludedMismatches")
        void 行10_除外の候補の区間が今の航海と合わなければ理由を返す(String description, List<Voyage> current, StaleLegReason reason) {
            assertThat(presented().staleLegs(current, FRESHNESS, COMMIT_AT))
                    .as(description)
                    .containsExactly(new StaleLeg(3, 1, reason));
        }

        @Test
        void 行10b_除外の候補は取得から許容時間を過ぎただけなら空() {
            List<Voyage> onlyLateOld = replaced(allReacquiredAt(REACQUIRED_AT), late());

            assertThat(presented().staleLegs(onlyLateOld, FRESHNESS, LONG_AFTER))
                    .isEmpty();
        }

        @Test
        void 行10b_情報不足で除外した直後の候補も空() {
            List<Voyage> old = List.of(direct("V-OLD", "2026-10-29T00:00:00Z", at("2026-10-06T02:00:00Z")));
            RoutingCase routingCase = calculated(old, List.of());
            assertThat(routingCase
                            .routeVersion()
                            .candidates()
                            .getFirst()
                            .evaluation()
                            .conforming())
                    .isFalse();

            assertThat(routingCase.staleLegs(old, FRESHNESS, JUDGED_AT)).isEmpty();
        }

        private static List<PortCall> tokyoTwiceCalls() {
            return List.of(
                    new PortCall(TOKYO, null, at("2026-10-08T00:00:00Z")),
                    new PortCall(SINGAPORE, at("2026-10-10T00:00:00Z"), at("2026-10-11T00:00:00Z")),
                    // 同じ港に同じ時刻の出発で 2 度寄る（一致する組が 2 つになる。Bolt 39f の行 9）
                    new PortCall(TOKYO, at("2026-10-07T20:00:00Z"), at("2026-10-08T00:00:00Z")),
                    new PortCall(ROTTERDAM, at("2026-10-30T00:00:00Z"), null));
        }

        private static Leg tokyoToRotterdam(Integer loadCallNo, Integer dischargeCallNo) {
            return new Leg(
                    "V-TWICE",
                    TOKYO,
                    ROTTERDAM,
                    at("2026-10-08T00:00:00Z"),
                    at("2026-10-30T00:00:00Z"),
                    "V-TWICE@1",
                    ACQUIRED_AT,
                    loadCallNo,
                    dischargeCallNo);
        }

        static Stream<Arguments> twoVisits() {
            List<PortCall> shifted = List.of(
                    new PortCall(HONG_KONG, null, at("2026-10-07T12:00:00Z")),
                    new PortCall(TOKYO, at("2026-10-07T20:00:00Z"), at("2026-10-08T00:00:00Z")),
                    new PortCall(ROTTERDAM, at("2026-10-30T00:00:00Z"), null));
            return Stream.of(
                    Arguments.of("予定の一致する組が 1 つで位置がずれている", shifted, tokyoToRotterdam(1, 2), List.of()),
                    Arguments.of("一致する組が 2 つ以上で位置の組がある", tokyoTwiceCalls(), tokyoToRotterdam(3, 4), List.of()),
                    Arguments.of(
                            "一致する組が 2 つ以上で位置の組がない",
                            tokyoTwiceCalls(),
                            tokyoToRotterdam(2, 4),
                            List.of(new StaleLeg(1, 1, StaleLegReason.SCHEDULE_CHANGED))),
                    Arguments.of(
                            "位置が NULL（Bolt 39f より前の候補）", tokyoTwiceCalls(), tokyoToRotterdam(null, null), List.of()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("twoVisits")
        void 行11_2度寄る航海はmatchesLegが偽のときだけ予定の変更(
                String description, List<PortCall> calls, Leg leg, List<StaleLeg> expected) {
            RoutingCase routingCase = withVersions(
                    new RouteVersion(1, RouteVersionStatus.CANDIDATES_PRESENTED, List.of(candidate(leg)), JUDGED_AT));

            assertThat(routingCase.staleLegs(
                            List.of(new Voyage("V-TWICE", calls, "V-TWICE@1", ACQUIRED_AT)), FRESHNESS, COMMIT_AT))
                    .as(description)
                    .isEqualTo(expected);
        }

        private static RouteCandidate candidate(Leg leg) {
            return new RouteCandidate(
                    1, List.of(leg), new ConstraintEvaluation(leg.arrivalAt(), List.of(), null), JUDGED_AT);
        }

        private static RoutingCase withVersions(RouteVersion... versions) {
            return RoutingCaseFixture.withRouteVersions(RoutingCaseTest.open(), List.of(versions));
        }

        static Stream<Arguments> notConfirmable() {
            List<RouteCandidate> candidates = presented().routeVersion().candidates();
            RouteConfirmation confirmation =
                    new RouteConfirmation(1, new DecisionRationale("根拠"), ROUTE_DESIGNER.userId(), COMMIT_AT);
            RouteVersion confirmed =
                    new RouteVersion(1, RouteVersionStatus.CONFIRMED, candidates, JUDGED_AT, confirmation);
            return Stream.of(
                    Arguments.of("作成中", RoutingCaseTest.open()),
                    Arguments.of(
                            "専門判断待ち",
                            withVersions(new RouteVersion(1, RouteVersionStatus.EXPERT_REVIEW, candidates, JUDGED_AT))),
                    Arguments.of("確定", withVersions(confirmed)),
                    Arguments.of(
                            "再設計要",
                            withVersions(new RouteVersion(
                                    1,
                                    RouteVersionStatus.REDESIGN_REQUIRED,
                                    candidates,
                                    JUDGED_AT,
                                    candidates.size(),
                                    confirmation,
                                    new RedesignRecord(COMMIT_AT, List.of(RedesignReasonCode.DEADLINE_EXCEEDED))))),
                    Arguments.of(
                            "旧版",
                            withVersions(new RouteVersion(1, RouteVersionStatus.SUPERSEDED, candidates, JUDGED_AT))),
                    Arguments.of(
                            "候補提示済みで確定した経路版がある",
                            withVersions(
                                    confirmed,
                                    new RouteVersion(
                                            2, RouteVersionStatus.CANDIDATES_PRESENTED, candidates, JUDGED_AT))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("notConfirmable")
        void 行12_確定できない経路版の状態なら今の航海が合わなくても空(String description, RoutingCase routingCase) {
            // 確定できるときは同じ入力で返ることを先に確かめる（T-106）
            assertThat(presented().staleLegs(List.of(), FRESHNESS, COMMIT_AT)).isNotEmpty();

            assertThat(routingCase.staleLegs(List.of(), FRESHNESS, COMMIT_AT))
                    .as(description)
                    .isEmpty();
        }

        static Stream<Arguments> justCalculated() {
            Voyage tokyoTwice = new Voyage(
                    "V-TWICE",
                    List.of(
                            new PortCall(TOKYO, null, at("2026-10-08T00:00:00Z")),
                            new PortCall(SINGAPORE, at("2026-10-10T00:00:00Z"), at("2026-10-11T00:00:00Z")),
                            new PortCall(TOKYO, at("2026-10-13T00:00:00Z"), at("2026-10-14T00:00:00Z")),
                            new PortCall(ROTTERDAM, at("2026-10-30T00:00:00Z"), null)),
                    "V-TWICE@1",
                    ACQUIRED_AT);
            return Stream.of(
                    Arguments.of("直行と積替え 1 回と除外", VOYAGES, List.of(SINGAPORE_8H), 1),
                    Arguments.of("積替え 2 回", threeLegVoyages(), List.of(SINGAPORE_8H, HONG_KONG_8H), 3),
                    Arguments.of("2 度寄る航海", List.of(tokyoTwice), List.of(), 1));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("justCalculated")
        void 行14_算出の直後は算出に使った航海で判定すると空(
                String description, List<Voyage> voyages, List<ConnectionRule> rules, int legsOfFirst) {
            RoutingCase routingCase = calculated(voyages, rules);
            List<RouteCandidate> candidates =
                    new ArrayList<>(routingCase.routeVersion().candidates());
            assertThat(candidates).as(description).isNotEmpty();
            assertThat(candidates.getFirst().legs()).as(description).hasSize(legsOfFirst);

            assertThat(routingCase.staleLegs(voyages, FRESHNESS, JUDGED_AT))
                    .as(description)
                    .isEmpty();
        }
    }
}

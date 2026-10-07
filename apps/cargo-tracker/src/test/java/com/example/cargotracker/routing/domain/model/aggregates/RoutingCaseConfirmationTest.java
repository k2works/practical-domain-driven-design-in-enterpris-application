package com.example.cargotracker.routing.domain.model.aggregates;

import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.ROTTERDAM;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.SINGAPORE;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.TOKYO;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.at;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.direct;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.feeder;
import static com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseTest.fromSingapore;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 経路の確定（US-07 AC1・AC2、R-INV-03〜06。Bolt 19）と経路版の一覧（Bolt 17 レビュー D-64）。
 *
 * <p>候補は判定時刻 2026-10-07T03:00Z に算出する。候補 1 は直行 V-EARLY（10-08T00:00Z 出発）、候補 2 は SGSIN で 10 時間の
 * 接続の積替え V-FEED → V-CONN、候補 3 は期限超過の直行 V-LATE（除外）。
 */
class RoutingCaseConfirmationTest {

    static final UtcInstant JUDGED_AT = at("2026-10-07T03:00:00Z");
    static final UtcInstant COMMIT_AT = at("2026-10-07T04:00:00Z");
    static final UtcInstant FIRST_DEPARTURE = at("2026-10-08T00:00:00Z");
    static final RouteApprover ROUTE_DESIGNER = new RouteApprover(UUID.randomUUID(), true);
    static final ConnectionRule SINGAPORE_8H =
            new ConnectionRule(UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null);
    /** 判定の後（確定の前）に有効になる厳しい規則（T-41。8 時間と違う値）。 */
    static final ConnectionRule SINGAPORE_12H_FROM_0330 =
            new ConnectionRule(UUID.randomUUID(), SINGAPORE, Duration.ofHours(12), at("2026-10-07T03:30:00Z"), null);

    private final RouteCandidateFinder finder = new RouteCandidateFinder();
    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();

    @Test
    void 適合の候補を根拠を付けて経路設計者が確定すると経路版が確定しDE05が返る() {
        RoutingCase routingCase = presented();

        RouteConfirmed event = routingCase.confirm(
                2, "  SGSIN の接続に 2 時間の余裕がある。  ", ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator);

        RouteVersion version = routingCase.routeVersion();
        assertThat(version.status()).isEqualTo(RouteVersionStatus.CONFIRMED);
        assertThat(version.confirmation())
                .contains(new RouteConfirmation(
                        2, new DecisionRationale("SGSIN の接続に 2 時間の余裕がある。"), ROUTE_DESIGNER.userId(), COMMIT_AT));
        assertThat(version.candidates()).isEqualTo(presented().routeVersion().candidates());
        assertThat(routingCase.confirmedRouteVersion())
                .map(RouteVersion::routeVersionNo)
                .contains(1);
        assertThat(event)
                .isEqualTo(new RouteConfirmed(
                        routingCase.id().value(),
                        "RC-2026-0001",
                        1,
                        routingCase.quotationId(),
                        routingCase.transportRequestId(),
                        1,
                        ROUTE_DESIGNER.userId(),
                        COMMIT_AT,
                        List.of("V-FEED@1", "V-CONN@1")));
    }

    @ParameterizedTest(name = "根拠が「{0}」なら確定しない")
    @ValueSource(strings = {"", " ", "　\t\n"})
    void 根拠が空なら確定しない(String rationale) {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(1, rationale, ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator),
                RouteConfirmationRejectionReason.RATIONALE_MISSING);
        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CANDIDATES_PRESENTED);
    }

    @Test
    void 根拠がnullなら確定しない() {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(1, null, ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator),
                RouteConfirmationRejectionReason.RATIONALE_MISSING);
    }

    @ParameterizedTest(name = "根拠が {0} 文字なら確定する")
    @ValueSource(ints = {1, DecisionRationale.MAX_LENGTH})
    void 根拠は1文字から4000文字まで(int length) {
        RoutingCase routingCase = presented();

        routingCase.confirm(1, "あ".repeat(length), ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator);

        assertThat(routingCase.routeVersion().confirmation())
                .map(confirmation -> confirmation.rationale().text().length())
                .contains(length);
    }

    @Test
    void 根拠が4001文字なら確定しない() {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(
                        1,
                        "あ".repeat(DecisionRationale.MAX_LENGTH + 1),
                        ROUTE_DESIGNER,
                        List.of(SINGAPORE_8H),
                        COMMIT_AT,
                        evaluator),
                RouteConfirmationRejectionReason.RATIONALE_TOO_LONG);
    }

    @Test
    void 経路設計者でない承認者は確定できない() {
        RoutingCase routingCase = presented();
        RouteApprover sales = new RouteApprover(UUID.randomUUID(), false);

        assertRejected(
                () -> routingCase.confirm(1, "根拠", sales, List.of(SINGAPORE_8H), COMMIT_AT, evaluator),
                RouteConfirmationRejectionReason.NOT_ROUTE_DESIGNER);
        assertThat(routingCase.confirmedRouteVersion()).isEmpty();
    }

    @Test
    void 除外の候補は確定できない() {
        RoutingCase routingCase = presented();
        assertThat(routingCase.routeVersion().candidates().get(2).evaluation().conforming())
                .as("前提: 候補 3 は除外")
                .isFalse();

        assertRejected(
                () -> routingCase.confirm(3, "根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator),
                RouteConfirmationRejectionReason.CANDIDATE_EXCLUDED);
    }

    @ParameterizedTest(name = "候補番号 {0} は経路版にない")
    @ValueSource(ints = {0, 4, 99})
    void ない候補は確定できない(int candidateNo) {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(
                        candidateNo, "根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator),
                RouteConfirmationRejectionReason.CANDIDATE_NOT_FOUND);
    }

    @Test
    void 確定の時刻で有効な規則で判定し直して不適合なら確定しない() {
        RoutingCase routingCase = presented();
        List<ConnectionRule> rules = List.of(SINGAPORE_8H, SINGAPORE_12H_FROM_0330);

        assertRejected(
                () -> routingCase.confirm(2, "根拠", ROUTE_DESIGNER, rules, COMMIT_AT, evaluator),
                RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);
    }

    @Test
    void 確定の時刻の前に有効になった規則でも適合なら確定する() {
        RoutingCase routingCase = presented();
        ConnectionRule singapore10h = new ConnectionRule(
                UUID.randomUUID(), SINGAPORE, Duration.ofHours(10), at("2026-10-07T03:30:00Z"), null);

        routingCase.confirm(2, "根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H, singapore10h), COMMIT_AT, evaluator);

        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
    }

    @Test
    void 最初の区間の出発予定の1分前なら確定する() {
        RoutingCase routingCase = presented();

        routingCase.confirm(
                1, "根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H), minutesAfter(FIRST_DEPARTURE, -1), evaluator);

        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
    }

    @ParameterizedTest(name = "確定の時刻が最初の区間の出発予定の {0} 分後なら出発済み")
    @CsvSource({"0", "1"})
    void 最初の区間が出発済みなら確定しない(int minutesAfterDeparture) {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(
                        1,
                        "根拠",
                        ROUTE_DESIGNER,
                        List.of(SINGAPORE_8H),
                        minutesAfter(FIRST_DEPARTURE, minutesAfterDeparture),
                        evaluator),
                RouteConfirmationRejectionReason.ALREADY_DEPARTED);
    }

    @Test
    void 候補を算出する前の経路版は確定できない() {
        RoutingCase routingCase = RoutingCaseTest.open();

        assertRejected(
                () -> routingCase.confirm(1, "根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator),
                RouteConfirmationRejectionReason.NOT_CONFIRMABLE_STATE);
    }

    @Test
    void 確定した経路版は確定し直せない() {
        RoutingCase routingCase = presented();
        routingCase.confirm(1, "根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator);

        assertRejected(
                () -> routingCase.confirm(
                        2, "別の根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H), minutesAfter(COMMIT_AT, 1), evaluator),
                RouteConfirmationRejectionReason.NOT_CONFIRMABLE_STATE);
        assertThat(routingCase.routeVersion().confirmation())
                .map(RouteConfirmation::candidateNo)
                .contains(1);
    }

    @Test
    void 確定した経路版の候補は算出し直せない() {
        RoutingCase routingCase = presented();
        routingCase.confirm(1, "根拠", ROUTE_DESIGNER, List.of(SINGAPORE_8H), COMMIT_AT, evaluator);
        List<RouteCandidate> confirmedCandidates = routingCase.routeVersion().candidates();

        assertThatThrownBy(() -> routingCase.calculateCandidates(
                        List.of(direct("V-NEW", "2026-10-30T00:00:00Z")),
                        List.of(SINGAPORE_8H),
                        minutesAfter(COMMIT_AT, 1),
                        finder,
                        evaluator))
                .isInstanceOf(IllegalStateException.class);
        assertThat(routingCase.routeVersion().candidates()).isEqualTo(confirmedCandidates);
    }

    @Test
    void 案件は経路版の一覧を持ち算出は最新の経路版だけを書き換える() {
        RouteVersion superseded = presented().routeVersion();
        RouteVersion old = new RouteVersion(
                1, RouteVersionStatus.SUPERSEDED, superseded.candidates(), superseded.candidatesEvaluatedAt());
        RoutingCase routingCase = RoutingCase.reconstitute(
                new RoutingCaseId(UUID.randomUUID()),
                new RoutingCaseNumber(2026, 1),
                UUID.randomUUID(),
                "TR-2026-0001",
                1,
                UUID.randomUUID(),
                List.of(),
                new RouteSpecification(TOKYO, ROTTERDAM, RoutingCaseTest.DEADLINE, "GENERAL"),
                at("2026-10-06T02:00:00Z"),
                List.of(old, RouteVersion.draft(2)),
                5);

        routingCase.calculateCandidates(
                List.of(direct("V-NEW", "2026-10-30T00:00:00Z")), List.of(SINGAPORE_8H), COMMIT_AT, finder, evaluator);

        assertThat(routingCase.routeVersions())
                .extracting(RouteVersion::routeVersionNo, RouteVersion::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, RouteVersionStatus.SUPERSEDED),
                        org.assertj.core.groups.Tuple.tuple(2, RouteVersionStatus.CANDIDATES_PRESENTED));
        assertThat(routingCase.routeVersions().getFirst()).isEqualTo(old);
        assertThat(routingCase.routeVersion().routeVersionNo()).isEqualTo(2);
    }

    @Test
    void 確定した経路版は案件に1つだけ() {
        RouteVersion presentedVersion = presented().routeVersion();
        RouteVersion confirmedV1 = new RouteVersion(
                1,
                RouteVersionStatus.CONFIRMED,
                presentedVersion.candidates(),
                presentedVersion.candidatesEvaluatedAt(),
                new RouteConfirmation(1, new DecisionRationale("根拠"), ROUTE_DESIGNER.userId(), COMMIT_AT));

        assertThatThrownBy(() -> RoutingCase.reconstitute(
                        new RoutingCaseId(UUID.randomUUID()),
                        new RoutingCaseNumber(2026, 1),
                        UUID.randomUUID(),
                        "TR-2026-0001",
                        1,
                        UUID.randomUUID(),
                        List.of(),
                        new RouteSpecification(TOKYO, ROTTERDAM, RoutingCaseTest.DEADLINE, "GENERAL"),
                        at("2026-10-06T02:00:00Z"),
                        List.of(confirmedV1, withNo(confirmedV1, 2)),
                        5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 確定の経路版は確定の記録を持ちほかの状態は持たない() {
        RouteVersion presentedVersion = presented().routeVersion();
        RouteConfirmation confirmation =
                new RouteConfirmation(1, new DecisionRationale("根拠"), ROUTE_DESIGNER.userId(), COMMIT_AT);

        assertThatThrownBy(() -> new RouteVersion(
                        1,
                        RouteVersionStatus.CONFIRMED,
                        presentedVersion.candidates(),
                        presentedVersion.candidatesEvaluatedAt(),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteVersion(
                        1,
                        RouteVersionStatus.CANDIDATES_PRESENTED,
                        presentedVersion.candidates(),
                        presentedVersion.candidatesEvaluatedAt(),
                        confirmation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 候補 3 件（適合 2、除外 1）を算出した案件。 */
    private RoutingCase presented() {
        RoutingCase routingCase = RoutingCaseTest.open();
        routingCase.calculateCandidates(
                List.of(
                        direct("V-EARLY", "2026-10-29T00:00:00Z"),
                        feeder("V-FEED", "2026-10-09T00:00:00Z"),
                        fromSingapore("V-CONN", "2026-10-09T10:00:00Z", "2026-10-30T00:00:00Z"),
                        direct("V-LATE", "2026-11-03T12:00:00Z")),
                List.of(SINGAPORE_8H),
                JUDGED_AT,
                finder,
                evaluator);
        return routingCase;
    }

    private static RouteVersion withNo(RouteVersion version, int routeVersionNo) {
        return new RouteVersion(
                routeVersionNo,
                version.status(),
                version.candidates(),
                version.candidatesEvaluatedAt(),
                version.confirmation().orElse(null));
    }

    private static UtcInstant minutesAfter(UtcInstant instant, int minutes) {
        return new UtcInstant(instant.instant().plus(Duration.ofMinutes(minutes)));
    }

    private static void assertRejected(ThrowingCallable confirmation, RouteConfirmationRejectionReason reason) {
        assertThatThrownBy(confirmation)
                .isInstanceOfSatisfying(
                        RouteConfirmationRejected.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(reason));
    }
}

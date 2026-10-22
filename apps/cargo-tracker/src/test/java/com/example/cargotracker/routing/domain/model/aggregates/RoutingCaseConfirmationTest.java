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
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.Location;
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
 * 接続の積替え V-FEED → V-CONN（シンガポールを 10-09T10:00Z に出発）、候補 3 は期限超過の直行 V-LATE（除外）。
 *
 * <p>確定では規則を読み直して同じ判定にかける。規則は接続の時刻（次の区間の出発予定）に有効なものを使う（D-90、Bolt 37）。
 *
 * <p>確定では航海も読み直し、区間の情報版と比べ、確定の時刻で情報不足を判定する（US-07 AC3。Bolt 37c）。航海の取得時刻は
 * 2026-10-07T01:00Z（判定時刻の 2 時間前）。
 */
class RoutingCaseConfirmationTest {

    static final UtcInstant JUDGED_AT = at("2026-10-07T03:00:00Z");
    static final UtcInstant COMMIT_AT = at("2026-10-07T04:00:00Z");
    static final UtcInstant FIRST_DEPARTURE = at("2026-10-08T00:00:00Z");
    /** 候補 2 のシンガポールでの次の区間（V-CONN）の出発予定。規則はこの時刻で選ぶ。 */
    static final UtcInstant CONNECTION_DEPARTURE = at("2026-10-09T10:00:00Z");

    static final RouteApprover ROUTE_DESIGNER = new RouteApprover(UUID.randomUUID(), true);
    static final ConnectionRule SINGAPORE_8H =
            new ConnectionRule(UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), null);
    /** 算出の後に登録された厳しい規則（T-41。8 時間と違う値）。算出には渡さず、確定で読み直す。 */
    static final ConnectionRule SINGAPORE_12H_REGISTERED_AFTER_CALCULATION =
            new ConnectionRule(UUID.randomUUID(), SINGAPORE, Duration.ofHours(12), at("2026-01-01T00:00:00Z"), null);

    /** 情報鮮度の判定（既定の許容時間。Bolt 37b2）。 */
    static final InfoFreshnessPolicy FRESHNESS = new InfoFreshnessPolicy(Duration.ofHours(24));

    /** 候補を算出した航海（確定で読み直したとき変わっていなければ、この一覧を渡す）。 */
    static final List<Voyage> VOYAGES = List.of(
            direct("V-EARLY", "2026-10-29T00:00:00Z"),
            feeder("V-FEED", "2026-10-09T00:00:00Z"),
            fromSingapore("V-CONN", CONNECTION_DEPARTURE.instant().toString(), "2026-10-30T00:00:00Z"),
            direct("V-LATE", "2026-11-03T12:00:00Z"));

    private final RouteCandidateFinder finder = new RouteCandidateFinder();
    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();

    @Test
    void 適合の候補を根拠を付けて経路設計者が確定すると経路版が確定しDE05が返る() {
        RoutingCase routingCase = presented();

        RouteConfirmed event = routingCase.confirm(
                2,
                "  SGSIN の接続に 2 時間の余裕がある。  ",
                ROUTE_DESIGNER,
                VOYAGES,
                List.of(SINGAPORE_8H),
                COMMIT_AT,
                evaluator,
                FRESHNESS);

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
                () -> routingCase.confirm(
                        1, rationale, ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.RATIONALE_MISSING);
        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CANDIDATES_PRESENTED);
    }

    @Test
    void 根拠がnullなら確定しない() {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(
                        1, null, ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.RATIONALE_MISSING);
    }

    @ParameterizedTest(name = "根拠が {0} 文字なら確定する")
    @ValueSource(ints = {1, DecisionRationale.MAX_LENGTH})
    void 根拠は1文字から4000文字まで(int length) {
        RoutingCase routingCase = presented();

        routingCase.confirm(
                1, "あ".repeat(length), ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS);

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
                        VOYAGES,
                        List.of(SINGAPORE_8H),
                        COMMIT_AT,
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.RATIONALE_TOO_LONG);
    }

    @Test
    void 経路設計者でない承認者は確定できない() {
        RoutingCase routingCase = presented();
        RouteApprover sales = new RouteApprover(UUID.randomUUID(), false);

        assertRejected(
                () -> routingCase.confirm(
                        1, "根拠", sales, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
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
                () -> routingCase.confirm(
                        3, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.CANDIDATE_EXCLUDED);
    }

    @ParameterizedTest(name = "候補番号 {0} は経路版にない")
    @ValueSource(ints = {0, 4, 99})
    void ない候補は確定できない(int candidateNo) {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(
                        candidateNo,
                        "根拠",
                        ROUTE_DESIGNER,
                        VOYAGES,
                        List.of(SINGAPORE_8H),
                        COMMIT_AT,
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.CANDIDATE_NOT_FOUND);
    }

    @Test
    void 確定のときに読み直した規則で判定し直して不適合なら確定しない() {
        RoutingCase routingCase = presented();
        List<ConnectionRule> rules = List.of(SINGAPORE_8H, SINGAPORE_12H_REGISTERED_AFTER_CALCULATION);

        assertRejected(
                () -> routingCase.confirm(2, "根拠", ROUTE_DESIGNER, VOYAGES, rules, COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);
    }

    @Test
    void 確定のときに読み直した規則が厳しくなっても適合なら確定する() {
        RoutingCase routingCase = presented();
        ConnectionRule singapore10h = new ConnectionRule(
                UUID.randomUUID(), SINGAPORE, Duration.ofHours(10), at("2026-01-01T00:00:00Z"), null);

        routingCase.confirm(
                2, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H, singapore10h), COMMIT_AT, evaluator, FRESHNESS);

        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
    }

    @Test
    void 最初の区間の出発予定の1分前なら確定する() {
        RoutingCase routingCase = presented();

        routingCase.confirm(
                1,
                "根拠",
                ROUTE_DESIGNER,
                VOYAGES,
                List.of(SINGAPORE_8H),
                minutesAfter(FIRST_DEPARTURE, -1),
                evaluator,
                FRESHNESS);

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
                        VOYAGES,
                        List.of(SINGAPORE_8H),
                        minutesAfter(FIRST_DEPARTURE, minutesAfterDeparture),
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.ALREADY_DEPARTED);
    }

    @Test
    void 候補を算出する前の経路版は確定できない() {
        RoutingCase routingCase = RoutingCaseTest.open();

        assertRejected(
                () -> routingCase.confirm(
                        1, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.NOT_CONFIRMABLE_STATE);
    }

    @Test
    void 確定した経路版は確定し直せない() {
        RoutingCase routingCase = presented();
        routingCase.confirm(1, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS);

        assertRejected(
                () -> routingCase.confirm(
                        2,
                        "別の根拠",
                        ROUTE_DESIGNER,
                        VOYAGES,
                        List.of(SINGAPORE_8H),
                        minutesAfter(COMMIT_AT, 1),
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.NOT_CONFIRMABLE_STATE);
        assertThat(routingCase.routeVersion().confirmation())
                .map(RouteConfirmation::candidateNo)
                .contains(1);
    }

    @Test
    void 確定した経路版の候補は算出し直せない() {
        RoutingCase routingCase = presented();
        routingCase.confirm(1, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS);
        List<RouteCandidate> confirmedCandidates = routingCase.routeVersion().candidates();
        List<Voyage> voyages = List.of(direct("V-NEW", "2026-10-30T00:00:00Z"));
        List<ConnectionRule> rules = List.of(SINGAPORE_8H);
        UtcInstant later = minutesAfter(COMMIT_AT, 1);
        InfoFreshnessPolicy freshness = new InfoFreshnessPolicy(Duration.ofHours(24));

        assertThatThrownBy(() -> routingCase.calculateCandidates(voyages, rules, later, finder, evaluator, freshness))
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
                null,
                null,
                List.of(old, RouteVersion.draft(2)),
                5);

        routingCase.calculateCandidates(
                List.of(direct("V-NEW", "2026-10-30T00:00:00Z")),
                List.of(SINGAPORE_8H),
                COMMIT_AT,
                finder,
                evaluator,
                new InfoFreshnessPolicy(Duration.ofHours(24)));

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

        RoutingCaseId id = new RoutingCaseId(UUID.randomUUID());
        RoutingCaseNumber number = new RoutingCaseNumber(2026, 1);
        UUID transportRequestId = UUID.randomUUID();
        UUID quotationId = UUID.randomUUID();
        RouteSpecification specification =
                new RouteSpecification(TOKYO, ROTTERDAM, RoutingCaseTest.DEADLINE, "GENERAL");
        UtcInstant requestedAt = at("2026-10-06T02:00:00Z");
        List<RouteVersion> twoConfirmed = List.of(confirmedV1, withNo(confirmedV1, 2));
        List<Location> noVia = List.of();

        assertThatThrownBy(() -> RoutingCase.reconstitute(
                        id,
                        number,
                        transportRequestId,
                        "TR-2026-0001",
                        1,
                        quotationId,
                        noVia,
                        specification,
                        requestedAt,
                        null,
                        null,
                        twoConfirmed,
                        5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 確定の経路版は確定の記録を持ちほかの状態は持たない() {
        RouteVersion presentedVersion = presented().routeVersion();
        RouteConfirmation confirmation =
                new RouteConfirmation(1, new DecisionRationale("根拠"), ROUTE_DESIGNER.userId(), COMMIT_AT);
        List<RouteCandidate> candidates = presentedVersion.candidates();
        UtcInstant evaluatedAt = presentedVersion.candidatesEvaluatedAt();

        assertThatThrownBy(() -> new RouteVersion(1, RouteVersionStatus.CONFIRMED, candidates, evaluatedAt, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteVersion(
                        1, RouteVersionStatus.CANDIDATES_PRESENTED, candidates, evaluatedAt, confirmation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 接続の時刻と同時刻に有効になる厳しい規則で判定し直して不適合なら確定しない() {
        RoutingCase routingCase = presented();
        ConnectionRule strictFromConnection =
                new ConnectionRule(UUID.randomUUID(), SINGAPORE, Duration.ofHours(12), CONNECTION_DEPARTURE, null);

        assertRejected(
                () -> routingCase.confirm(
                        2,
                        "根拠",
                        ROUTE_DESIGNER,
                        VOYAGES,
                        List.of(SINGAPORE_8H, strictFromConnection),
                        COMMIT_AT,
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);
    }

    @Test
    void 接続の時刻の1分後に有効になる規則では判定しない() {
        RoutingCase routingCase = presented();
        ConnectionRule strictLater = new ConnectionRule(
                UUID.randomUUID(), SINGAPORE, Duration.ofHours(12), minutesAfter(CONNECTION_DEPARTURE, 1), null);

        routingCase.confirm(
                2, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H, strictLater), COMMIT_AT, evaluator, FRESHNESS);

        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
    }

    @Test
    void 適用開始が確定の時刻より後で接続の時刻より前の規則は確定で使う() {
        // D-90: 確定の時刻にはまだ有効でなくても、積み替える時刻に有効な規則で判定する（Bolt 19 の確認ポイント 3 の変更）
        RoutingCase routingCase = presented();
        ConnectionRule strictAfterCommit = new ConnectionRule(
                UUID.randomUUID(), SINGAPORE, Duration.ofHours(12), minutesAfter(COMMIT_AT, 1), null);

        assertRejected(
                () -> routingCase.confirm(
                        2,
                        "根拠",
                        ROUTE_DESIGNER,
                        VOYAGES,
                        List.of(SINGAPORE_8H, strictAfterCommit),
                        COMMIT_AT,
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);
    }

    @Test
    void 確定の時刻に有効でも接続の時刻の前に終わり後継のない規則なら判定し直して不適合() {
        // D-90 で確定できなくなる向き: 確定の時刻には有効でも、積み替える時刻に有効な規則がなければ接続できない
        RoutingCase routingCase = presented();
        ConnectionRule endsBeforeConnection = new ConnectionRule(
                UUID.randomUUID(),
                SINGAPORE,
                Duration.ofHours(8),
                at("2026-01-01T00:00:00Z"),
                minutesAfter(CONNECTION_DEPARTURE, -1));

        assertRejected(
                () -> routingCase.confirm(
                        2,
                        "根拠",
                        ROUTE_DESIGNER,
                        VOYAGES,
                        List.of(endsBeforeConnection),
                        COMMIT_AT,
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);
    }

    @Test
    void 規則が接続の時刻に終わり厳しい規則に置き換わっていれば判定し直して不適合() {
        RoutingCase routingCase = presented();
        ConnectionRule endsAtConnection = new ConnectionRule(
                UUID.randomUUID(), SINGAPORE, Duration.ofHours(8), at("2026-01-01T00:00:00Z"), CONNECTION_DEPARTURE);
        ConnectionRule replacement =
                new ConnectionRule(UUID.randomUUID(), SINGAPORE, Duration.ofHours(12), CONNECTION_DEPARTURE, null);

        assertRejected(
                () -> routingCase.confirm(
                        2,
                        "根拠",
                        ROUTE_DESIGNER,
                        VOYAGES,
                        List.of(endsAtConnection, replacement),
                        COMMIT_AT,
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);
    }

    @Test
    void 判断根拠の文字数はコードポイントで数え前後の空白を除いてから数える() {
        String surrogate = "\uD867\uDE3D"; // 𩸽（UTF-16 では 2 単位）
        RoutingCase routingCase = presented();

        routingCase.confirm(
                1,
                " " + surrogate.repeat(DecisionRationale.MAX_LENGTH) + "\n",
                ROUTE_DESIGNER,
                VOYAGES,
                List.of(SINGAPORE_8H),
                COMMIT_AT,
                evaluator,
                FRESHNESS);

        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
        RoutingCase another = presented();
        String tooLong = surrogate.repeat(DecisionRationale.MAX_LENGTH + 1);
        assertRejected(
                () -> another.confirm(
                        1, tooLong, ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.RATIONALE_TOO_LONG);
    }

    @Test
    void 判断根拠の拒否の理由は値オブジェクトが決める() {
        assertThat(DecisionRationale.rejectionOf(null)).contains(RouteConfirmationRejectionReason.RATIONALE_MISSING);
        assertThat(DecisionRationale.rejectionOf(" \t")).contains(RouteConfirmationRejectionReason.RATIONALE_MISSING);
        assertThat(DecisionRationale.rejectionOf("あ".repeat(DecisionRationale.MAX_LENGTH + 1)))
                .contains(RouteConfirmationRejectionReason.RATIONALE_TOO_LONG);
        assertThat(DecisionRationale.rejectionOf(" 根拠 ")).isEmpty();
        assertThat(DecisionRationale.of(" 根拠 ")).isEqualTo(new DecisionRationale("根拠"));
    }

    @Test
    void 最初の区間が出発し二つ目の区間がまだなら出発済み() {
        RoutingCase routingCase = presented();

        assertRejected(
                () -> routingCase.confirm(
                        2,
                        "根拠",
                        ROUTE_DESIGNER,
                        VOYAGES,
                        List.of(SINGAPORE_8H),
                        minutesAfter(FIRST_DEPARTURE, 1),
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.ALREADY_DEPARTED);
    }

    @Test
    void 確かめる順は経路設計者と状態と候補と根拠の順() {
        RoutingCase routingCase = presented();
        RouteApprover sales = new RouteApprover(UUID.randomUUID(), false);

        assertRejected(
                () -> routingCase.confirm(
                        3, "", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.CANDIDATE_EXCLUDED);
        routingCase.confirm(1, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS);
        assertRejected(
                () -> routingCase.confirm(
                        1, "根拠", sales, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.NOT_ROUTE_DESIGNER);
    }

    @Test
    void 二つ目の区間の航海の情報版が更新されていれば確定せず候補は変えない() {
        RoutingCase routingCase = presented();
        List<Voyage> reloaded = withVoyage("V-CONN", "V-CONN@2", COMMIT_AT);

        assertRejected(
                () -> routingCase.confirm(
                        2, "根拠", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.INFO_VERSION_CHANGED);
        assertThat(routingCase.routeVersion()).isEqualTo(presented().routeVersion());
    }

    @Test
    void 区間の航海がなくなっていれば情報版の更新で確定しない() {
        RoutingCase routingCase = presented();
        List<Voyage> reloaded = VOYAGES.stream()
                .filter(voyage -> !voyage.voyageNumber().equals("V-FEED"))
                .toList();

        assertRejected(
                () -> routingCase.confirm(
                        2, "根拠", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.INFO_VERSION_CHANGED);
    }

    @Test
    void 候補に使っていない航海の情報版が更新されても確定する() {
        RoutingCase routingCase = presented();
        List<Voyage> reloaded = withVoyage("V-LATE", "V-LATE@2", COMMIT_AT);

        routingCase.confirm(2, "根拠", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS);

        assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
    }

    @Test
    void 候補は情報版の違う区間を区間の順に返す() {
        RouteCandidate candidate = presented().routeVersion().candidates().get(1);

        assertThat(candidate.legsWithChangedInfo(java.util.Map.of("V-FEED", "V-FEED@2", "V-CONN", "V-CONN@1")))
                .extracting(leg -> leg.voyageNumber())
                .containsExactly("V-FEED");
        assertThat(candidate.legsWithChangedInfo(java.util.Map.of("V-CONN", "V-CONN@3")))
                .extracting(leg -> leg.voyageNumber())
                .containsExactly("V-FEED", "V-CONN");
        assertThat(candidate.legsWithChangedInfo(java.util.Map.of("V-FEED", "V-FEED@1", "V-CONN", "V-CONN@1")))
                .isEmpty();
    }

    @Test
    void 情報版の更新は判断根拠の不足より先に確かめる() {
        RoutingCase routingCase = presented();
        List<Voyage> reloaded = withVoyage("V-CONN", "V-CONN@2", COMMIT_AT);

        assertRejected(
                () -> routingCase.confirm(
                        2, " ", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.INFO_VERSION_CHANGED);
    }

    @Test
    void 情報版の更新は出発済みより先に確かめる() {
        RoutingCase routingCase = presented();
        List<Voyage> reloaded = withVoyage("V-EARLY", "V-EARLY@2", COMMIT_AT);
        UtcInstant afterDeparture = minutesAfter(FIRST_DEPARTURE, 1);

        assertRejected(
                () -> routingCase.confirm(
                        1, "根拠", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), afterDeparture, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.INFO_VERSION_CHANGED);
    }

    @ParameterizedTest(name = "読み直した航海の取得が確定の時刻の 24 時間と {0} 秒前なら情報不足={1}")
    @CsvSource({"1, true", "0, false", "-1, false"})
    void 確定の時刻に読み直した航海の情報が許容時間を過ぎていれば情報不足で確定しない(long secondsBeyond, boolean insufficient) {
        // 区間の写しの取得時刻（10-07T01:00Z）は確定の時刻の 3 時間前で新しい。判定は読み直した航海の取得時刻で行う
        RoutingCase routingCase = presented();
        UtcInstant acquiredAt =
                new UtcInstant(COMMIT_AT.instant().minus(Duration.ofHours(24)).minusSeconds(secondsBeyond));
        List<Voyage> reloaded = withVoyage("V-EARLY", "V-EARLY@1", acquiredAt);

        if (insufficient) {
            assertRejected(
                    () -> routingCase.confirm(
                            1, "根拠", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                    RouteConfirmationRejectionReason.INFO_INSUFFICIENT);
        } else {
            routingCase.confirm(
                    1, "根拠", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS);
            assertThat(routingCase.routeVersion().status()).isEqualTo(RouteVersionStatus.CONFIRMED);
        }
    }

    @Test
    void 二つ目の区間の航海だけが古くても情報不足で確定しない() {
        RoutingCase routingCase = presented();
        UtcInstant stale = new UtcInstant(COMMIT_AT.instant().minus(Duration.ofHours(30)));
        List<Voyage> reloaded = withVoyage("V-CONN", "V-CONN@1", stale);

        assertRejected(
                () -> routingCase.confirm(
                        2, "根拠", ROUTE_DESIGNER, reloaded, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.INFO_INSUFFICIENT);
    }

    @Test
    void 出発済みは情報不足より先に確かめる() {
        RoutingCase routingCase = presented();
        // 確定の時刻は最初の区間の出発（10-08T00:00Z）の後で、取得時刻（10-07T01:00Z）から 24 時間を超える
        UtcInstant commitAt = minutesAfter(ACQUIRED_AT, 25 * 60);

        assertRejected(
                () -> routingCase.confirm(
                        1, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), commitAt, evaluator, FRESHNESS),
                RouteConfirmationRejectionReason.ALREADY_DEPARTED);
    }

    @Test
    void 情報不足は規則の判定し直しより先に確かめる() {
        RoutingCase routingCase = presented();
        UtcInstant stale = new UtcInstant(COMMIT_AT.instant().minus(Duration.ofHours(30)));
        List<Voyage> reloaded = withVoyage("V-CONN", "V-CONN@1", stale);

        assertRejected(
                () -> routingCase.confirm(
                        2,
                        "根拠",
                        ROUTE_DESIGNER,
                        reloaded,
                        List.of(SINGAPORE_12H_REGISTERED_AFTER_CALCULATION),
                        COMMIT_AT,
                        evaluator,
                        FRESHNESS),
                RouteConfirmationRejectionReason.INFO_INSUFFICIENT);
    }

    @Test
    void 確定できるのは候補提示済みで確定した経路版がないとき() {
        RoutingCase draft = RoutingCaseTest.open();
        RoutingCase presented = presented();
        RoutingCase confirmed = presented();
        confirmed.confirm(1, "根拠", ROUTE_DESIGNER, VOYAGES, List.of(SINGAPORE_8H), COMMIT_AT, evaluator, FRESHNESS);

        assertThat(draft.confirmable()).isFalse();
        assertThat(presented.confirmable()).isTrue();
        assertThat(confirmed.confirmable()).isFalse();
    }

    /** 候補 3 件（適合 2、除外 1）を算出した案件。 */
    private RoutingCase presented() {
        RoutingCase routingCase = RoutingCaseTest.open();
        routingCase.calculateCandidates(VOYAGES, List.of(SINGAPORE_8H), JUDGED_AT, finder, evaluator, FRESHNESS);
        return routingCase;
    }

    /** 確定で読み直した航海の一覧（1 本だけ、情報版と取得時刻を変える。寄港は変えない）。 */
    private static List<Voyage> withVoyage(String voyageNumber, String infoVersion, UtcInstant acquiredAt) {
        return VOYAGES.stream()
                .map(voyage -> voyage.voyageNumber().equals(voyageNumber)
                        ? new Voyage(voyageNumber, voyage.portCalls(), infoVersion, acquiredAt)
                        : voyage)
                .toList();
    }

    /** 区間の航海の取得時刻（算出のとき）。 */
    private static final UtcInstant ACQUIRED_AT = at("2026-10-07T01:00:00Z");

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

package com.example.cargotracker.routing.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.GoldenDatasetCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 経路候補の比較（US-06）の業務ルール層のステップ。経路設計案件は DE-16 の配信で作られたものを使う（シナリオに 1 件）。
 */
public class RoutingSteps {

    /** 経路設計者（業務ルール層のシナリオの操作者）。 */
    static final UserId ROUTE_DESIGNER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000501"));

    private static final UtcInstant INFO_ACQUIRED_AT = new UtcInstant(Instant.parse("2026-10-07T01:00:00Z"));
    private static final Map<String, ExclusionReasonCode> REASONS = Map.of(
            "期限超過", ExclusionReasonCode.DEADLINE_EXCEEDED,
            "接続不足", ExclusionReasonCode.CONNECTION_TOO_SHORT,
            "接続できない", ExclusionReasonCode.NOT_CONNECTABLE,
            "情報不足", ExclusionReasonCode.INFO_INSUFFICIENT);

    private final RoutingCaseCommandService commandService;
    private final RoutingCaseQueryService queryService;
    private final InMemoryVoyageRepository voyages;
    private final InMemoryConnectionRuleRepository rules;
    private final MutableClock clock;
    private CandidateCalculationOutcome outcome;
    private GoldenDatasetCase goldenCase;

    public RoutingSteps(
            RoutingCaseCommandService commandService,
            RoutingCaseQueryService queryService,
            InMemoryVoyageRepository voyages,
            InMemoryConnectionRuleRepository rules,
            MutableClock clock) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.voyages = voyages;
        this.rules = rules;
        this.clock = clock;
    }

    @前提("港 {string} の必要最小接続時間は {int} 時間である")
    public void 港の必要最小接続時間(String port, int hours) {
        rules.add(new ConnectionRule(
                UUID.randomUUID(),
                new Location(port),
                Duration.ofHours(hours),
                new UtcInstant(Instant.parse("2026-01-01T00:00:00Z")),
                null));
    }

    /** 適用の終わりのある規則（開始は 2026-01-01。終わりを含まない）。後継の規則は置かない（D-90 の境界）。 */
    @前提("港 {string} の必要最小接続時間 {int} 時間の規則の適用が {string} に終わる")
    public void 規則の適用が終わる(String port, int hours, String validTo) {
        rules.add(new ConnectionRule(
                UUID.randomUUID(),
                new Location(port),
                Duration.ofHours(hours),
                new UtcInstant(Instant.parse("2026-01-01T00:00:00Z")),
                at(validTo)));
    }

    /** 航海を入れる。「取得時刻」の列は省略でき、空なら既定の取得時刻（判定時刻の 2 時間前）にする（Bolt 37b2）。 */
    @前提("次の航海がある")
    public void 次の航海がある(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            String number = row.get("航海番号");
            String acquiredAt = blankToNull(row.get("取得時刻"));
            voyages.add(new Voyage(
                    number,
                    List.of(
                            new PortCall(new Location(row.get("積地")), null, at(row.get("出発予定"))),
                            new PortCall(new Location(row.get("揚地")), at(row.get("到着予定")), null)),
                    number + "@1",
                    acquiredAt == null ? INFO_ACQUIRED_AT : at(acquiredAt)));
        }
    }

    /**
     * Golden dataset v0 の案件の規則と航海を入れる（Bolt 37f）。案件の条件は背景の輸送条件で決まるので、案件のデータの条件と一致する
     * ことを確かめる。
     */
    @前提("Golden dataset v0 の案件 {string} の規則と航海を読み込む")
    public void Golden_datasetの案件を読み込む(String id) {
        goldenCase = GoldenDatasetCase.load(id);
        RoutingCaseSummary routingCase = onlyCase();
        assertThat(routingCase.origin())
                .isEqualTo(goldenCase.routeSpecification().origin());
        assertThat(routingCase.destination())
                .isEqualTo(goldenCase.routeSpecification().destination());
        assertThat(routingCase.arrivalDeadline())
                .isEqualTo(goldenCase.routeSpecification().arrivalDeadline());
        assertThat(queryService
                        .findByNumber(routingCase.number())
                        .orElseThrow()
                        .specification()
                        .cargoCategory())
                .isEqualTo(goldenCase.routeSpecification().cargoCategory());
        goldenCase.rules().forEach(rules::add);
        goldenCase.voyages().forEach(voyages::add);
    }

    /** 判定時刻は背景の最後の時刻（詳細経路設計の依頼）より後にする（時計を戻さない）。 */
    @前提("現在時刻を案件の判定時刻にする")
    public void 現在時刻を案件の判定時刻にする() {
        assertThat(goldenCase.judgedAt().instant()).isAfterOrEqualTo(clock.instant());
        clock.setInstant(goldenCase.judgedAt().instant());
    }

    /**
     * 適合の候補の番号を「,」でつないだもの（なければ「なし」）。実際の候補と、案件のデータの期待結果の両方を例の列と比べる（例の表を
     * 案件のデータと食い違わせない）。
     */
    @ならば("適合候補は {string} である")
    public void 適合候補は(String expected) {
        List<RouteCandidate> candidates = currentCase().routeVersion().candidates();
        assertThat(conformingSummary(candidates.stream()
                        .map(candidate -> new Summary(
                                candidate.candidateNo(), candidate.evaluation().conforming(), List.of()))
                        .toList()))
                .isEqualTo(expected);
        assertThat(conformingSummary(expectedSummaries())).as("案件のデータの期待結果").isEqualTo(expected);
    }

    /**
     * 除外の候補の「番号:理由」を「,」でつないだもの（なければ「なし」）。理由は集約が付けた順に「・」でつなぎ、同じ理由は 1 つにする
     * （全体は集約の単位のテストが確かめる）。実際の候補と、案件のデータの期待結果の両方を例の列と比べる。
     */
    @ならば("除外候補と理由は {string} である")
    public void 除外候補と理由は(String expected) {
        List<RouteCandidate> candidates = currentCase().routeVersion().candidates();
        assertThat(excludedSummary(candidates.stream()
                        .map(candidate -> new Summary(
                                candidate.candidateNo(),
                                candidate.evaluation().conforming(),
                                candidate.evaluation().reasons().stream()
                                        .map(ExclusionReason::code)
                                        .toList()))
                        .toList()))
                .isEqualTo(expected);
        assertThat(excludedSummary(expectedSummaries())).as("案件のデータの期待結果").isEqualTo(expected);
    }

    /** 候補の要約（番号、適合か、理由）。 */
    private record Summary(int candidateNo, boolean conforming, List<ExclusionReasonCode> reasons) {}

    private List<Summary> expectedSummaries() {
        List<GoldenDatasetCase.ExpectedCandidate> expected = goldenCase.expected();
        return java.util.stream.IntStream.range(0, expected.size())
                .mapToObj(i -> new Summary(
                        i + 1, expected.get(i).conforming(), expected.get(i).reasons()))
                .toList();
    }

    private static String conformingSummary(List<Summary> summaries) {
        String conforming = summaries.stream()
                .filter(Summary::conforming)
                .map(summary -> String.valueOf(summary.candidateNo()))
                .collect(Collectors.joining(","));
        return conforming.isEmpty() ? "なし" : conforming;
    }

    private static String excludedSummary(List<Summary> summaries) {
        String excluded = summaries.stream()
                .filter(summary -> !summary.conforming())
                .map(summary -> summary.candidateNo() + ":"
                        + summary.reasons().stream()
                                .map(RoutingSteps::nameOf)
                                .distinct()
                                .collect(Collectors.joining("・")))
                .collect(Collectors.joining(","));
        return excluded.isEmpty() ? "なし" : excluded;
    }

    /**
     * 経路設計の案件がない（Golden dataset v0 の G-04。Bolt 37f）。提出で拒まれた特殊貨物が経路設計に届かないことの表明で、守りは提出の
     * 拒否にある（提出が通っても、審査・見積り・詳細経路設計の依頼を経ないので案件はできない）。
     */
    @ならば("経路設計案件はない")
    public void 経路設計案件はない() {
        assertThat(queryService.listCases()).isEmpty();
    }

    @ならば("経路設計案件が {int} 件あり、条件は {string} から {string}、希望到着期限は {string} である")
    public void 経路設計案件がある(int count, String origin, String destination, String deadline) {
        List<RoutingCaseSummary> cases = queryService.listCases();
        assertThat(cases).hasSize(count);
        assertThat(cases.getFirst().origin()).isEqualTo(new Location(origin));
        assertThat(cases.getFirst().destination()).isEqualTo(new Location(destination));
        assertThat(cases.getFirst().arrivalDeadline()).isEqualTo(at(deadline));
    }

    @もし("経路設計者が案件の候補を算出する")
    public void 経路設計者が案件の候補を算出する() {
        RoutingCaseSummary routingCase = onlyCase();
        outcome = commandService.calculateCandidates(new CalculateCandidatesCommand(
                routingCase.number(),
                queryService.findByNumber(routingCase.number()).orElseThrow().aggregateVersion(),
                ROUTE_DESIGNER));
    }

    @ならば("算出の結果は {int} 件で、適合は {int} 件、除外は {int} 件である")
    public void 算出の結果(int found, int conforming, int excluded) {
        assertThat(outcome).isInstanceOf(CandidateCalculationOutcome.Calculated.class);
        CandidateCalculation calculation = ((CandidateCalculationOutcome.Calculated) outcome).calculation();
        assertThat(calculation.found()).isEqualTo(found);
        assertThat(calculation.conforming()).isEqualTo(conforming);
        assertThat(calculation.excluded()).isEqualTo(excluded);
    }

    @ならば("候補は次のとおりである")
    public void 候補は次のとおりである(DataTable table) {
        List<RouteCandidate> candidates = currentCase().routeVersion().candidates();
        assertThat(candidates).hasSize(table.asMaps().size());
        for (Map<String, String> row : table.asMaps()) {
            RouteCandidate candidate = candidates.get(Integer.parseInt(row.get("候補")) - 1);
            assertThat(voyagesOf(candidate)).isEqualTo(row.get("航海"));
            assertThat(candidate.evaluation().conforming()).isEqualTo("適合".equals(row.get("判定")));
            assertThat(candidate.evaluation().estimatedArrivalAt()).isEqualTo(at(row.get("到着予定")));
            String slack = blankToNull(row.get("接続余裕"));
            assertThat(candidate
                            .evaluation()
                            .connectionSlack()
                            .map(Duration::toString)
                            .orElse(null))
                    .isEqualTo(slack);
            String reason = blankToNull(row.get("理由"));
            assertThat(candidate.evaluation().reasons())
                    .extracting(ExclusionReason::code)
                    .isEqualTo(reason == null ? List.of() : codesOf(reason));
        }
    }

    @ならば("除外された候補の理由は次のとおりである")
    public void 除外された候補の理由(DataTable table) {
        Map<String, RouteCandidate> byVoyages = currentCase().routeVersion().candidates().stream()
                .collect(Collectors.toMap(RoutingSteps::voyagesOf, candidate -> candidate));
        // 表にない除外の候補がなく、表の候補の理由はそれぞれ 1 つだけ（Bolt 17 レビュー D-63）
        assertThat(byVoyages.values().stream()
                        .filter(candidate -> !candidate.evaluation().conforming())
                        .map(RoutingSteps::voyagesOf))
                .containsExactlyInAnyOrderElementsOf(
                        table.asMaps().stream().map(row -> row.get("航海")).toList());
        for (Map<String, String> row : table.asMaps()) {
            RouteCandidate candidate = byVoyages.get(row.get("航海"));
            assertThat(candidate).as(row.get("航海")).isNotNull();
            assertThat(candidate.evaluation().reasons()).hasSize(1);
            ExclusionReason reason = candidate.evaluation().reasons().getFirst();
            assertThat(reason.code()).isEqualTo(REASONS.get(row.get("理由")));
            assertThat(reason.violatedAt()).isEqualTo(at(row.get("不適合の時刻")));
            assertThat(threshold(reason)).isEqualTo(row.get("閾値"));
            assertThat(reason.infoVersion()).isEqualTo(row.get("参照情報版"));
        }
    }

    /** 1 つの候補の理由を、並びも含めて確かめる（理由が複数の候補。Bolt 37b2）。 */
    @ならば("候補 {string} の理由は次のとおりである")
    public void 候補の理由(String voyageNumbers, DataTable table) {
        RouteCandidate candidate = candidateOf(voyageNumbers);
        List<ExclusionReason> reasons = candidate.evaluation().reasons();
        List<Map<String, String>> rows = table.asMaps();
        assertThat(reasons).hasSize(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            ExclusionReason reason = reasons.get(i);
            assertThat(reason.code()).isEqualTo(REASONS.get(row.get("理由")));
            assertThat(reason.violatedAt()).isEqualTo(at(row.get("不適合の時刻")));
            assertThat(threshold(reason)).isEqualTo(row.get("閾値"));
            assertThat(reason.infoVersion()).isEqualTo(row.get("参照情報版"));
        }
    }

    @ならば("候補 {string} の最も古い情報の取得時刻は {string} である")
    public void 候補の最も古い情報の取得時刻(String voyageNumbers, String acquiredAt) {
        assertThat(candidateOf(voyageNumbers).oldestInfoAcquiredAt()).isEqualTo(at(acquiredAt));
    }

    @ならば("どの候補も情報の取得時刻は {string} である")
    public void どの候補も情報の取得時刻(String acquiredAt) {
        assertThat(currentCase().routeVersion().candidates())
                .isNotEmpty()
                .allSatisfy(candidate ->
                        assertThat(candidate.oldestInfoAcquiredAt()).isEqualTo(at(acquiredAt)));
    }

    @ならば("候補の判定時刻は {string} である")
    public void 候補の判定時刻(String evaluatedAt) {
        assertThat(currentCase().routeVersion().evaluatedAt()).contains(at(evaluatedAt));
    }

    private RoutingCaseSummary onlyCase() {
        List<RoutingCaseSummary> cases = queryService.listCases();
        assertThat(cases).hasSize(1);
        return cases.getFirst();
    }

    private RoutingCase currentCase() {
        return queryService.findByNumber(onlyCase().number()).orElseThrow();
    }

    private RouteCandidate candidateOf(String voyageNumbers) {
        return currentCase().routeVersion().candidates().stream()
                .filter(candidate -> voyagesOf(candidate).equals(voyageNumbers))
                .findFirst()
                .orElseThrow(() -> new AssertionError("候補がありません: " + voyageNumbers));
    }

    private static String nameOf(ExclusionReasonCode code) {
        return REASONS.entrySet().stream()
                .filter(entry -> entry.getValue() == code)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("理由の表記の表にない理由: " + code));
    }

    private static List<ExclusionReasonCode> codesOf(String reasons) {
        return Arrays.stream(reasons.split(",")).map(REASONS::get).toList();
    }

    private static String voyagesOf(RouteCandidate candidate) {
        return candidate.legs().stream().map(Leg::voyageNumber).collect(Collectors.joining(","));
    }

    /** 閾値の表記（期限超過は期限の時刻、接続不足は港と必要最小接続時間、接続できないは港、情報不足は航海番号と許容時間）。 */
    private static String threshold(ExclusionReason reason) {
        return switch (reason.code()) {
            case DEADLINE_EXCEEDED -> reason.deadline().instant().toString();
            case CONNECTION_TOO_SHORT -> reason.port().unLocode() + " " + reason.requiredConnection();
            case INFO_INSUFFICIENT -> reason.voyageNumber() + " " + reason.freshnessTolerance();
            default -> reason.port().unLocode();
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

package com.example.cargotracker.routing.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 経路候補の比較（US-06）の業務ルール層のステップ。経路設計案件は DE-16 の配信で作られたものを使う（シナリオに 1 件）。
 */
public class RoutingSteps {

    private static final UtcInstant INFO_ACQUIRED_AT = new UtcInstant(Instant.parse("2026-10-01T06:10:00Z"));
    private static final Map<String, ExclusionReasonCode> REASONS = Map.of(
            "期限超過", ExclusionReasonCode.DEADLINE_EXCEEDED,
            "接続不足", ExclusionReasonCode.CONNECTION_TOO_SHORT,
            "接続できない", ExclusionReasonCode.NOT_CONNECTABLE);

    private final RoutingCaseCommandService commandService;
    private final RoutingCaseQueryService queryService;
    private final InMemoryVoyageRepository voyages;
    private final InMemoryConnectionRuleRepository rules;
    private CandidateCalculationOutcome outcome;

    public RoutingSteps(
            RoutingCaseCommandService commandService,
            RoutingCaseQueryService queryService,
            InMemoryVoyageRepository voyages,
            InMemoryConnectionRuleRepository rules) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.voyages = voyages;
        this.rules = rules;
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

    @前提("次の航海がある")
    public void 次の航海がある(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            String number = row.get("航海番号");
            voyages.add(new Voyage(
                    number,
                    List.of(
                            new PortCall(new Location(row.get("積地")), null, at(row.get("出発予定"))),
                            new PortCall(new Location(row.get("揚地")), at(row.get("到着予定")), null)),
                    number + "@1",
                    INFO_ACQUIRED_AT));
        }
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
        outcome = commandService.calculateCandidates(new CalculateCandidatesCommand(routingCase.number()));
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
                    .isEqualTo(reason == null ? List.of() : List.of(REASONS.get(reason)));
        }
    }

    @ならば("除外された候補の理由は次のとおりである")
    public void 除外された候補の理由(DataTable table) {
        Map<String, RouteCandidate> byVoyages = currentCase().routeVersion().candidates().stream()
                .collect(Collectors.toMap(RoutingSteps::voyagesOf, candidate -> candidate));
        for (Map<String, String> row : table.asMaps()) {
            RouteCandidate candidate = byVoyages.get(row.get("航海"));
            assertThat(candidate).as(row.get("航海")).isNotNull();
            ExclusionReason reason = candidate.evaluation().reasons().getFirst();
            assertThat(reason.code()).isEqualTo(REASONS.get(row.get("理由")));
            assertThat(reason.violatedAt()).isEqualTo(at(row.get("不適合の時刻")));
            assertThat(threshold(reason)).isEqualTo(row.get("閾値"));
            assertThat(reason.infoVersion()).isEqualTo(row.get("参照情報版"));
        }
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

    private static String voyagesOf(RouteCandidate candidate) {
        return candidate.legs().stream().map(Leg::voyageNumber).collect(Collectors.joining(","));
    }

    /** 閾値の表記（期限超過は期限の時刻、接続不足は港と必要最小接続時間、接続できないは港）。 */
    private static String threshold(ExclusionReason reason) {
        return switch (reason.code()) {
            case DEADLINE_EXCEEDED -> reason.deadline().instant().toString();
            case CONNECTION_TOO_SHORT -> reason.port().unLocode() + " " + reason.requiredConnection();
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

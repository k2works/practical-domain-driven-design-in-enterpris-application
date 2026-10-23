package com.example.cargotracker.routing.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.application.internal.commands.EvaluateConfirmedRouteCommand;
import com.example.cargotracker.routing.application.internal.commandservices.ConfirmedRouteEvaluationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 確定した経路の評価（US-07 AC4・AC5。Bolt 37d2）の業務ルール層のステップ。評価は外部情報の更新を受けたシステムの操作で、
 * 外部原本の取込（W7）の代わりにシナリオが航海を置き換えてから評価を起こす。案件はシナリオに 1 件。
 */
public class ConfirmedRouteEvaluationSteps {

    private static final Map<String, RedesignReasonCode> REASONS = Map.of(
            "期限超過", RedesignReasonCode.DEADLINE_EXCEEDED,
            "接続不足", RedesignReasonCode.CONNECTION_TOO_SHORT,
            "接続できない", RedesignReasonCode.NOT_CONNECTABLE,
            "航海の予定が使えない", RedesignReasonCode.VOYAGE_UNAVAILABLE);

    private final RoutingCaseCommandService commandService;
    private final RoutingCaseQueryService queryService;
    private final DeferredEventDelivery eventDelivery;
    private final InMemoryVoyageRepository voyages;
    private ConfirmedRouteEvaluationOutcome outcome;

    public ConfirmedRouteEvaluationSteps(
            RoutingCaseCommandService commandService,
            RoutingCaseQueryService queryService,
            DeferredEventDelivery eventDelivery,
            InMemoryVoyageRepository voyages) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.eventDelivery = eventDelivery;
        this.voyages = voyages;
    }

    /**
     * 外部原本の取込で航海の情報版・取得時刻・寄港の時刻が変わる（積地と揚地の 2 寄港。情報版を変えずに時刻だけを変えると
     * 評価されないので、1 つのステップで変える）。
     */
    @前提("航海 {string} が次の内容に更新される")
    public void 航海が更新される(String voyageNumber, DataTable table) {
        Map<String, String> row = table.asMaps().getFirst();
        voyages.replace(new Voyage(
                voyageNumber,
                List.of(
                        new PortCall(new Location(row.get("積地")), null, at(row.get("出発予定"))),
                        new PortCall(new Location(row.get("揚地")), at(row.get("到着予定")), null)),
                row.get("情報版"),
                at(row.get("取得時刻"))));
    }

    /** 外部原本の取込で航海がなくなる（欠航）。 */
    @前提("航海 {string} がなくなる")
    public void 航海がなくなる(String voyageNumber) {
        voyages.remove(voyageNumber);
    }

    @もし("外部情報の更新を受けて案件の確定した経路を評価する")
    public void 確定した経路を評価する() {
        outcome = commandService.evaluateConfirmedRoute(
                new EvaluateConfirmedRouteCommand(onlyCase().number()));
        assertThat(outcome)
                .isNotInstanceOfAny(
                        ConfirmedRouteEvaluationOutcome.NotFound.class, ConfirmedRouteEvaluationOutcome.Conflict.class);
    }

    /**
     * 届く順 B（DE-06 が DE-05 より先に届く。Bolt 37e2 の届く順の表、Bolt 37e4c）。評価で出た再設計要のイベントだけを先に配り、
     * 経路を確定したイベントは残す。
     */
    @もし("再設計要のイベントだけが先に配信される")
    public void 再設計要のイベントだけが先に配信される() {
        eventDelivery.deliverOnly(RouteRedesignRequired.class);
    }

    @ならば("先に配信されたイベントは再設計要のイベントだけである")
    public void 先に配信されたイベントは再設計要のイベントだけである() {
        // 配信の部品は購読側へ届ける処理の中で配った記録を残す（届けなければ記録も残らない。Bolt 37e4c の変異 V1）
        assertThat(eventDelivery.lastDelivered())
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event).isInstanceOf(RouteRedesignRequired.class));
    }

    @ならば("まだ配信していないイベントは経路を確定したイベントだけである")
    public void まだ配信していないイベントは経路を確定したイベントだけである() {
        assertThat(eventDelivery.published())
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event).isInstanceOf(RouteConfirmed.class));
    }

    /** 評価で保存もイベントの発行もしなかった（再設計要の経路版の 2 度目の評価。UC-08 1a）。 */
    @ならば("評価の結果は変わらない")
    public void 評価の結果は変わらない() {
        assertThat(outcome).isEqualTo(new ConfirmedRouteEvaluationOutcome.Unchanged());
    }

    @ならば("経路版 {int} は再設計要で、理由は {string}、再設計要にした時刻は {string} である")
    public void 経路版は再設計要(int routeVersionNo, String reasons, String requiredAt) {
        RouteVersion version = currentCase().routeVersions().get(routeVersionNo - 1);
        assertThat(version.status()).isEqualTo(RouteVersionStatus.REDESIGN_REQUIRED);
        assertThat(version.redesign()).contains(new RedesignRecord(at(requiredAt), reasonsOf(reasons)));
    }

    @かつ("経路版 {int} には候補 {int} の確定の記録と参照情報版 {string} が残っている")
    public void 確定の記録と旧情報版が残る(int routeVersionNo, int candidateNo, String infoVersions) {
        RoutingCase routingCase = currentCase();
        assertThat(routingCase.routeVersions().get(routeVersionNo - 1).confirmation())
                .hasValueSatisfying(
                        confirmation -> assertThat(confirmation.candidateNo()).isEqualTo(candidateNo));
        RouteCandidate candidate =
                routingCase.confirmedCandidateOf(routeVersionNo).orElseThrow();
        assertThat(candidate.legs().stream().map(Leg::infoVersion).toList())
                .isEqualTo(List.of(infoVersions.split(",")));
    }

    @かつ("再設計要のイベントが {int} 件発行され、理由は {string}、旧情報版は {string} である")
    public void 再設計要のイベントが発行される(int count, String reasons, String infoVersions) {
        RoutingCase routingCase = currentCase();
        RouteVersion version = routingCase.routeVersions().getFirst();
        RouteRedesignRequired expected = new RouteRedesignRequired(
                routingCase.id().value(),
                routingCase.number().text(),
                version.routeVersionNo(),
                routingCase.quotationId(),
                routingCase.transportRequestId(),
                routingCase.transportRequestVersionNo(),
                version.redesign().orElseThrow().requiredAt(),
                reasonsOf(reasons).stream().map(RedesignReasonCode::name).toList(),
                List.of(infoVersions.split(",")));
        assertThat(redesignEvents()).hasSize(count).containsOnly(expected);
    }

    @ならば("経路版 {int} は確定のままで、再設計要のイベントは発行されない")
    public void 経路版は確定のまま(int routeVersionNo) {
        RouteVersion version = currentCase().routeVersions().get(routeVersionNo - 1);
        assertThat(version.status()).isEqualTo(RouteVersionStatus.CONFIRMED);
        assertThat(version.redesign()).isEmpty();
        assertThat(redesignEvents()).isEmpty();
    }

    private static List<RedesignReasonCode> reasonsOf(String reasons) {
        return Arrays.stream(reasons.split(","))
                .map(reason -> {
                    RedesignReasonCode code = REASONS.get(reason);
                    if (code == null) {
                        throw new IllegalArgumentException("再設計要の理由の語がありません: " + reason);
                    }
                    return code;
                })
                .toList();
    }

    private List<RouteRedesignRequired> redesignEvents() {
        return eventDelivery.published().stream()
                .filter(RouteRedesignRequired.class::isInstance)
                .map(RouteRedesignRequired.class::cast)
                .toList();
    }

    private RoutingCaseSummary onlyCase() {
        List<RoutingCaseSummary> cases = queryService.listCases();
        assertThat(cases).hasSize(1);
        return cases.getFirst();
    }

    private RoutingCase currentCase() {
        return queryService.findByNumber(onlyCase().number()).orElseThrow();
    }

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

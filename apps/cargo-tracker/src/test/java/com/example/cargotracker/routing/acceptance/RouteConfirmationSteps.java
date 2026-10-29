package com.example.cargotracker.routing.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commands.ConfirmRouteCommand;
import com.example.cargotracker.routing.application.internal.commandservices.RouteConfirmationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmationRejectionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 経路の確定（US-07 AC1・AC2。Bolt 19、AC3。Bolt 37c）の業務ルール層のステップ。案件は DE-16 の配信で作られたもの（シナリオに 1 件）。
 */
public class RouteConfirmationSteps {

    private static final UserId OTHER_ROUTE_DESIGNER =
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000502"));
    private static final UserId SALES = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
    private static final Map<String, RouteConfirmationRejectionReason> REASONS = Map.of(
            "情報版の更新", RouteConfirmationRejectionReason.INFO_VERSION_CHANGED,
            "情報不足", RouteConfirmationRejectionReason.INFO_INSUFFICIENT,
            "判断根拠の不足", RouteConfirmationRejectionReason.RATIONALE_MISSING,
            "経路設計者でない", RouteConfirmationRejectionReason.NOT_ROUTE_DESIGNER,
            "除外の候補", RouteConfirmationRejectionReason.CANDIDATE_EXCLUDED,
            "出発済み", RouteConfirmationRejectionReason.ALREADY_DEPARTED,
            "確定済み", RouteConfirmationRejectionReason.NOT_CONFIRMABLE_STATE,
            "再設計要", RouteConfirmationRejectionReason.ROUTE_REDESIGN_REQUIRED,
            "条件を満たさなくなった", RouteConfirmationRejectionReason.NO_LONGER_CONFORMING);

    private final RoutingCaseCommandService commandService;
    private final RoutingCaseQueryService queryService;
    private final DeferredEventDelivery eventDelivery;
    private final InMemoryConnectionRuleRepository rules;
    private final InMemoryVoyageRepository voyages;
    private RouteConfirmationOutcome outcome;
    private Long openedVersion;

    public RouteConfirmationSteps(
            RoutingCaseCommandService commandService,
            RoutingCaseQueryService queryService,
            DeferredEventDelivery eventDelivery,
            InMemoryConnectionRuleRepository rules,
            InMemoryVoyageRepository voyages) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.eventDelivery = eventDelivery;
        this.rules = rules;
        this.voyages = voyages;
    }

    /** 候補を算出した後に、外部原本の取込で航海の情報版と取得時刻が変わる（寄港は変えない。Bolt 37c）。 */
    @前提("航海 {string} の情報版が {string} に、取得時刻が {string} に更新される")
    public void 航海の情報が更新される(String voyageNumber, String infoVersion, String acquiredAt) {
        Voyage current = voyages.findAll().stream()
                .filter(voyage -> voyage.voyageNumber().equals(voyageNumber))
                .findFirst()
                .orElseThrow();
        voyages.replace(
                new Voyage(voyageNumber, current.portCalls(), infoVersion, new UtcInstant(Instant.parse(acquiredAt))));
    }

    @前提("港 {string} の必要最小接続時間が {string} から {int} 時間になる")
    public void 必要最小接続時間が変わる(String port, String validFrom, int hours) {
        rules.add(new ConnectionRule(
                UUID.randomUUID(),
                new Location(port),
                java.time.Duration.ofHours(hours),
                new UtcInstant(Instant.parse(validFrom)),
                null));
    }

    @前提("経路設計者が案件を開く")
    public void 経路設計者が案件を開く() {
        openedVersion = currentCase().aggregateVersion();
    }

    @かつ("ほかの経路設計者が候補を算出し直す")
    public void ほかの経路設計者が候補を算出し直す() {
        commandService.calculateCandidates(
                new CalculateCandidatesCommand(
                        onlyCase().number(), currentCase().aggregateVersion(), OTHER_ROUTE_DESIGNER),
                RoutingSteps.handling(OTHER_ROUTE_DESIGNER));
    }

    @もし("経路設計者が候補 {int} を判断根拠 {string} で確定する")
    public void 経路設計者が確定する(int candidateNo, String rationale) {
        confirm(candidateNo, rationale, currentCase().aggregateVersion(), RoutingSteps.ROUTE_DESIGNER, true);
    }

    @もし("経路設計者の役割を持たない利用者が候補 {int} を判断根拠 {string} で確定する")
    public void 経路設計者でない利用者が確定する(int candidateNo, String rationale) {
        confirm(candidateNo, rationale, currentCase().aggregateVersion(), SALES, false);
    }

    @もし("経路設計者が開いたときの版で候補 {int} を判断根拠 {string} で確定する")
    public void 開いたときの版で確定する(int candidateNo, String rationale) {
        confirm(candidateNo, rationale, openedVersion, RoutingSteps.ROUTE_DESIGNER, true);
    }

    @ならば("経路版 {int} は確定で、候補 {int} と判断根拠 {string} と承認者の経路設計者と承認時刻 {string} が記録されている")
    public void 経路版は確定(int routeVersionNo, int candidateNo, String rationale, String approvedAt) {
        assertThat(outcome)
                .isEqualTo(new RouteConfirmationOutcome.Confirmed(onlyCase().number(), routeVersionNo, candidateNo));
        RoutingCase routingCase = currentCase();
        RouteVersion version = routingCase.routeVersions().get(routeVersionNo - 1);
        assertThat(version.status()).isEqualTo(RouteVersionStatus.CONFIRMED);
        assertThat(version.confirmation())
                .contains(new RouteConfirmation(
                        candidateNo,
                        new DecisionRationale(rationale),
                        RoutingSteps.ROUTE_DESIGNER.value(),
                        new UtcInstant(Instant.parse(approvedAt))));
        assertThat(routingCase.confirmedRouteVersion())
                .map(RouteVersion::routeVersionNo)
                .contains(routeVersionNo);
    }

    @ならば("経路を確定したイベントが発行され、参照情報版は {string} である")
    public void 経路を確定したイベントが発行される(String infoVersions) {
        RoutingCase routingCase = currentCase();
        RouteConfirmation confirmation =
                routingCase.confirmedRouteVersion().orElseThrow().confirmation().orElseThrow();
        assertThat(confirmedEvents())
                .containsExactly(new RouteConfirmed(
                        routingCase.id().value(),
                        routingCase.number().text(),
                        1,
                        routingCase.quotationId(),
                        routingCase.transportRequestId(),
                        routingCase.transportRequestVersionNo(),
                        confirmation.approvedBy(),
                        confirmation.approvedAt(),
                        List.of(infoVersions.split(","))));
    }

    @ならば("確定は拒否され、理由は {string} である")
    public void 確定は拒否される(String reason) {
        assertThat(outcome).isEqualTo(new RouteConfirmationOutcome.Rejected(REASONS.get(reason)));
    }

    /** 確定したか、拒否の理由（「確定」なら確定した。Bolt 37c の境界のシナリオアウトライン）。 */
    @ならば("確定の結果は {string} である")
    public void 確定の結果(String result) {
        if ("確定".equals(result)) {
            assertThat(outcome).isInstanceOf(RouteConfirmationOutcome.Confirmed.class);
        } else {
            assertThat(outcome).isEqualTo(new RouteConfirmationOutcome.Rejected(REASONS.get(result)));
        }
    }

    @ならば("確定はほかの経路設計者の更新と競合する")
    public void 確定は競合する() {
        assertThat(outcome).isEqualTo(new RouteConfirmationOutcome.Conflict());
    }

    @ならば("経路版 {int} は候補算出済みのままで、経路を確定したイベントは発行されない")
    public void 経路版は候補算出済みのまま(int routeVersionNo) {
        RoutingCase routingCase = currentCase();
        assertThat(routingCase.routeVersions().get(routeVersionNo - 1).status())
                .isEqualTo(RouteVersionStatus.CANDIDATES_PRESENTED);
        assertThat(routingCase.confirmedRouteVersion()).isEmpty();
        assertThat(confirmedEvents()).isEmpty();
    }

    private void confirm(int candidateNo, String rationale, long expectedVersion, UserId approver, boolean designer) {
        outcome = commandService.confirm(
                new ConfirmRouteCommand(
                        onlyCase().number(), candidateNo, rationale, expectedVersion, approver, designer),
                RoutingSteps.handling(approver));
    }

    private List<RouteConfirmed> confirmedEvents() {
        return eventDelivery.published().stream()
                .filter(RouteConfirmed.class::isInstance)
                .map(RouteConfirmed.class::cast)
                .toList();
    }

    private RoutingCaseSummary onlyCase() {
        List<RoutingCaseSummary> cases = queryService.listCases(RoutingSteps.SCENARIO_DESIGNER);
        assertThat(cases).hasSize(1);
        return cases.getFirst();
    }

    private RoutingCase currentCase() {
        return queryService
                .findByNumber(onlyCase().number(), RoutingSteps.SCENARIO_DESIGNER)
                .orElseThrow();
    }
}

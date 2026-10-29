package com.example.cargotracker.routing.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.acceptance.InMemoryConnectionRuleRepository;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.acceptance.InMemoryVoyageRepository;
import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commands.ConfirmRouteCommand;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseScope;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 入力ポートの候補の算出と経路の確定の担当範囲（Bolt 38g の行 5c・5d。US-16 AC4）。操作者の担当企業の外の案件は、何も書かずに範囲外の結果を
 * 返す（画面を開いた後に割り当てが外れたときの守り）。範囲の判定は版の照合より先なので、古い版でも競合を返さない（存在を漏らさない）。
 */
class RoutingCaseCommandServiceScopeTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());
    private static final CompanyId OTHER = new CompanyId(UUID.randomUUID());
    private static final UserId DESIGNER = new UserId(UUID.randomUUID());

    private final List<Object> published = new ArrayList<>();
    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
    private final RoutingCaseCommandService service = new RoutingCaseCommandService(
            repository,
            new InMemoryVoyageRepository(),
            new InMemoryConnectionRuleRepository(),
            published::add,
            Clock.fixed(Instant.parse("2026-10-07T06:00:00Z"), ZoneOffset.UTC),
            new InfoFreshnessPolicy(Duration.ofHours(24)),
            new RoutingCaseScope(repository));

    @Test
    void 範囲の外の案件の候補は算出せずに範囲外を返す() {
        // 行 5c
        RoutingCase routingCase = saved();

        assertThat(service.calculateCandidates(calculate(routingCase.number(), 0), designer(OTHER)))
                .isEqualTo(new CandidateCalculationOutcome.OutOfScope());
        assertThat(repository.findByNumber(routingCase.number()).orElseThrow().aggregateVersion())
                .isEqualTo(routingCase.aggregateVersion());
    }

    @Test
    void 範囲の外では古い版でも競合ではなく範囲外を返す() {
        // 行 5d（範囲の判定が版の照合より先）
        RoutingCase routingCase = saved();

        assertThat(service.calculateCandidates(calculate(routingCase.number(), 7), designer(OTHER)))
                .isEqualTo(new CandidateCalculationOutcome.OutOfScope());
        assertThat(service.confirm(confirm(routingCase.number(), 7), designer(OTHER)))
                .isEqualTo(new RouteConfirmationOutcome.OutOfScope());
    }

    @Test
    void 範囲の外の案件は確定せずに範囲外を返す() {
        // 行 5c
        RoutingCase routingCase = saved();

        assertThat(service.confirm(confirm(routingCase.number(), 0), designer()))
                .isEqualTo(new RouteConfirmationOutcome.OutOfScope());
        assertThat(published).isEmpty();
    }

    @Test
    void 範囲の中の案件は今のまま算出し版が違えば競合を返す() {
        // 行 5a
        RoutingCase routingCase = saved();

        assertThat(service.calculateCandidates(calculate(routingCase.number(), 0), designer(SHIPPER)))
                .isInstanceOf(CandidateCalculationOutcome.Calculated.class);
        assertThat(service.calculateCandidates(calculate(routingCase.number(), 0), designer(SHIPPER)))
                .isEqualTo(new CandidateCalculationOutcome.Conflict());
    }

    @Test
    void 案件番号の案件がなければ範囲によらず見つからない() {
        RoutingCaseNumber missing = new RoutingCaseNumber(2026, 99);

        assertThat(service.calculateCandidates(calculate(missing, 0), designer()))
                .isEqualTo(new CandidateCalculationOutcome.NotFound());
        assertThat(service.confirm(confirm(missing, 0), designer())).isEqualTo(new RouteConfirmationOutcome.NotFound());
    }

    private RoutingCase saved() {
        RoutingCase routingCase = RoutingCaseFixture.openFor(SHIPPER);
        repository.save(routingCase);
        return routingCase;
    }

    private static CalculateCandidatesCommand calculate(RoutingCaseNumber number, long expectedVersion) {
        return new CalculateCandidatesCommand(number, expectedVersion, DESIGNER);
    }

    private static ConfirmRouteCommand confirm(RoutingCaseNumber number, long expectedVersion) {
        return new ConfirmRouteCommand(number, 1, "直行で期限に間に合う。", expectedVersion, DESIGNER, true);
    }

    private static AuthenticatedActor designer(CompanyId... shippers) {
        return AuthenticatedActorFixture.routeDesignerHandling(DESIGNER, shippers);
    }
}

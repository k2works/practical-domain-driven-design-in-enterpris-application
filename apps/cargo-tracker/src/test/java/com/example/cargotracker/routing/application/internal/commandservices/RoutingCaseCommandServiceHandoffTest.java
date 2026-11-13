package com.example.cargotracker.routing.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.acceptance.InMemoryConnectionRuleRepository;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.acceptance.InMemoryVoyageRepository;
import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseScope;
import com.example.cargotracker.routing.domain.events.RouteCandidatesNotFound;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
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

/** 候補の算出で適合する候補がなければ DE-32 を発行して専門判断へ引き継ぐ（UC-06 3a。Bolt 40-14）。 */
class RoutingCaseCommandServiceHandoffTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());
    private static final UserId DESIGNER = new UserId(UUID.randomUUID());

    private final List<Object> published = new ArrayList<>();
    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
    private final InMemoryVoyageRepository voyages = new InMemoryVoyageRepository();
    private final RoutingCaseCommandService service = new RoutingCaseCommandService(
            repository,
            voyages,
            new InMemoryConnectionRuleRepository(),
            published::add,
            Clock.fixed(Instant.parse("2026-10-07T06:00:00Z"), ZoneOffset.UTC),
            new InfoFreshnessPolicy(Duration.ofHours(24)),
            new RoutingCaseScope(repository));

    private AuthenticatedActor designer() {
        return AuthenticatedActorFixture.routeDesignerHandling(DESIGNER, SHIPPER);
    }

    @Test
    void 行1_航海がなければ外部データの欠落を発行する() {
        RoutingCase routingCase = RoutingCaseFixture.openFor(SHIPPER);
        repository.save(routingCase);

        CandidateCalculationOutcome outcome = service.calculateCandidates(
                new CalculateCandidatesCommand(routingCase.number(), routingCase.aggregateVersion(), DESIGNER),
                designer());

        assertThat(outcome).isInstanceOf(CandidateCalculationOutcome.Calculated.class);
        assertThat(published).singleElement().isInstanceOfSatisfying(RouteCandidatesNotFound.class, event -> {
            assertThat(event.reason()).isEqualTo("EXTERNAL_DATA_MISSING");
            assertThat(event.operatorUserId()).isEqualTo(DESIGNER);
            assertThat(event.shipperCompanyId()).isEqualTo(SHIPPER);
        });
    }

    @Test
    void 行2_算出が競合したときは発行しない() {
        RoutingCase routingCase = RoutingCaseFixture.openFor(SHIPPER);
        repository.save(routingCase);

        CandidateCalculationOutcome outcome = service.calculateCandidates(
                new CalculateCandidatesCommand(routingCase.number(), routingCase.aggregateVersion() + 5, DESIGNER),
                designer());

        assertThat(outcome).isEqualTo(new CandidateCalculationOutcome.Conflict());
        assertThat(published).isEmpty();
    }
}

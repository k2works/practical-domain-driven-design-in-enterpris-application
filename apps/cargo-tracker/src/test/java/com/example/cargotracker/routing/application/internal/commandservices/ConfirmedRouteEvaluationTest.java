package com.example.cargotracker.routing.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.acceptance.InMemoryConnectionRuleRepository;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.acceptance.InMemoryVoyageRepository;
import com.example.cargotracker.routing.application.internal.commands.EvaluateConfirmedRouteCommand;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseScope;
import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRule;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 入力ポートの確定した経路の評価（US-07 AC4。Bolt 37d2）。案件は直行 V-EARLY の候補 1 を確定したもの（RC-2026-0001）。
 * 評価は外部情報の更新を受けたシステムの操作で、案件の版を期待版として受け取らない（読み込んだ版で競合を検知する）。
 */
class ConfirmedRouteEvaluationTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-10-07T06:00:00Z");

    private final List<Object> published = new ArrayList<>();
    private final InMemoryVoyageRepository voyages = new InMemoryVoyageRepository();
    private final InMemoryConnectionRuleRepository rules = new InMemoryConnectionRuleRepository();
    private RoutingCase confirmed;

    @BeforeEach
    void setUp() {
        confirmed = RoutingCaseFixture.confirmed().routingCase();
        rules.add(new ConnectionRule(
                UUID.randomUUID(),
                new Location("SGSIN"),
                Duration.ofHours(8),
                new UtcInstant(Instant.parse("2026-01-01T00:00:00Z")),
                null));
    }

    @Test
    void 案件番号の案件がなければ見つからない() {
        InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();

        assertThat(service(repository)
                        .evaluateConfirmedRoute(new EvaluateConfirmedRouteCommand(new RoutingCaseNumber(2026, 9))))
                .isEqualTo(new ConfirmedRouteEvaluationOutcome.NotFound());
        assertThat(published).isEmpty();
    }

    @Test
    void 区間の情報版と区間の予定の組が今の航海にあれば変わらないで保存もイベントの発行もしない() {
        InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
        repository.save(confirmed);
        voyages.add(earlyVoyage("V-EARLY@1", "2026-10-29T00:00:00Z"));

        assertThat(service(repository).evaluateConfirmedRoute(command()))
                .isEqualTo(new ConfirmedRouteEvaluationOutcome.Unchanged());
        assertThat(repository.findByNumber(confirmed.number()).orElseThrow().aggregateVersion())
                .isEqualTo(confirmed.aggregateVersion());
        assertThat(published).isEmpty();
    }

    @Test
    void 不適合になれば再設計要をシステムの操作で保存してDE06を発行する() {
        List<RoutingCaseNumber> savedBySystem = new ArrayList<>();
        List<UUID> operators = new ArrayList<>();
        InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository() {
            @Override
            public synchronized void updateBySystem(RoutingCase routingCase) {
                savedBySystem.add(routingCase.number());
                super.updateBySystem(routingCase);
            }

            @Override
            public synchronized void update(RoutingCase routingCase, UUID operatorId) {
                operators.add(operatorId);
                super.update(routingCase, operatorId);
            }
        };
        repository.save(confirmed);
        voyages.add(earlyVoyage("V-EARLY@2", "2026-11-05T00:00:00Z"));

        assertThat(service(repository).evaluateConfirmedRoute(command()))
                .isEqualTo(new ConfirmedRouteEvaluationOutcome.RedesignRequired(
                        confirmed.number(), 1, List.of(RedesignReasonCode.DEADLINE_EXCEEDED)));
        RoutingCase saved = repository.findByNumber(confirmed.number()).orElseThrow();
        assertThat(saved.routeVersion().status()).isEqualTo(RouteVersionStatus.REDESIGN_REQUIRED);
        assertThat(saved.aggregateVersion()).isEqualTo(confirmed.aggregateVersion() + 1);
        // 評価は利用者の操作ではないので、操作者のある更新を使わない（Bolt 39f の行 29）
        assertThat(savedBySystem).containsExactly(confirmed.number());
        assertThat(operators).isEmpty();
        assertThat(published).singleElement().isInstanceOfSatisfying(RouteRedesignRequired.class, event -> {
            assertThat(event.routeVersionNo()).isEqualTo(1);
            assertThat(event.requiredAt()).isEqualTo(new UtcInstant(EVALUATED_AT));
            assertThat(event.reasons()).containsExactly("DEADLINE_EXCEEDED");
            assertThat(event.referencedInfoVersions()).containsExactly("V-EARLY@1");
        });
    }

    @Test
    void 読み込んだ後に案件が更新されていれば競合でイベントを発行しない() {
        // 入力ポートは競合を返すだけで、読み直しは DE-30 の listener が行う（Bolt 39f の決定 (5)）。読み込んだ直後に
        // ほかの更新で案件の版を進め、版の照合で競合させる
        InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository() {
            @Override
            public Optional<RoutingCase> findByNumber(RoutingCaseNumber number) {
                Optional<RoutingCase> loaded = super.findByNumber(number);
                super.findByNumber(number).ifPresent(super::updateBySystem);
                return loaded;
            }
        };
        repository.save(confirmed);
        voyages.add(earlyVoyage("V-EARLY@2", "2026-11-05T00:00:00Z"));

        assertThat(service(repository).evaluateConfirmedRoute(command()))
                .isEqualTo(new ConfirmedRouteEvaluationOutcome.Conflict());
        assertThat(published).isEmpty();
    }

    @Nested
    class Bolt39e_航海の読み方 {

        @Test
        void 行34_評価はfindByVoyageNumbersだけを確定の区間の航海番号で呼びfindCandidateVoyagesを呼ばない() {
            InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
            repository.save(confirmed);
            voyages.add(earlyVoyage("V-EARLY@1", "2026-10-29T00:00:00Z"));
            InMemoryVoyageRepository recordingVoyages = Mockito.spy(voyages);

            new RoutingCaseCommandService(
                            repository,
                            recordingVoyages,
                            rules,
                            published::add,
                            Clock.fixed(EVALUATED_AT, ZoneOffset.UTC),
                            new InfoFreshnessPolicy(Duration.ofHours(24)),
                            new RoutingCaseScope(repository))
                    .evaluateConfirmedRoute(command());

            Mockito.verify(recordingVoyages).findByVoyageNumbers(confirmed.confirmedVoyageNumbers());
            Mockito.verify(recordingVoyages, Mockito.never()).findCandidateVoyages(Mockito.any());
            assertThat(confirmed.confirmedVoyageNumbers()).isNotEmpty();
        }
    }

    private RoutingCaseCommandService service(InMemoryRoutingCaseRepository repository) {
        return new RoutingCaseCommandService(
                repository,
                voyages,
                rules,
                published::add,
                Clock.fixed(EVALUATED_AT, ZoneOffset.UTC),
                new InfoFreshnessPolicy(Duration.ofHours(24)),
                new RoutingCaseScope(repository));
    }

    private EvaluateConfirmedRouteCommand command() {
        return new EvaluateConfirmedRouteCommand(confirmed.number());
    }

    private static Voyage earlyVoyage(String infoVersion, String arrival) {
        return new Voyage(
                "V-EARLY",
                List.of(
                        new PortCall(new Location("JPTYO"), null, at("2026-10-08T00:00:00Z")),
                        new PortCall(new Location("NLRTM"), at(arrival), null)),
                infoVersion,
                at("2026-10-07T05:00:00Z"));
    }

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

package com.example.cargotracker.identity.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.commands.RegisterKpiBaselineCommand;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaselineRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** KPI 基準値の登録（US-21 AC5、KPI-INV-02。W9 の Bolt 44-2）。登録できるのは監査担当者だけ。 */
class KpiBaselineCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 11, 1);

    private final List<KpiBaseline> saved = new ArrayList<>();

    private final KpiBaselineRepository repository = new KpiBaselineRepository() {
        @Override
        public void save(KpiBaseline baseline) {
            saved.add(baseline);
        }

        @Override
        public Optional<KpiBaseline> findLatest(KpiKind kpi) {
            return saved.stream()
                    .filter(b -> b.kpi() == kpi)
                    .filter(b -> saved.stream()
                            .noneMatch(
                                    n -> n.supersedes().filter(b.id()::equals).isPresent()))
                    .reduce((first, second) -> second);
        }
    };

    private final KpiBaselineCommandService service =
            new KpiBaselineCommandService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private static AuthenticatedActor auditor() {
        return AuthenticatedActorFixture.auditor(
                new UserId(UUID.fromString("00000000-0000-0000-0000-0000044d0001")),
                new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000044d0002")));
    }

    private static RegisterKpiBaselineCommand command(KpiKind kpi, String p50) {
        return new RegisterKpiBaselineCommand(kpi, FROM, TO, new BigDecimal(p50), Optional.empty(), "現行の記録から算出");
    }

    @Test
    void 監査担当者は基準値を登録でき_登録者と登録時刻が残る() {
        AuthenticatedActor actor = auditor();

        KpiBaselineOutcome outcome = service.register(actor, command(KpiKind.KPI_01, "480"));

        assertThat(outcome).isInstanceOf(KpiBaselineOutcome.Registered.class);
        assertThat(saved).singleElement().satisfies(baseline -> {
            assertThat(baseline.registeredBy()).isEqualTo(actor.userId());
            assertThat(baseline.registeredAt().instant()).isEqualTo(NOW);
            assertThat(baseline.supersedes()).isEmpty();
        });
    }

    @Test
    void 同じ_KPI_を登録し直すと前の最新を指す新しい記録になる() {
        service.register(auditor(), command(KpiKind.KPI_02, "40"));
        KpiBaseline first = saved.getFirst();

        service.register(auditor(), command(KpiKind.KPI_02, "45"));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(1).supersedes()).contains(first.id());
        // 別の KPI の記録は指さない
        service.register(auditor(), command(KpiKind.KPI_01, "480"));
        assertThat(saved.get(2).supersedes()).isEmpty();
    }

    @Test
    void 監査担当者でない利用者は登録できず何も保存しない() {
        AuthenticatedActor sales = AuthenticatedActorFixture.salesHandling(
                new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()));

        assertThat(service.register(sales, command(KpiKind.KPI_01, "480")))
                .isInstanceOf(KpiBaselineOutcome.NotPermitted.class);
        assertThat(saved).isEmpty();
    }

    @Test
    void 入力の誤りは理由を示して保存しない() {
        KpiBaselineOutcome outcome = service.register(
                auditor(),
                new RegisterKpiBaselineCommand(
                        KpiKind.KPI_01, FROM, TO.minusDays(3), new BigDecimal("480"), Optional.empty(), "算出方法"));

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        KpiBaselineOutcome.InvalidInput.class,
                        invalid -> assertThat(invalid.message()).contains("28 日"));
        assertThat(saved).isEmpty();
    }
}

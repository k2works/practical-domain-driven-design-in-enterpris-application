package com.example.cargotracker.routing.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.StaleLeg;
import com.example.cargotracker.routing.domain.model.valueobjects.StaleLegReason;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** 経路設計案件の照会のうち、S-06 を開くときに 1 文で航海を読む判定の入口（決定 (4)・(5)。Bolt 39f2）。 */
class RoutingCaseQueryServiceTest {

    private static final UtcInstant JUDGED_AT = at("2026-10-07T03:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-07T04:00:00Z");
    private static final Location TOKYO = new Location("JPTYO");
    private static final Location ROTTERDAM = new Location("NLRTM");
    private static final int CANDIDATES = RoutingCase.MAX_CANDIDATES;
    private static final int LEGS_PER_CANDIDATE = 3;

    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
    private final VoyageRepository voyages = mock(VoyageRepository.class);
    private final RoutingCaseQueryService service = new RoutingCaseQueryService(
            repository, new RoutingCaseScope(repository), voyages, new InfoFreshnessPolicy(Duration.ofHours(24)));

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }

    private static Leg leg(String voyageNumber) {
        return new Leg(
                voyageNumber,
                TOKYO,
                ROTTERDAM,
                at("2026-10-10T00:00:00Z"),
                at("2026-10-30T00:00:00Z"),
                voyageNumber + "@1",
                at("2026-10-07T01:00:00Z"));
    }

    private static RouteCandidate candidate(int candidateNo, List<Leg> legs) {
        return new RouteCandidate(
                candidateNo, legs, new ConstraintEvaluation(at("2026-10-30T00:00:00Z"), List.of(), null), JUDGED_AT);
    }

    private static RoutingCase withVersions(RouteVersion... versions) {
        return RoutingCaseFixture.withRouteVersions(RoutingCaseFixture.calculated(), List.of(versions));
    }

    private static RouteVersion presentedVersion(int versionNo, List<RouteCandidate> candidates) {
        return new RouteVersion(versionNo, RouteVersionStatus.CANDIDATES_PRESENTED, candidates, JUDGED_AT);
    }

    @Nested
    class Bolt39f2_古くなった候補の照会 {

        @Test
        void 行13_候補提示済みで候補が0件なら空で航海を読まない() {
            RoutingCase noCandidates = withVersions(presentedVersion(1, List.of()));

            assertThat(service.staleLegsOf(noCandidates, NOW)).isEmpty();

            verifyNoInteractions(voyages);
        }

        @Test
        void 行15_20候補3区間で航海番号が重なっても重複のない番号で1回だけ読む() {
            List<RouteCandidate> candidates = new ArrayList<>();
            Set<String> expected = new HashSet<>();
            for (int no = 1; no <= CANDIDATES; no++) {
                List<Leg> legs = new ArrayList<>();
                for (int position = 0; position < LEGS_PER_CANDIDATE; position++) {
                    String voyageNumber = "V-" + (no + position) % 7;
                    expected.add(voyageNumber);
                    legs.add(leg(voyageNumber));
                }
                candidates.add(candidate(no, legs));
            }
            when(voyages.findByVoyageNumbers(any())).thenReturn(List.of());

            List<StaleLeg> stale = service.staleLegsOf(withVersions(presentedVersion(1, candidates)), NOW);

            assertThat(stale).hasSize(CANDIDATES * LEGS_PER_CANDIDATE);
            verify(voyages)
                    .findByVoyageNumbers(argThat(
                            numbers -> numbers.size() == expected.size() && new HashSet<>(numbers).equals(expected)));
            verifyNoMoreInteractions(voyages);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"許容時間の内, 2026-10-07T04:00:00Z, false", "許容時間を過ぎた, 2026-10-09T00:00:00Z, true"})
        void 渡した時刻で判定する(String description, String now, boolean expected) {
            RoutingCase routingCase = withVersions(presentedVersion(1, List.of(candidate(1, List.of(leg("V-1"))))));
            when(voyages.findByVoyageNumbers(any()))
                    .thenReturn(List.of(new Voyage(
                            "V-1",
                            List.of(
                                    new PortCall(TOKYO, null, at("2026-10-10T00:00:00Z")),
                                    new PortCall(ROTTERDAM, at("2026-10-30T00:00:00Z"), null)),
                            "V-1@1",
                            at("2026-10-07T01:00:00Z"))));

            assertThat(service.staleLegsOf(routingCase, Instant.parse(now)))
                    .as(description)
                    .isEqualTo(expected ? List.of(new StaleLeg(1, 1, StaleLegReason.INFO_INSUFFICIENT)) : List.of());
        }

        static Stream<Arguments> notConfirmable() {
            List<RouteCandidate> candidates = List.of(candidate(1, List.of(leg("V-1"))));
            RouteConfirmation confirmation =
                    new RouteConfirmation(1, new DecisionRationale("根拠"), UUID.randomUUID(), JUDGED_AT);
            RouteVersion confirmed =
                    new RouteVersion(1, RouteVersionStatus.CONFIRMED, candidates, JUDGED_AT, confirmation);
            return Stream.of(
                    Arguments.of("作成中", withVersions(RouteVersion.draft(1))),
                    Arguments.of(
                            "専門判断待ち",
                            withVersions(new RouteVersion(1, RouteVersionStatus.EXPERT_REVIEW, candidates, JUDGED_AT))),
                    Arguments.of("確定", withVersions(confirmed)),
                    Arguments.of(
                            "再設計要",
                            withVersions(new RouteVersion(
                                    1,
                                    RouteVersionStatus.REDESIGN_REQUIRED,
                                    candidates,
                                    JUDGED_AT,
                                    candidates.size(),
                                    confirmation,
                                    new RedesignRecord(JUDGED_AT, List.of(RedesignReasonCode.DEADLINE_EXCEEDED))))),
                    Arguments.of(
                            "旧版",
                            withVersions(new RouteVersion(1, RouteVersionStatus.SUPERSEDED, candidates, JUDGED_AT))),
                    Arguments.of("候補提示済みで確定した経路版がある", withVersions(confirmed, presentedVersion(2, candidates))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("notConfirmable")
        void 行16_確定できない状態なら航海を読まない(String description, RoutingCase routingCase) {
            assertThat(service.staleLegsOf(routingCase, NOW)).as(description).isEmpty();

            verifyNoInteractions(voyages);
        }
    }
}

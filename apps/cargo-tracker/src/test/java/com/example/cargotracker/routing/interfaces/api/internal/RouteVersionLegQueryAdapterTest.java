package com.example.cargotracker.routing.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import org.junit.jupiter.api.Test;

/**
 * 経路設計の公開 API（確定した経路版の区間の照会）のインバウンドアダプター（ADR-015、units.md の U3 → U2。Bolt 25）。追跡が予定として
 * 採用する区間を、確定した経路版の確定の記録の候補から区間の順に返す。経路版は不変なので、確定した区間と同じものが返る。
 * 案件・経路版がない、または確定していない経路版は空を返す（追跡は警告のログを残して開始しない）。
 */
class RouteVersionLegQueryAdapterTest {

    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
    private final RouteVersionLegQueryAdapter adapter =
            new RouteVersionLegQueryAdapter(new RoutingCaseQueryService(repository));

    @Test
    void 確定した経路版の区間を区間の順に返す() {
        RoutingCaseFixture.Confirmed confirmed = RoutingCaseFixture.confirmed();
        repository.save(confirmed.routingCase());

        assertThat(adapter.confirmedLegsOf("RC-2026-0001", 1)).singleElement().satisfies(leg -> {
            assertThat(leg.voyageNumber()).isEqualTo("V-EARLY");
            assertThat(leg.load().unLocode()).isEqualTo("JPTYO");
            assertThat(leg.discharge().unLocode()).isEqualTo("NLRTM");
            assertThat(leg.arrivalAt().instant()).isAfter(leg.departureAt().instant());
        });
    }

    @Test
    void ない経路版番号は空を返す() {
        repository.save(RoutingCaseFixture.confirmed().routingCase());

        assertThat(adapter.confirmedLegsOf("RC-2026-0001", 2)).isEmpty();
    }

    @Test
    void ない案件は空を返す() {
        assertThat(adapter.confirmedLegsOf("RC-2026-0999", 1)).isEmpty();
    }

    @Test
    void 確定していない経路版は空を返す() {
        repository.save(RoutingCaseFixture.calculated());

        assertThat(adapter.confirmedLegsOf("RC-2026-0001", 1)).isEmpty();
    }

    @Test
    void 案件番号の形でない表記は空を返す() {
        assertThat(adapter.confirmedLegsOf("not-a-case", 1)).isEmpty();
    }
}

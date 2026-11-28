package com.example.cargotracker.routing.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 社内の経路設計案件の担当範囲の判定（Bolt 38g。US-16 AC4、IA-INV-02）。 */
class RoutingCaseScopeTest {

    private static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());
    private static final CompanyId OTHER = new CompanyId(UUID.randomUUID());

    private final InMemoryRoutingCaseRepository repository = new InMemoryRoutingCaseRepository();
    private final RoutingCaseScope scope = new RoutingCaseScope(repository);

    @Test
    void 担当企業の案件は見つかる() {
        RoutingCase routingCase = saved(SHIPPER);

        // 読み出しは保存した状態から組み立て直した写し（同じ案件番号）
        assertThat(scope.find(routingCase.number(), designer(SHIPPER)).inScope())
                .map(RoutingCase::number)
                .contains(routingCase.number());
    }

    @Test
    void 担当企業の外の案件は範囲の外() {
        RoutingCase routingCase = saved(SHIPPER);

        assertThat(scope.find(routingCase.number(), designer(OTHER))).isEqualTo(new RoutingCaseLookup.OutOfScope());
        assertThat(scope.find(routingCase.number(), designer())).isEqualTo(new RoutingCaseLookup.OutOfScope());
    }

    @Test
    void 案件番号の案件がなければ見つからない() {
        assertThat(scope.find(new RoutingCaseNumber(2026, 99), designer(SHIPPER)))
                .isEqualTo(new RoutingCaseLookup.NotFound());
    }

    @Test
    void 範囲の中の案件だけを取り出せる() {
        RoutingCase routingCase = saved(SHIPPER);

        assertThat(new RoutingCaseLookup.Found(routingCase).inScope()).contains(routingCase);
        assertThat(new RoutingCaseLookup.OutOfScope().inScope()).isEmpty();
        assertThat(new RoutingCaseLookup.NotFound().inScope()).isEmpty();
    }

    @Test
    void 一覧は担当企業の集合で読む() {
        RoutingCaseRepository mocked = mock(RoutingCaseRepository.class);
        when(mocked.findSummaries(any())).thenReturn(List.of());

        new RoutingCaseScope(mocked).listWithin(designer(SHIPPER, OTHER), mocked::findSummaries);

        verify(mocked).findSummaries(Set.of(SHIPPER, OTHER));
    }

    @Test
    void 担当企業がなければ一覧を読まずに空を返す() {
        // 行 6（空の守り。IN () は書けない）
        RoutingCaseRepository mocked = mock(RoutingCaseRepository.class);

        assertThat(new RoutingCaseScope(mocked).listWithin(designer(), mocked::findSummaries))
                .isEmpty();
        verify(mocked, never()).findSummaries(any());
    }

    private RoutingCase saved(CompanyId shipper) {
        RoutingCase routingCase = RoutingCaseFixture.openFor(shipper);
        repository.save(routingCase);
        return routingCase;
    }

    private static AuthenticatedActor designer(CompanyId... shippers) {
        return AuthenticatedActorFixture.routeDesignerHandling(new UserId(UUID.randomUUID()), shippers);
    }
}

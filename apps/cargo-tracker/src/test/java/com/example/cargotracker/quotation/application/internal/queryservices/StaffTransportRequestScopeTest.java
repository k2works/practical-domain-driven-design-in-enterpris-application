package com.example.cargotracker.quotation.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 社内の側の輸送要求の担当範囲の判定（Bolt 38f の行 1b・2b・4。判定はサービスの側の 1 か所。決定 (10)）。 */
class StaffTransportRequestScopeTest {

    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final CompanyId SHIPPER_A = new CompanyId(UUID.randomUUID());
    private static final CompanyId SHIPPER_B = new CompanyId(UUID.randomUUID());

    private final TransportRequestRepository repository = mock(TransportRequestRepository.class);
    private final StaffTransportRequestScope scope = new StaffTransportRequestScope(repository);

    private static AuthenticatedActor salesWith(Set<CompanyId> assigned) {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.SALES),
                "営業 一郎",
                "A 社",
                assigned);
    }

    private TransportRequest requestOf(CompanyId shipper) {
        TransportRequest request = mock(TransportRequest.class);
        when(request.shipperCompanyId()).thenReturn(shipper);
        when(repository.findByNumberForStaff(NUMBER)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void 担当企業の輸送要求は見つかる() {
        TransportRequest request = requestOf(SHIPPER_A);

        assertThat(scope.find(NUMBER, salesWith(Set.of(SHIPPER_A))))
                .isEqualTo(new StaffTransportRequestLookup.Found(request));
    }

    @Test
    void 担当企業の外の輸送要求は範囲外() {
        requestOf(SHIPPER_B);

        assertThat(scope.find(NUMBER, salesWith(Set.of(SHIPPER_A))))
                .isEqualTo(new StaffTransportRequestLookup.OutOfScope());
    }

    @Test
    void 担当企業がなければ範囲外() {
        requestOf(SHIPPER_A);

        assertThat(scope.find(NUMBER, salesWith(Set.of()))).isEqualTo(new StaffTransportRequestLookup.OutOfScope());
    }

    @Test
    void 輸送要求がなければ見つからない() {
        // 行 4（範囲外と分けて、記録しない）
        when(repository.findByNumberForStaff(NUMBER)).thenReturn(Optional.empty());

        assertThat(scope.find(NUMBER, salesWith(Set.of(SHIPPER_A))))
                .isEqualTo(new StaffTransportRequestLookup.NotFound());
    }

    @Test
    void 一覧は担当企業で絞って読み担当企業がなければ読まずに空() {
        // 行 3a（空の守りは 1 か所。IN () は書けない）
        java.util.concurrent.atomic.AtomicReference<Set<CompanyId>> asked =
                new java.util.concurrent.atomic.AtomicReference<>();

        assertThat(scope.listWithin(salesWith(Set.of(SHIPPER_A)), companies -> {
                    asked.set(companies);
                    return List.of("輸送要求");
                }))
                .containsExactly("輸送要求");
        assertThat(asked.get()).containsExactly(SHIPPER_A);

        assertThat(scope.<String>listWithin(salesWith(Set.of()), companies -> {
                    throw new AssertionError("担当企業がなければ読まない");
                }))
                .isEmpty();
        verify(repository, never()).findByNumberForStaff(NUMBER);
    }
}

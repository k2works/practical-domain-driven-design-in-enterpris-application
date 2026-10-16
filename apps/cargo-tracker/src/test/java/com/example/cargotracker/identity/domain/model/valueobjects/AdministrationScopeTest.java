package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 管理範囲（Bolt 34b。IA-INV-14）。 */
class AdministrationScopeTest {

    private static final CompanyId OPERATOR = new CompanyId(UUID.randomUUID());
    private static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());

    @Test
    void 割り当てた企業を含み割り当てていない企業を含まない() {
        AdministrationScope scope = new AdministrationScope(Set.of(OPERATOR));

        assertThat(scope.covers(OPERATOR)).isTrue();
        assertThat(scope.covers(SHIPPER)).isFalse();
    }

    @Test
    void 割り当てのない管理範囲はどの企業も含まない() {
        assertThat(new AdministrationScope(Set.of()).covers(OPERATOR)).isFalse();
    }

    @Test
    void 渡した集合を後から変えても管理範囲は変わらない() {
        Set<CompanyId> companies = new HashSet<>(Set.of(OPERATOR));
        AdministrationScope scope = new AdministrationScope(companies);

        companies.add(SHIPPER);

        assertThat(scope.covers(SHIPPER)).isFalse();
        assertThat(scope.companies()).containsExactly(OPERATOR);
    }
}

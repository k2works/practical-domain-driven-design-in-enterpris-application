package com.example.cargotracker.identity.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 担当企業（Bolt 38e。IA-INV-02）。 */
class AssignedCompaniesTest {

    private static final CompanyId SHIPPER_A = new CompanyId(UUID.randomUUID());
    private static final CompanyId SHIPPER_B = new CompanyId(UUID.randomUUID());
    private static final CompanyId OUTSIDE = new CompanyId(UUID.randomUUID());

    @Test
    void 担当企業のない利用者は空() {
        assertThat(AssignedCompanies.none().companies()).isEmpty();
        assertThat(AssignedCompanies.none().contains(SHIPPER_A)).isFalse();
    }

    @Test
    void 渡した集合を後から変えても担当企業は変わらない() {
        Set<CompanyId> companies = new HashSet<>(Set.of(SHIPPER_A));
        AssignedCompanies assigned = new AssignedCompanies(companies);

        companies.add(SHIPPER_B);

        assertThat(assigned.companies()).containsExactly(SHIPPER_A);
    }

    @Test
    void 範囲の中の担当企業と範囲の外の数に分ける() {
        AssignedCompanies assigned = new AssignedCompanies(Set.of(SHIPPER_A, OUTSIDE));
        AdministrationScope scope = new AdministrationScope(Set.of(SHIPPER_A, SHIPPER_B));

        assertThat(assigned.within(scope).companies()).containsExactly(SHIPPER_A);
        assertThat(assigned.outsideCountOf(scope)).isEqualTo(1);
    }

    @Test
    void 範囲の中だけを選んだ企業に置き換え範囲の外はそのまま残す() {
        AssignedCompanies assigned = new AssignedCompanies(Set.of(SHIPPER_A, OUTSIDE));
        AdministrationScope scope = new AdministrationScope(Set.of(SHIPPER_A, SHIPPER_B));

        AssignedCompanies replaced = assigned.replaceWithin(scope, new AssignedCompanies(Set.of(SHIPPER_B)));

        assertThat(replaced.companies()).containsExactlyInAnyOrder(SHIPPER_B, OUTSIDE);
    }
}

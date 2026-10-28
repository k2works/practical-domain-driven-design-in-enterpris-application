package com.example.cargotracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 認証された利用者の担当企業の判定（Bolt 38f の決定 (6)。失敗に倒す）。 */
class AuthenticatedActorTest {

    private static final CompanyId SHIPPER_A = new CompanyId(UUID.randomUUID());
    private static final CompanyId SHIPPER_B = new CompanyId(UUID.randomUUID());

    private static AuthenticatedActor salesWith(Set<CompanyId> assigned) {
        return new AuthenticatedActor(
                new UserId(UUID.randomUUID()),
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.SALES),
                "営業 一郎",
                "A 社",
                assigned);
    }

    @Test
    void 担当企業に含む企業だけを扱う() {
        AuthenticatedActor actor = salesWith(Set.of(SHIPPER_A));

        assertThat(actor.handlesCompany(SHIPPER_A)).isTrue();
        assertThat(actor.handlesCompany(SHIPPER_B)).isFalse();
        assertThat(actor.hasNoAssignedCompanies()).isFalse();
    }

    @Test
    void 担当企業がなければどの企業も扱わない() {
        // 行 3b・3c（空なら全件にしない。失敗に倒す）
        AuthenticatedActor actor = salesWith(Set.of());

        assertThat(actor.handlesCompany(SHIPPER_A)).isFalse();
        assertThat(actor.hasNoAssignedCompanies()).isTrue();
    }

    @Test
    void 担当企業を持たない作り方は空になる() {
        AuthenticatedActor actor = new AuthenticatedActor(
                new UserId(UUID.randomUUID()), new CompanyId(UUID.randomUUID()), Set.of(Role.SALES), "営業 一郎", "A 社");

        assertThat(actor.assignedCompanies()).isEmpty();
        assertThat(actor.handlesCompany(SHIPPER_A)).isFalse();
    }

    @Test
    void 担当企業は不変の写しで持つ() {
        Set<CompanyId> assigned = new HashSet<>(Set.of(SHIPPER_A));
        AuthenticatedActor actor = salesWith(assigned);
        assigned.add(SHIPPER_B);

        assertThat(actor.assignedCompanies()).containsExactly(SHIPPER_A);
    }
}

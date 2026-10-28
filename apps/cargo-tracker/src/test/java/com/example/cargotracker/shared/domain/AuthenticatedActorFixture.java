package com.example.cargotracker.shared.domain;

import java.util.Set;
import java.util.UUID;

/** テストの主体（Bolt 38f）。社内の画面とサービスは担当企業で絞るので、担当企業を持つ営業担当者をここで作る。 */
public final class AuthenticatedActorFixture {

    private AuthenticatedActorFixture() {}

    /** 担当企業に {@code shippers} を持つ営業担当者。 */
    public static AuthenticatedActor salesHandling(UserId userId, CompanyId... shippers) {
        return new AuthenticatedActor(
                userId, new CompanyId(UUID.randomUUID()), Set.of(Role.SALES), "営業 一郎", "A 社", Set.of(shippers));
    }
}

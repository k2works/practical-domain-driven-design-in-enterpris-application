package com.example.cargotracker.shared.domain;

import java.util.Set;
import java.util.UUID;

/** テストの主体（Bolt 38f）。社内の画面とサービスは担当企業で絞るので、担当企業を持つ営業担当者をここで作る。 */
public final class AuthenticatedActorFixture {

    /** 受入シナリオ（業務ルール層）の荷主企業（001。Bolt 38f2）。 */
    public static final CompanyId SCENARIO_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    /** 受入シナリオ（業務ルール層）のもう 1 つの荷主企業（002。Bolt 38f2）。 */
    public static final CompanyId SCENARIO_OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private AuthenticatedActorFixture() {}

    /** 担当企業に {@code shippers} を持つ営業担当者。 */
    public static AuthenticatedActor salesHandling(UserId userId, CompanyId... shippers) {
        return new AuthenticatedActor(
                userId, new CompanyId(UUID.randomUUID()), Set.of(Role.SALES), "営業 一郎", "A 社", Set.of(shippers));
    }

    /** 担当企業に {@code shippers} を持つ追跡管理者（Bolt 38g）。 */
    public static AuthenticatedActor trackingManagerHandling(UserId userId, CompanyId... shippers) {
        return new AuthenticatedActor(
                userId,
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.TRACKING_MANAGER),
                "追跡 管理",
                "A 社",
                Set.of(shippers));
    }

    /** 担当企業に {@code shippers} を持つ経路設計者（Bolt 38g）。 */
    public static AuthenticatedActor routeDesignerHandling(UserId userId, CompanyId... shippers) {
        return new AuthenticatedActor(
                userId,
                new CompanyId(UUID.randomUUID()),
                Set.of(Role.ROUTE_DESIGNER),
                "経路 設計",
                "A 社",
                Set.of(shippers));
    }
}

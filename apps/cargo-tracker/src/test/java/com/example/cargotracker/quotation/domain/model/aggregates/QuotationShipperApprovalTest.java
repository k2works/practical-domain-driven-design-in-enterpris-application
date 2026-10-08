package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteAssignmentResult;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipperApproval;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 経路版の割当てと荷主の承認（US-24 AC4・AC5、R-INV-11、Q-INV-07・10。Bolt 20）。
 * 有効期限は QuotationFixture.EXPIRES_AT = 2099-10-08T09:00:00Z。
 */
class QuotationShipperApprovalTest {

    private final QuotationId id = new QuotationId(UUID.randomUUID());
    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());
    private final UserId salesRep = new UserId(UUID.randomUUID());
    private final UserId shipperUser = new UserId(UUID.randomUUID());
    private final UtcInstant now = at("2026-10-08T03:00:00Z");

    private final AssignedRoute route = new AssignedRoute(
            "RC-2026-0001",
            1,
            at("2026-10-08T02:00:00Z"),
            List.of(
                    new AssignedRouteLeg(
                            "V-381",
                            new Location("JPTYO"),
                            new Location("SGSIN"),
                            at("2099-10-12T03:00:00Z"),
                            at("2099-10-20T08:00:00Z")),
                    new AssignedRouteLeg(
                            "V-417",
                            new Location("SGSIN"),
                            new Location("NLRTM"),
                            at("2099-10-22T06:00:00Z"),
                            at("2099-10-30T09:00:00Z"))));

    // 経路版の割当て（R-INV-11。DE-21）

    @Test
    void 詳細設計依頼済みの見積りに経路版を割り当てると荷主承認待ちになり割り当てた経路を持ちDE21を生成する() {
        Quotation quotation = routingRequested();

        assertThat(quotation.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.ASSIGNED);

        assertThat(quotation.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(quotation.assignedRoute()).contains(route);
        assertThat(quotation.domainEvents())
                .singleElement()
                .isEqualTo(new QuotationRouteAssigned(
                        id.value(), 1, transportRequestId.value(), 1, "RC-2026-0001", 1, now));
    }

    @Test
    void 同じ経路版の割当ては何もしない() {
        Quotation quotation = routingRequested();
        quotation.assignRoute(route, now);
        quotation.clearDomainEvents();

        assertThat(quotation.assignRoute(route, at("2026-10-08T04:00:00Z")))
                .isEqualTo(RouteAssignmentResult.ALREADY_ASSIGNED);

        assertThat(quotation.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(quotation.domainEvents()).isEmpty();
    }

    @Test
    void 承認済みの見積りへの同じ経路版の割当ても何もしない() {
        Quotation quotation = awaitingApproval();
        quotation.approveByShipper(shipperUser, now);
        quotation.clearDomainEvents();

        assertThat(quotation.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.ALREADY_ASSIGNED);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(quotation.domainEvents()).isEmpty();
    }

    @Test
    void 別の経路版は割り当てない() {
        Quotation quotation = awaitingApproval();
        AssignedRoute another = new AssignedRoute("RC-2026-0001", 2, now, route.legs());

        assertThat(quotation.assignRoute(another, now)).isEqualTo(RouteAssignmentResult.ANOTHER_ROUTE_VERSION_ASSIGNED);
        assertThat(quotation.assignedRoute()).contains(route);
        assertThat(quotation.domainEvents()).isEmpty();
    }

    @Test
    void 詳細設計依頼済みでない見積りと置換済み失効の見積りには割り当てない() {
        Quotation pending = pendingApproval();
        Quotation presented = presented();
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);
        Quotation expired = presented();
        expired.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-09T00:00:00Z"));

        assertThat(pending.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.NOT_ROUTING_REQUESTED);
        assertThat(presented.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.NOT_ROUTING_REQUESTED);
        assertThat(replaced.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.RETIRED);
        assertThat(expired.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.RETIRED);
        assertThat(presented.status()).isEqualTo(QuotationStatus.PRESENTED);
        assertThat(presented.assignedRoute()).isEmpty();
        assertThat(presented.domainEvents()).isEmpty();
    }

    @Test
    void 有効期限を過ぎた詳細設計依頼済みの見積りにも割り当て期限は承認で判定する() {
        Quotation quotation = routingRequested();
        UtcInstant afterExpiry = at("2099-10-08T09:00:00Z");

        assertThat(quotation.assignRoute(route, afterExpiry)).isEqualTo(RouteAssignmentResult.ASSIGNED);

        assertThat(quotation.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(quotation.approvalRejectionAt(afterExpiry)).contains(QuotationRejection.EXPIRED);
    }

    // 荷主の承認（US-24 AC4・AC5、Q-INV-07・10。DE-04）

    @Test
    void 荷主承認待ちの見積りを承認すると承認済みになり承認者と承認時刻を残しDE04を生成する() {
        Quotation quotation = awaitingApproval();

        assertThat(quotation.approveByShipper(shipperUser, now)).isEmpty();

        assertThat(quotation.status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(quotation.shipperApproval()).contains(new ShipperApproval(shipperUser, now));
        assertThat(quotation.assignedRoute()).as("承認した経路版は割り当てた経路").contains(route);
        assertThat(quotation.domainEvents())
                .singleElement()
                .isEqualTo(new QuotationApprovedByShipper(
                        id.value(), 1, transportRequestId.value(), 1, "RC-2026-0001", 1, shipperUser.value(), now));
    }

    @ParameterizedTest
    @CsvSource({"2099-10-08T08:59:00Z, true", "2099-10-08T09:00:00Z, false", "2099-10-08T09:01:00Z, false"})
    void 有効期限の1分前は承認でき同時刻と1分後は失効として拒否する(String approvedAt, boolean accepted) {
        Quotation quotation = awaitingApproval();

        assertThat(quotation.approveByShipper(shipperUser, at(approvedAt)))
                .isEqualTo(accepted ? Optional.empty() : Optional.of(QuotationRejection.EXPIRED));
        assertThat(quotation.status())
                .isEqualTo(accepted ? QuotationStatus.APPROVED : QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(quotation.shipperApproval().isPresent()).isEqualTo(accepted);
        assertThat(quotation.domainEvents()).hasSize(accepted ? 1 : 0);
    }

    @Test
    void 割当ての前と置換済み失効と承認済みの見積りは承認できない() {
        Quotation routingRequested = routingRequested();
        Quotation presented = presented();
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);
        Quotation expired = presented();
        expired.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-09T00:00:00Z"));
        Quotation approved = awaitingApproval();
        approved.approveByShipper(shipperUser, now);
        approved.clearDomainEvents();

        assertThat(routingRequested.approveByShipper(shipperUser, now))
                .contains(QuotationRejection.NOT_AWAITING_SHIPPER_APPROVAL);
        assertThat(presented.approveByShipper(shipperUser, now))
                .contains(QuotationRejection.NOT_AWAITING_SHIPPER_APPROVAL);
        assertThat(replaced.approveByShipper(shipperUser, now)).contains(QuotationRejection.REPLACED);
        assertThat(expired.approveByShipper(shipperUser, now)).contains(QuotationRejection.EXPIRED);
        assertThat(approved.approveByShipper(shipperUser, at("2099-10-08T09:00:00Z")))
                .as("承認済みは期限の後でも承認済みとして示す")
                .contains(QuotationRejection.ALREADY_APPROVED);
        assertThat(approved.domainEvents()).isEmpty();
        assertThat(routingRequested.status()).isEqualTo(QuotationStatus.ROUTING_REQUESTED);
    }

    @Test
    void 荷主承認待ちと承認済みの見積りは再見積りできない() {
        Quotation awaiting = awaitingApproval();
        Quotation approved = awaitingApproval();
        approved.approveByShipper(shipperUser, now);

        assertThat(awaiting.requoteRejection()).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(awaiting.replaceWith(new QuotationId(UUID.randomUUID()), now))
                .contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(awaiting.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(approved.requoteRejection()).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(awaiting.approvalRejectionAt(now)).as("承認できるかを判定時刻で問い合わせられる").isEmpty();
    }

    @Test
    void 荷主承認待ちと承認済みの見積りは有効期限と同時刻から失効として扱い数える見積りに入る() {
        Quotation awaiting = awaitingApproval();
        Quotation approved = awaitingApproval();
        approved.approveByShipper(shipperUser, now);

        for (Quotation quotation : List.of(awaiting, approved)) {
            assertThat(quotation.isExpiredAt(at("2099-10-08T08:59:59Z"))).isFalse();
            assertThat(quotation.isExpiredAt(at("2099-10-08T09:00:00Z"))).isTrue();
            assertThat(quotation.isActive()).isTrue();
            assertThat(quotation.isVisibleToShipper()).isTrue();
            assertThat(quotation.responseRejectionAt(now)).contains(QuotationRejection.ROUTING_REQUESTED);
        }
    }

    // 予約確定に使えるか（Q-INV-06、BR-10、ADR-016。Bolt 23）

    @ParameterizedTest
    @CsvSource({"2099-10-08T08:59:00Z, true", "2099-10-08T09:00:00Z, false", "2099-10-08T09:01:00Z, false"})
    void 承認済みの見積りはcommit時刻が有効期限の1分前なら予約確定に使え同時刻と1分後は失効(String committedAt, boolean bookable) {
        Quotation approved = awaitingApproval();
        approved.approveByShipper(shipperUser, now);

        assertThat(approved.bookingRejectionAt(at(committedAt)))
                .isEqualTo(bookable ? Optional.empty() : Optional.of(QuotationRejection.EXPIRED));
    }

    @Test
    void 承認済みでない見積りと置換済み失効の見積りは予約確定に使えない() {
        Quotation awaiting = awaitingApproval();
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);
        Quotation expired = presented();
        expired.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-09T00:00:00Z"));

        assertThat(awaiting.bookingRejectionAt(now)).contains(QuotationRejection.NOT_APPROVED);
        assertThat(routingRequested().bookingRejectionAt(now)).contains(QuotationRejection.NOT_APPROVED);
        assertThat(replaced.bookingRejectionAt(now)).contains(QuotationRejection.REPLACED);
        assertThat(expired.bookingRejectionAt(now)).contains(QuotationRejection.EXPIRED);
    }

    private Quotation pendingApproval() {
        Quotation quotation = Quotation.create(id, transportRequestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        return quotation;
    }

    private Quotation presented() {
        Quotation quotation = pendingApproval();
        quotation.presentInternally(salesRep, at("2026-10-05T04:30:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    private Quotation routingRequested() {
        Quotation quotation = presented();
        quotation.requestRouteDesign(shipperUser, at("2026-10-06T02:00:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    private Quotation awaitingApproval() {
        Quotation quotation = routingRequested();
        quotation.assignRoute(route, at("2026-10-08T02:30:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}

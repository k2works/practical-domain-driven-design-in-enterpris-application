package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.QuotationPresentedForReapproval;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteAssignmentResult;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutePolicy;
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
    private static final UtcInstant EXPIRES = QuotationFixture.EXPIRES_AT;
    /** 旧版の経路方針（QuotationFixture.completeInput のシンガポール経由）。 */
    private static final RoutePolicy OLD_POLICY = new RoutePolicy(
            List.of(new Location("SGSIN")),
            new UtcInstant(Instant.parse("2099-10-10T00:00:00Z")),
            new UtcInstant(Instant.parse("2099-10-30T09:00:00Z")));

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
        assertThat(quotation.approvalRejectionAt(at("2099-10-08T08:59:59Z")))
                .as("承認できるかを判定時刻で問い合わせられる")
                .isEmpty();
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
    @CsvSource({
        "2099-10-08T08:59:00Z, true",
        "2099-10-08T08:59:59.999999Z, true",
        "2099-10-08T09:00:00Z, false",
        "2099-10-08T09:01:00Z, false"
    })
    void 承認済みの見積りはcommit時刻が有効期限の1分前と1マイクロ秒前なら予約確定に使え同時刻と1分後は失効(String committedAt, boolean bookable) {
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

    // 荷主の承認の後に失効した見積りの再見積り（D-85。Bolt 35b）

    @ParameterizedTest
    @CsvSource({"2099-10-08T08:59:00Z", "2099-10-08T08:59:59.999999Z"})
    void 承認済みの見積りは有効期限の1分前と1マイクロ秒前は期限内として再見積りできない(String instant) {
        Quotation approved = approved();
        UtcInstant before = at(instant);

        assertThat(approved.requoteRejectionAt(before)).contains(QuotationRejection.APPROVED_AND_STILL_VALID);
        assertThat(approved.replaceWith(new QuotationId(UUID.randomUUID()), before))
                .contains(QuotationRejection.APPROVED_AND_STILL_VALID);
        assertThat(approved.status()).isEqualTo(QuotationStatus.APPROVED);
        Quotation next = calculatedNext(transportRequestId);
        assertThat(approved.requoteInto(next, before)).contains(QuotationRejection.APPROVED_AND_STILL_VALID);
        assertThat(next.assignedRoute()).as("拒んだら引き継がない").isEmpty();
    }

    @Test
    void 承認済みでなければ再見積りの判定は期限の前後で変わらない() {
        Quotation replaced = presented();
        replaced.replaceWith(new QuotationId(UUID.randomUUID()), now);
        Quotation expired = presented();
        expired.replaceWith(new QuotationId(UUID.randomUUID()), at("2099-10-09T00:00:00Z"));
        List<Quotation> quotations = List.of(
                Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 1),
                pendingApproval(),
                presented(),
                routingRequested(),
                awaitingApproval(),
                replaced,
                expired);

        assertThat(quotations).extracting(Quotation::status).doesNotContain(QuotationStatus.APPROVED);
        for (Quotation quotation : quotations) {
            for (UtcInstant at : List.of(EXPIRES, at("2099-10-09T00:00:00Z"))) {
                assertThat(quotation.requoteRejectionAt(at))
                        .as("%s at %s", quotation.status(), at)
                        .isEqualTo(quotation.requoteRejectionAt(now));
            }
        }
    }

    @Test
    void 承認済みの見積りは有効期限と同時刻と1分後から再見積りできる() {
        assertThat(approved().requoteRejectionAt(EXPIRES)).isEmpty();
        assertThat(approved()
                        .requoteRejectionAt(new UtcInstant(EXPIRES.instant().plusSeconds(60))))
                .isEmpty();
    }

    @Test
    void 荷主承認待ちと詳細設計依頼済みは期限を過ぎても再見積りできない() {
        UtcInstant after = new UtcInstant(EXPIRES.instant().plusSeconds(60));

        Quotation awaiting = awaitingApproval();

        assertThat(awaiting.requoteRejectionAt(after)).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(routingRequested().requoteRejectionAt(after)).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(awaiting.requoteInto(calculatedNext(transportRequestId), after))
                .contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(awaiting.replaceWith(new QuotationId(UUID.randomUUID()), after))
                .contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(awaiting.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
    }

    @Test
    void 承認済みで失効した見積りを再見積りすると失効になり置換先を持たず承認の記録が残る() {
        Quotation approved = approved();

        assertThat(approved.requoteInto(calculatedNext(transportRequestId), EXPIRES))
                .isEmpty();

        assertThat(approved.status()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(approved.replacedBy()).isEmpty();
        assertThat(approved.shipperApproval()).isPresent();
    }

    @Test
    void 承認済みで失効した見積りを再見積りすると新しい見積りが経路と荷主の回答と経路方針を引き継ぐ() {
        Quotation next = calculatedNext(transportRequestId);
        RoutePolicy inputPolicy = next.routePolicy().orElseThrow();

        approved().requoteInto(next, EXPIRES);

        assertThat(next.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(next.assignedRoute()).contains(route);
        assertThat(next.respondedBy()).contains(shipperUser);
        assertThat(next.respondedAt()).contains(at("2026-10-06T02:00:00Z"));
        assertThat(next.routePolicy()).as("入力の経路方針でなく旧版の経路方針").contains(OLD_POLICY);
        assertThat(inputPolicy).isNotEqualTo(OLD_POLICY);
    }

    @Test
    void 経路を持たない見積りを再見積りしても新しい見積りは何も引き継がず入力の経路方針のまま() {
        Quotation old = presented();
        Quotation next = calculatedNext(transportRequestId);
        RoutePolicy inputPolicy = next.routePolicy().orElseThrow();

        assertThat(old.requoteInto(next, now)).isEmpty();

        assertThat(old.status()).isEqualTo(QuotationStatus.REPLACED);
        assertThat(old.replacedBy()).contains(next.id());
        assertThat(next.assignedRoute()).isEmpty();
        assertThat(next.respondedBy()).isEmpty();
        assertThat(next.routePolicy()).contains(inputPolicy);
    }

    @Test
    void 経路を持つ見積りは置換先のIDだけでは置き換えられない() {
        Quotation approved = approved();
        QuotationId replacement = new QuotationId(UUID.randomUUID());

        assertThatThrownBy(() -> approved.replaceWith(replacement, EXPIRES)).isInstanceOf(IllegalStateException.class);
        assertThat(approved.status()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    void 経路を引き継いだ見積りは提示で荷主承認待ちになりDE03を出しDE21を出さない() {
        Quotation next = inherited();

        assertThat(next.presentInternally(salesRep, now)).isEmpty();

        assertThat(next.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(next.assignedRoute()).contains(route);
        assertThat(next.respondedBy()).contains(shipperUser);
        assertThat(next.respondedAt()).contains(at("2026-10-06T02:00:00Z"));
        assertThat(next.domainEvents()).anyMatch(QuotationPresented.class::isInstance);
        assertThat(next.domainEvents()).noneMatch(QuotationRouteAssigned.class::isInstance);
    }

    @Test
    void 経路を引き継いだ見積りの提示はDE03の後に再承認待ちにしたDE24を出す() {
        Quotation next = inherited();

        next.presentInternally(salesRep, now);

        assertThat(next.domainEvents()).hasSize(2);
        assertThat(next.domainEvents().get(0)).isInstanceOf(QuotationPresented.class);
        assertThat(next.domainEvents().get(1))
                .isEqualTo(
                        new QuotationPresentedForReapproval(next.id().value(), 2, transportRequestId.value(), 1, now));
    }

    @Test
    void 経路を持たない見積りの提示はDE24を出さない() {
        Quotation pending = pendingApproval();

        pending.presentInternally(salesRep, now);

        assertThat(pending.domainEvents()).noneMatch(QuotationPresentedForReapproval.class::isInstance);
    }

    @Test
    void 経路を引き継いだ見積りは荷主の回答を拒み経路の割当ては状態ごとの結果になり荷主の承認で承認済みになる() {
        Quotation pending = inherited();
        assertThat(pending.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.NOT_ROUTING_REQUESTED);

        Quotation next = inherited();
        next.presentInternally(salesRep, now);
        assertThat(next.requestRouteDesign(shipperUser, now)).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(next.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.ALREADY_ASSIGNED);
        assertThat(next.assignRoute(new AssignedRoute("RC-2026-0001", 2, now, route.legs()), now))
                .isEqualTo(RouteAssignmentResult.ANOTHER_ROUTE_VERSION_ASSIGNED);
        assertThat(next.approveByShipper(shipperUser, now)).isEmpty();
        assertThat(next.status()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    void 経路を引き継いだ承認待ちの見積りを再見積りすると次の見積りがまた引き継ぎ旧版は置換済みになる() {
        Quotation pending = inherited();
        Quotation third = calculatedNext(transportRequestId);

        assertThat(pending.requoteInto(third, now)).isEmpty();

        assertThat(pending.status()).isEqualTo(QuotationStatus.REPLACED);
        assertThat(third.assignedRoute()).contains(route);
        assertThat(third.respondedBy()).contains(shipperUser);
        assertThat(third.routePolicy()).contains(OLD_POLICY);
    }

    @Test
    void 再見積りは同じ輸送要求の同じ版の算出した見積りだけを新しい見積りにし誤りなら旧版を変えない() {
        Quotation otherRequest = calculatedNext(new TransportRequestId(UUID.randomUUID()));
        Quotation otherVersion = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 2, 2);
        otherVersion.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        Quotation draft = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 2);
        Quotation presentedNext = calculatedNext(transportRequestId);
        presentedNext.presentInternally(salesRep, now);
        Quotation approved = approved();

        assertThatThrownBy(() -> approved.requoteInto(otherRequest, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> approved.requoteInto(otherVersion, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> approved.requoteInto(draft, EXPIRES)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approved.requoteInto(presentedNext, EXPIRES))
                .isInstanceOf(IllegalStateException.class);
        assertThat(approved.status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(otherRequest.assignedRoute()).isEmpty();
    }

    private Quotation approved() {
        Quotation quotation = awaitingApproval();
        quotation.approveByShipper(shipperUser, now);
        quotation.clearDomainEvents();
        return quotation;
    }

    /** 承認済みで失効した見積り 1 から経路を引き継いだ承認待ちの見積り 2。 */
    private Quotation inherited() {
        Quotation next = calculatedNext(transportRequestId);
        approved().requoteInto(next, EXPIRES);
        return next;
    }

    /** 旧版と別の経路方針（香港経由）の入力で算出した、輸送要求の版 1 の承認待ちの見積り 2。 */
    private Quotation calculatedNext(TransportRequestId requestId) {
        QuotationInput input = QuotationFixture.completeInput();
        Quotation next = Quotation.create(new QuotationId(UUID.randomUUID()), requestId, 1, 2);
        next.calculate(
                new QuotationInput(
                        input.lines(),
                        input.currency(),
                        input.expiresAt(),
                        List.of(new Location("HKHKG")),
                        at("2099-10-11T00:00:00Z"),
                        at("2099-10-31T00:00:00Z")),
                at("2026-10-05T04:00:00Z"));
        return next;
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

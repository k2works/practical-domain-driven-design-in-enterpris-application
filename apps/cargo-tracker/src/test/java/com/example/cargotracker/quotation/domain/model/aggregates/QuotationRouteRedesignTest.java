package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteAssignmentResult;
import com.example.cargotracker.quotation.domain.model.valueobjects.RouteRedesignMarkResult;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 割り当てた経路の再設計要の印と、荷主の承認・本予約の判定（US-07 AC5、Q-INV-10、B-INV-05。Bolt 37e）。状態の組み合わせの表の行ごとに
 * 1 本（T-163）。割り当てた経路は案件 RC-2026-0001 の経路版 1。有効期限は QuotationFixture.EXPIRES_AT = 2099-10-08T09:00:00Z で、
 * 判定時刻（2026-10-08）は期限の前。
 */
class QuotationRouteRedesignTest {

    private static final String CASE = "RC-2026-0001";
    private static final UtcInstant EXPIRES = QuotationFixture.EXPIRES_AT;
    private static final UtcInstant MARKED_AT = at("2026-10-08T04:00:00Z");

    private final QuotationId id = new QuotationId(UUID.randomUUID());
    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());
    private final UserId salesRep = new UserId(UUID.randomUUID());
    private final UserId shipperUser = new UserId(UUID.randomUUID());
    private final UtcInstant now = at("2026-10-08T05:00:00Z");

    private final AssignedRoute route = new AssignedRoute(
            CASE,
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

    // 荷主の承認と本予約の判定（行 1〜7）

    @Test
    void 印のない荷主承認待ちは承認でき本予約は承認済みでない() {
        // 行 1
        Quotation quotation = awaitingApproval();

        assertThat(quotation.approvalRejectionAt(now)).isEmpty();
        assertThat(quotation.bookingRejectionAt(now)).contains(QuotationRejection.NOT_APPROVED);
    }

    @Test
    void 印のある荷主承認待ちは再設計要で承認できず承認しても変わらない() {
        // 行 2
        Quotation quotation = marked(awaitingApproval());

        assertThat(quotation.approvalRejectionAt(now)).contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
        assertThat(quotation.approveByShipper(shipperUser, now)).contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(quotation.shipperApproval()).isEmpty();
        assertThat(quotation.domainEvents()).isEmpty();
        assertThat(quotation.bookingRejectionAt(now)).contains(QuotationRejection.NOT_APPROVED);
    }

    @Test
    void 印のない承認済みは本予約に使える() {
        // 行 3
        Quotation quotation = approved();

        assertThat(quotation.approvalRejectionAt(now)).contains(QuotationRejection.ALREADY_APPROVED);
        assertThat(quotation.bookingRejectionAt(now)).isEmpty();
    }

    @Test
    void 印のある承認済みは承認の判定は承認済みのままで本予約は再設計要() {
        // 行 4
        Quotation quotation = marked(approved());

        assertThat(quotation.approvalRejectionAt(now)).contains(QuotationRejection.ALREADY_APPROVED);
        assertThat(quotation.bookingRejectionAt(now)).contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
        assertThat(quotation.status()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    void 印のある荷主承認待ちも期限切れなら失効を先に示す() {
        // 行 5a
        Quotation quotation = marked(awaitingApproval());

        assertThat(quotation.approvalRejectionAt(EXPIRES)).contains(QuotationRejection.EXPIRED);
        assertThat(quotation.bookingRejectionAt(EXPIRES)).contains(QuotationRejection.EXPIRED);
    }

    @Test
    void 印のある承認済みが期限切れなら承認は承認済みで本予約は失効() {
        // 行 5b
        Quotation quotation = marked(approved());

        assertThat(quotation.approvalRejectionAt(EXPIRES)).contains(QuotationRejection.ALREADY_APPROVED);
        assertThat(quotation.bookingRejectionAt(EXPIRES)).contains(QuotationRejection.EXPIRED);
    }

    @Test
    void 失効と置換済みの記録を持つ見積りは印があっても失効と置換済みを先に示す() {
        // 行 6
        Quotation expired = approved();
        expired.requoteInto(calculatedNext(), EXPIRES);
        Quotation replaced = inherited();
        replaced.requoteInto(calculatedNext(), EXPIRES);
        assertThat(expired.markRouteRedesignRequired(CASE, 1, MARKED_AT)).isEqualTo(RouteRedesignMarkResult.MARKED);
        assertThat(replaced.markRouteRedesignRequired(CASE, 1, MARKED_AT)).isEqualTo(RouteRedesignMarkResult.MARKED);

        assertThat(expired.approvalRejectionAt(now)).contains(QuotationRejection.EXPIRED);
        assertThat(expired.bookingRejectionAt(now)).contains(QuotationRejection.EXPIRED);
        assertThat(replaced.approvalRejectionAt(now)).contains(QuotationRejection.REPLACED);
        assertThat(replaced.bookingRejectionAt(now)).contains(QuotationRejection.REPLACED);
    }

    @Test
    void 経路を引き継いだ承認待ちに印があると提示の前は荷主承認待ちでなく提示すると再設計要() {
        // 行 7（再見積りは旧版の有効期限 EXPIRES に行うので、提示と判定はその後）
        Quotation next = marked(inherited());
        UtcInstant afterRequote = new UtcInstant(EXPIRES.instant().plusSeconds(3_600));

        assertThat(next.approvalRejectionAt(afterRequote)).contains(QuotationRejection.NOT_AWAITING_SHIPPER_APPROVAL);
        assertThat(next.bookingRejectionAt(afterRequote)).contains(QuotationRejection.NOT_APPROVED);
        next.presentInternally(salesRep, afterRequote);
        assertThat(next.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(next.approvalRejectionAt(afterRequote)).contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
    }

    // 印を付ける操作と再見積り（行 8〜14、19）

    @Test
    void 同じ経路版を割り当てた荷主承認待ちと承認済みに印を付けると時刻を写し状態は変えない() {
        // 行 8
        Quotation awaiting = awaitingApproval();
        Quotation approved = approved();

        assertThat(awaiting.markRouteRedesignRequired(CASE, 1, MARKED_AT)).isEqualTo(RouteRedesignMarkResult.MARKED);
        assertThat(approved.markRouteRedesignRequired(CASE, 1, MARKED_AT)).isEqualTo(RouteRedesignMarkResult.MARKED);

        assertThat(awaiting.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(MARKED_AT);
        assertThat(awaiting.assignedRoute().orElseThrow().isSameVersionAs(route))
                .isTrue();
        assertThat(awaiting.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(approved.status()).isEqualTo(QuotationStatus.APPROVED);
        assertThat(awaiting.domainEvents()).isEmpty();
    }

    @Test
    void もう一度印を付けても最初の時刻のまま() {
        // 行 9
        Quotation quotation = marked(awaitingApproval());

        assertThat(quotation.markRouteRedesignRequired(CASE, 1, at("2026-10-08T06:00:00Z")))
                .isEqualTo(RouteRedesignMarkResult.ALREADY_MARKED);
        assertThat(quotation.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(MARKED_AT);
    }

    @Test
    void 経路を割り当てていない見積りには付けない() {
        // 行 10
        Quotation quotation = routingRequested();

        assertThat(quotation.markRouteRedesignRequired(CASE, 1, MARKED_AT)).isEqualTo(RouteRedesignMarkResult.NO_ROUTE);
        assertThat(quotation.assignedRoute()).isEmpty();
    }

    @Test
    void 別の経路版と別の案件の経路には付けない() {
        // 行 11
        Quotation quotation = awaitingApproval();

        assertThat(quotation.markRouteRedesignRequired(CASE, 2, MARKED_AT))
                .isEqualTo(RouteRedesignMarkResult.ANOTHER_ROUTE_VERSION);
        assertThat(quotation.markRouteRedesignRequired("RC-2026-0002", 1, MARKED_AT))
                .isEqualTo(RouteRedesignMarkResult.ANOTHER_ROUTE_VERSION);
        assertThat(quotation.assignedRoute().orElseThrow().redesignRequired()).isFalse();
    }

    @Test
    void 失効と置換済みの見積りにも同じ経路版なら付ける() {
        // 行 12（判定には効かない。記録をそろえる）
        Quotation expired = approved();
        expired.requoteInto(calculatedNext(), EXPIRES);

        Quotation replaced = inherited();
        replaced.requoteInto(calculatedNext(), EXPIRES);

        assertThat(expired.markRouteRedesignRequired(CASE, 1, MARKED_AT)).isEqualTo(RouteRedesignMarkResult.MARKED);
        assertThat(replaced.markRouteRedesignRequired(CASE, 1, MARKED_AT)).isEqualTo(RouteRedesignMarkResult.MARKED);
        assertThat(expired.status()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(replaced.status()).isEqualTo(QuotationStatus.REPLACED);
        assertThat(expired.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(MARKED_AT);
        assertThat(replaced.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(MARKED_AT);
    }

    @Test
    void 承認の後に失効した印のある見積りを再見積りすると新しい見積りに印が写り提示しても承認できない() {
        // 行 13（H1）
        Quotation old = marked(approved());
        Quotation next = calculatedNext();

        assertThat(old.requoteInto(next, EXPIRES)).isEmpty();
        next.presentInternally(salesRep, EXPIRES);

        assertThat(next.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(MARKED_AT);
        assertThat(next.approvalRejectionAt(EXPIRES)).contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
    }

    @Test
    void 印のある荷主承認待ちと承認済みは期限内には今の理由で再見積りできない() {
        // 行 14（期限内は出口がない。担当者の相談と US-08）
        assertThat(marked(awaitingApproval()).requoteRejectionAt(now)).contains(QuotationRejection.ROUTING_REQUESTED);
        assertThat(marked(approved()).requoteRejectionAt(now)).contains(QuotationRejection.APPROVED_AND_STILL_VALID);
    }

    @Test
    void 印のある荷主承認待ちは有効期限の後に再見積りでき旧版は失効し新しい見積りは経路と印を引き継ぐ() {
        // 行 14b（Bolt 37e の終了報告の承認の場で決めた出口。D-86 を印のある荷主承認待ちだけ変える）
        Quotation old = marked(awaitingApproval());
        Quotation next = calculatedNext();

        assertThat(old.requoteRejectionAt(EXPIRES)).isEmpty();
        assertThat(old.requoteInto(next, EXPIRES)).isEmpty();

        assertThat(old.status()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(next.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(MARKED_AT);
        next.presentInternally(salesRep, EXPIRES);
        assertThat(next.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        assertThat(next.approvalRejectionAt(EXPIRES)).contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
    }

    @Test
    void 印のない荷主承認待ちは有効期限の後も再見積りできない() {
        // 行 14c（D-86 のまま。荷主の承認を待つ）
        assertThat(awaitingApproval().requoteRejectionAt(EXPIRES)).contains(QuotationRejection.ROUTING_REQUESTED);
    }

    @Test
    void 印のある見積りに同じ経路版がもう一度割り当てられても印は残る() {
        // 行 19（DE-05 の再配信）
        Quotation quotation = marked(awaitingApproval());

        assertThat(quotation.assignRoute(route, now)).isEqualTo(RouteAssignmentResult.ALREADY_ASSIGNED);
        assertThat(quotation.assignedRoute().orElseThrow().redesignRequiredAt()).isEqualTo(MARKED_AT);
    }

    private Quotation marked(Quotation quotation) {
        quotation.markRouteRedesignRequired(CASE, 1, MARKED_AT);
        return quotation;
    }

    private Quotation approved() {
        Quotation quotation = awaitingApproval();
        quotation.approveByShipper(shipperUser, at("2026-10-08T03:00:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    /** 承認済みで失効した見積り 1 から経路を引き継いだ承認待ちの見積り 2。 */
    private Quotation inherited() {
        Quotation next = calculatedNext();
        approved().requoteInto(next, EXPIRES);
        return next;
    }

    /** 輸送要求の版 1 の承認待ちの見積り（再見積りの新しい見積り）。 */
    private Quotation calculatedNext() {
        QuotationInput input = QuotationFixture.completeInput();
        Quotation next = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 2);
        // 旧版の有効期限（EXPIRES）に再見積りするので、新しい見積りの有効期限はその後ろにする
        next.calculate(
                new QuotationInput(
                        input.lines(),
                        input.currency(),
                        at("2099-12-01T00:00:00Z"),
                        input.via(),
                        input.departureAt(),
                        input.arrivalAt()),
                at("2026-10-05T04:00:00Z"));
        return next;
    }

    private Quotation routingRequested() {
        Quotation quotation = Quotation.create(id, transportRequestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        quotation.presentInternally(salesRep, at("2026-10-05T04:30:00Z"));
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

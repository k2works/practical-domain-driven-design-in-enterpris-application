package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.ApproveQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.ShipperApprovalOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipperApproval;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 確定した経路の割当てと荷主の承認（US-24 AC4・AC5、R-INV-11、Q-INV-07・08・10、DE-21・DE-04）のステップ定義。
 * 荷主の承認は荷主の回答の入力ポートだけを呼ぶ（AT-05）。割当ては経路設計の確定（DE-05）の配信で起きる。
 */
public class ShipperApprovalSteps {

    /** 提出したのと同じ荷主（TransportRequestSteps と同じ値）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private static final UserId APPROVER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000102"));

    private static final Map<String, QuotationRejection> REJECTIONS = Map.of(
            "失効している", QuotationRejection.EXPIRED,
            "置換済み", QuotationRejection.REPLACED,
            "承認済み", QuotationRejection.ALREADY_APPROVED,
            "まだ承認できない", QuotationRejection.NOT_AWAITING_SHIPPER_APPROVAL);

    private final QuotationResponseService responseService;
    private final StaffQuotationQueryService staffQueryService;
    private final DeferredEventDelivery delivery;
    private final InMemoryTransportRequestRepository transportRequests;
    private final ScenarioContext context;
    private ShipperApprovalOutcome lastOutcome;

    public ShipperApprovalSteps(
            QuotationResponseService responseService,
            StaffQuotationQueryService staffQueryService,
            DeferredEventDelivery delivery,
            InMemoryTransportRequestRepository transportRequests,
            ScenarioContext context) {
        this.responseService = responseService;
        this.staffQueryService = staffQueryService;
        this.delivery = delivery;
        this.transportRequests = transportRequests;
        this.context = context;
    }

    @もし("荷主担当者が見積り {int} の見積りと経路を承認する")
    public void 見積りと経路を承認する(int quotationNo) {
        lastOutcome = responseService.approve(new ApproveQuotationCommand(number(), quotationNo, SHIPPER, APPROVER));
    }

    @もし("他社の荷主担当者が見積り {int} の見積りと経路を承認する")
    public void 他社が見積りと経路を承認する(int quotationNo) {
        lastOutcome =
                responseService.approve(new ApproveQuotationCommand(number(), quotationNo, OTHER_SHIPPER, APPROVER));
    }

    @ならば("承認の結果は {string} である")
    public void 承認の結果(String result) {
        switch (result) {
            case "承認した" -> assertThat(lastOutcome).isInstanceOf(ShipperApprovalOutcome.Approved.class);
            case "見つからない" -> assertThat(lastOutcome).isInstanceOf(ShipperApprovalOutcome.NotFound.class);
            default ->
                assertThat(lastOutcome).isEqualTo(new ShipperApprovalOutcome.Rejected(named(REJECTIONS, result)));
        }
    }

    @ならば("見積り {int} に案件 {string} の経路版 {int} が割り当てられ、区間は {string} で確定の時刻は {string} である")
    public void 経路版が割り当てられている(int quotationNo, String caseNumber, int routeVersionNo, String legs, String confirmedAt) {
        assertThat(staffQueryService.find(number(), quotationNo)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
            assertThat(quotation.assignedRoute()).hasValueSatisfying(route -> {
                assertThat(route.routingCaseNumber()).isEqualTo(caseNumber);
                assertThat(route.routeVersionNo()).isEqualTo(routeVersionNo);
                assertThat(route.confirmedAt()).isEqualTo(at(confirmedAt));
                assertThat(route.legs().stream().map(ShipperApprovalSteps::leg).collect(Collectors.joining(", ")))
                        .isEqualTo(legs);
            });
        });
    }

    @ならば("見積り {int} に経路版は割り当てられていない")
    public void 経路版は割り当てられていない(int quotationNo) {
        assertThat(staffQueryService.find(number(), quotationNo))
                .hasValueSatisfying(
                        quotation -> assertThat(quotation.assignedRoute()).isEmpty());
    }

    @ならば("見積りに経路版を割り当てたイベントが {int} 回だけ発行されている")
    public void 割り当てたイベントの回数(int times) {
        assertThat(delivery.published())
                .filteredOn(QuotationRouteAssigned.class::isInstance)
                .hasSize(times);
    }

    @ならば("見積り {int} は承認済みになり、承認者と承認時刻 {string} と承認した経路版 {string} の {int} が残る")
    public void 承認が残る(int quotationNo, String approvedAt, String caseNumber, int routeVersionNo) {
        assertThat(staffQueryService.find(number(), quotationNo)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(QuotationStatus.APPROVED);
            assertThat(quotation.shipperApproval()).contains(new ShipperApproval(APPROVER, at(approvedAt)));
            assertThat(quotation.assignedRoute()).hasValueSatisfying(route -> {
                assertThat(route.routingCaseNumber()).isEqualTo(caseNumber);
                assertThat(route.routeVersionNo()).isEqualTo(routeVersionNo);
            });
        });
    }

    @ならば("見積り {int} を荷主が承認したイベントが発行される")
    public void 承認したイベントが発行される(int quotationNo) {
        assertThat(delivery.published())
                .filteredOn(QuotationApprovedByShipper.class::isInstance)
                .map(QuotationApprovedByShipper.class::cast)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.quotationNo()).isEqualTo(quotationNo);
                    assertThat(event.transportRequestId()).isEqualTo(context.transportRequestId());
                    assertThat(event.approvedBy()).isEqualTo(APPROVER.value());
                    assertThat(event.routingCaseNumber()).isEqualTo("RC-2026-0001");
                    assertThat(event.routeVersionNo()).isEqualTo(1);
                });
    }

    @ならば("見積りを荷主が承認したイベントは発行されない")
    public void 承認したイベントは発行されない() {
        assertThat(delivery.published()).noneMatch(QuotationApprovedByShipper.class::isInstance);
    }

    private TransportRequestNumber number() {
        TransportRequest request = transportRequests
                .findById(new TransportRequestId(context.transportRequestId()))
                .orElseThrow();
        return request.number();
    }

    private static String leg(AssignedRouteLeg leg) {
        return leg.voyageNumber() + " " + leg.load().unLocode() + "→"
                + leg.discharge().unLocode();
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    /** シナリオの呼び名を値にする。知らない呼び名はシナリオの書き間違いとして分かる形で失敗させる。 */
    private static <T> T named(Map<String, T> names, String name) {
        T value = names.get(name);
        if (value == null) {
            throw new IllegalArgumentException("シナリオの呼び名: " + name);
        }
        return value;
    }
}

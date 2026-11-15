package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.ConsultQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.DeclineQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ConsultOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.DeclineOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteDesignRequestOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.domain.events.QuotationConsultationRequested;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 荷主の見積りへの回答（US-24 AC1、Q-INV-07・08・09、DE-16）のステップ定義。荷主の回答の入力ポートだけを呼ぶ（AT-05）。
 * 回答者は、認証（US-18）ができるまでの仮の荷主の利用者とする。
 */
public class QuotationResponseSteps {

    /** 営業担当者（荷主 2 社を担当する。Bolt 38f）。 */
    private static final AuthenticatedActor OPERATOR = AuthenticatedActorFixture.salesHandling(
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301")),
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002")));

    /** 提出したのと同じ荷主（TransportRequestSteps と同じ値）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private static final UserId RESPONDENT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    private static final Map<String, QuotationStatus> STATUSES =
            Map.of("提示済み", QuotationStatus.PRESENTED, "詳細設計依頼済み", QuotationStatus.ROUTING_REQUESTED);

    private static final Map<String, TransportRequestStatus> REQUEST_STATUSES = Map.of(
            "見積提示済み", TransportRequestStatus.QUOTED,
            "経路設計中", TransportRequestStatus.ROUTING,
            "荷主承認待ち", TransportRequestStatus.AWAITING_APPROVAL,
            "予約待ち", TransportRequestStatus.READY_TO_BOOK,
            "予約確定済み", TransportRequestStatus.BOOKED);

    private static final Map<String, QuotationRejection> REJECTIONS = Map.of(
            "失効している", QuotationRejection.EXPIRED,
            "置換済み", QuotationRejection.REPLACED,
            "回答済み", QuotationRejection.ROUTING_REQUESTED);

    private final QuotationResponseService responseService;
    private final StaffQuotationQueryService staffQueryService;
    private final DeferredEventDelivery delivery;
    private final InMemoryTransportRequestRepository transportRequests;
    private final ScenarioContext context;
    private RouteDesignRequestOutcome lastOutcome;
    private DeclineOutcome lastDecline;
    private ConsultOutcome lastConsult;
    private long rememberedVersion;

    public QuotationResponseSteps(
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

    /** 背景の共通の前提（{@link RouteDesignRequestedSteps}）からも呼ぶ（変えると 8 つの feature に波及する）。 */
    @もし("荷主担当者が見積り {int} について詳細経路設計へ進むと回答する")
    public void 詳細経路設計へ進むと回答する(int quotationNo) {
        lastOutcome = responseService.requestRouteDesign(
                new RequestRouteDesignCommand(number(), quotationNo, SHIPPER, RESPONDENT));
    }

    @もし("他社の荷主担当者が見積り {int} について詳細経路設計へ進むと回答する")
    public void 他社が詳細経路設計へ進むと回答する(int quotationNo) {
        lastOutcome = responseService.requestRouteDesign(
                new RequestRouteDesignCommand(number(), quotationNo, OTHER_SHIPPER, RESPONDENT));
    }

    /** 荷主の条件の相談（US-24 AC3。W9 の Bolt 46-1）。 */
    @もし("荷主担当者が見積り {int} の条件を内容 {string} で相談する")
    public void 相談する(int quotationNo, String content) {
        lastConsult = responseService.consult(
                new ConsultQuotationCommand(number(), quotationNo, SHIPPER, RESPONDENT, content));
    }

    @ならば("相談の結果は {string} である")
    public void 相談の結果(String result) {
        switch (result) {
            case "相談した" -> assertThat(lastConsult).isInstanceOf(ConsultOutcome.Requested.class);
            case "見つからない" -> assertThat(lastConsult).isInstanceOf(ConsultOutcome.NotFound.class);
            case "内容の誤り" -> assertThat(lastConsult).isInstanceOf(ConsultOutcome.InvalidContent.class);
            default -> assertThat(lastConsult).isEqualTo(new ConsultOutcome.Rejected(named(REJECTIONS, result)));
        }
    }

    @ならば("見積り {int} の条件の相談のイベントが内容 {string} で発行される")
    public void 相談のイベントが発行される(int quotationNo, String content) {
        assertThat(delivery.published())
                .filteredOn(QuotationConsultationRequested.class::isInstance)
                .map(QuotationConsultationRequested.class::cast)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.quotationNo()).isEqualTo(quotationNo);
                    assertThat(event.transportRequestId()).isEqualTo(context.transportRequestId());
                    assertThat(event.shipperCompanyId()).isEqualTo(SHIPPER.value());
                    assertThat(event.requestedBy()).isEqualTo(RESPONDENT.value());
                    assertThat(event.content()).isEqualTo(content);
                });
    }

    @ならば("見積り {int} は {string} のまま回答者も辞退も記録されていない")
    public void 状態のまま回答も辞退もない(int quotationNo, String status) {
        assertThat(staffQueryService.find(number(), quotationNo, OPERATOR)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(named(STATUSES, status));
            assertThat(quotation.respondedBy()).isEmpty();
            assertThat(quotation.isDeclined()).isFalse();
        });
    }

    /** 荷主の辞退（US-24 AC2。W9 の Bolt 46）。 */
    @もし("荷主担当者が見積り {int} を理由 {string} で辞退する")
    public void 辞退する(int quotationNo, String reason) {
        lastDecline = responseService.decline(
                new DeclineQuotationCommand(number(), quotationNo, SHIPPER, RESPONDENT, reason));
    }

    @もし("他社の荷主担当者が見積り {int} を理由 {string} で辞退する")
    public void 他社が辞退する(int quotationNo, String reason) {
        lastDecline = responseService.decline(
                new DeclineQuotationCommand(number(), quotationNo, OTHER_SHIPPER, RESPONDENT, reason));
    }

    @ならば("辞退の結果は {string} である")
    public void 辞退の結果(String result) {
        switch (result) {
            case "辞退した" -> assertThat(lastDecline).isInstanceOf(DeclineOutcome.Declined.class);
            case "見つからない" -> assertThat(lastDecline).isInstanceOf(DeclineOutcome.NotFound.class);
            case "理由の誤り" -> assertThat(lastDecline).isInstanceOf(DeclineOutcome.InvalidReason.class);
            default -> assertThat(lastDecline).isEqualTo(new DeclineOutcome.Rejected(named(REJECTIONS, result)));
        }
    }

    @ならば("見積り {int} は失効として記録され、辞退の理由 {string} と回答者と辞退時刻 {string} が残る")
    public void 辞退が記録される(int quotationNo, String reason, String declinedAt) {
        assertThat(staffQueryService.find(number(), quotationNo, OPERATOR)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(QuotationStatus.EXPIRED);
            assertThat(quotation.isDeclined()).isTrue();
            assertThat(quotation.declineReason()).contains(reason);
            assertThat(quotation.respondedBy()).contains(RESPONDENT);
            assertThat(quotation.respondedAt()).contains(new UtcInstant(Instant.parse(declinedAt)));
        });
    }

    @ならば("見積り {int} は辞退として記録されていない")
    public void 辞退として記録されていない(int quotationNo) {
        assertThat(staffQueryService.find(number(), quotationNo, OPERATOR))
                .hasValueSatisfying(
                        quotation -> assertThat(quotation.isDeclined()).isFalse());
    }

    @ならば("回答の結果は {string} である")
    public void 回答の結果(String result) {
        switch (result) {
            case "依頼した" -> assertThat(lastOutcome).isInstanceOf(RouteDesignRequestOutcome.Requested.class);
            case "見つからない" -> assertThat(lastOutcome).isInstanceOf(RouteDesignRequestOutcome.NotFound.class);
            default ->
                assertThat(lastOutcome).isEqualTo(new RouteDesignRequestOutcome.Rejected(named(REJECTIONS, result)));
        }
    }

    @ならば("見積り {int} は {string} になり、回答者と回答時刻 {string} が残る")
    public void 回答者と回答時刻が残る(int quotationNo, String status, String respondedAt) {
        assertThat(staffQueryService.find(number(), quotationNo, OPERATOR)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(named(STATUSES, status));
            assertThat(quotation.respondedBy()).contains(RESPONDENT);
            assertThat(quotation.respondedAt()).contains(new UtcInstant(Instant.parse(respondedAt)));
        });
    }

    @ならば("見積り {int} の詳細経路設計を依頼したイベントが発行される")
    public void 依頼したイベントが発行される(int quotationNo) {
        assertThat(delivery.published())
                .filteredOn(RouteDesignRequested.class::isInstance)
                .map(RouteDesignRequested.class::cast)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.quotationNo()).isEqualTo(quotationNo);
                    assertThat(event.transportRequestId()).isEqualTo(context.transportRequestId());
                    assertThat(event.transportRequestVersionNo()).isEqualTo(1);
                    assertThat(event.requestedBy()).isEqualTo(RESPONDENT.value());
                    assertThat(event.routeVia()).containsExactly("SGSIN");
                    assertThat(event.expiresAt()).isEqualTo(new UtcInstant(Instant.parse("2026-10-08T09:00:00Z")));
                });
    }

    @ならば("輸送要求は {string} のまま")
    public void 輸送要求の状態のまま(String status) {
        assertThat(currentRequest().status()).isEqualTo(named(REQUEST_STATUSES, status));
    }

    @もし("輸送要求の集約の版を控える")
    public void 集約の版を控える() {
        rememberedVersion = currentRequest().aggregateVersion();
    }

    @ならば("輸送要求の集約の版は控えた版から変わらない")
    public void 集約の版は変わらない() {
        assertThat(currentRequest().aggregateVersion()).isEqualTo(rememberedVersion);
    }

    /** 版のずれを、版の違うイベントで作る（今の状態遷移では、回答の後に輸送要求の版は進まない）。 */
    @もし("版 {int} への詳細経路設計の依頼のイベントが購読するコンテキストに配信される")
    public void 版の違う依頼が届く(int versionNo) {
        UtcInstant at = new UtcInstant(Instant.parse("2026-10-06T02:00:00Z"));
        delivery.publishEvent(new RouteDesignRequested(
                UUID.randomUUID(),
                1,
                context.transportRequestId(),
                versionNo,
                List.of(),
                at,
                at,
                at,
                RESPONDENT.value(),
                at));
        delivery.deliverAll();
    }

    private TransportRequest currentRequest() {
        return transportRequests
                .findById(new TransportRequestId(context.transportRequestId()))
                .orElseThrow();
    }

    /** シナリオの呼び名を値にする。知らない呼び名はシナリオの書き間違いとして分かる形で失敗させる。 */
    private static <T> T named(Map<String, T> names, String name) {
        T value = names.get(name);
        if (value == null) {
            throw new IllegalArgumentException("シナリオの呼び名: " + name);
        }
        return value;
    }

    private TransportRequestNumber number() {
        return TransportRequestNumber.parse(context.transportRequestNumber());
    }
}

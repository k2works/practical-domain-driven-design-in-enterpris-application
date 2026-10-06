package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteDesignRequestOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
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

    /** 提出したのと同じ荷主（TransportRequestSteps と同じ値）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private static final UserId RESPONDENT = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    private static final Map<String, QuotationStatus> STATUSES =
            Map.of("提示済み", QuotationStatus.PRESENTED, "詳細設計依頼済み", QuotationStatus.ROUTING_REQUESTED);

    private static final Map<String, TransportRequestStatus> REQUEST_STATUSES =
            Map.of("見積提示済み", TransportRequestStatus.QUOTED, "経路設計中", TransportRequestStatus.ROUTING);

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
        assertThat(staffQueryService.find(number(), quotationNo)).hasValueSatisfying(quotation -> {
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

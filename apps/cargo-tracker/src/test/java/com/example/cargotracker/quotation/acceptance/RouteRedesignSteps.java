package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotification;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.Instant;
import java.util.UUID;

/**
 * 再設計要の経路を割り当てた見積り（US-07 AC5、Q-INV-10、B-INV-05。Bolt 37e）のステップ定義。経路設計の DE-06 の listener（Bolt 37e2）の
 * 代わりに、見積りの公開 API で再設計要を伝える。本予約の照会は予約が呼ぶ見積りの公開 API を直接呼ぶ（予約の画面の文言は Bolt 37e3）。
 */
public class RouteRedesignSteps {

    /** 営業担当者（荷主 2 社を担当する。Bolt 38f）。 */
    private static final AuthenticatedActor OPERATOR = AuthenticatedActorFixture.salesHandling(
            new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301")),
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002")));

    private final RouteRedesignNotification notification;
    private final BookableQuotationQuery bookableQuotationQuery;
    private final StaffQuotationQueryService staffQueryService;
    private final InMemoryTransportRequestRepository transportRequests;
    private final InMemoryQuotationRepository quotations;
    private final ScenarioContext context;
    private final MutableClock clock;
    private RouteRedesignNotificationReceipt lastReceipt;
    private long rememberedQuotationVersion;

    public RouteRedesignSteps(
            RouteRedesignNotification notification,
            BookableQuotationQuery bookableQuotationQuery,
            StaffQuotationQueryService staffQueryService,
            InMemoryTransportRequestRepository transportRequests,
            InMemoryQuotationRepository quotations,
            ScenarioContext context,
            MutableClock clock) {
        this.notification = notification;
        this.bookableQuotationQuery = bookableQuotationQuery;
        this.staffQueryService = staffQueryService;
        this.transportRequests = transportRequests;
        this.quotations = quotations;
        this.context = context;
        this.clock = clock;
    }

    @もし("案件 {string} の経路版 {int} が {string} に再設計要になったと見積りに伝わる")
    public void 再設計要が伝わる(String routingCaseNumber, int routeVersionNo, String requiredAt) {
        lastReceipt = notification.notifyRedesignRequired(
                new RouteRedesignNotificationRequest(routingCaseNumber, routeVersionNo, at(requiredAt)));
    }

    @ならば("伝えた結果は {int} 件の見積りに印を付けた である")
    public void 印を付けた(int count) {
        assertThat(lastReceipt).isEqualTo(new RouteRedesignNotificationReceipt.Marked(count));
    }

    @ならば("伝えた結果は 印は付いていた である")
    public void 印は付いていた() {
        assertThat(lastReceipt).isEqualTo(new RouteRedesignNotificationReceipt.AlreadyMarked());
    }

    @ならば("伝えた結果は 見積りが見つからない である")
    public void 見積りが見つからない() {
        assertThat(lastReceipt)
                .isEqualTo(new RouteRedesignNotificationReceipt.NotMarked(
                        RouteRedesignNotificationReceipt.NotMarked.QUOTATION_NOT_FOUND));
    }

    @ならば("見積り {int} には {string} に再設計要になった印が付いている")
    public void 印が付いている(int quotationNo, String requiredAt) {
        assertThat(assignedRoute(quotationNo).redesignRequiredAt()).isEqualTo(at(requiredAt));
    }

    @もし("見積り {int} の集約の版を控える")
    public void 見積りの集約の版を控える(int quotationNo) {
        rememberedQuotationVersion = quotation(quotationNo).aggregateVersion();
    }

    /** 印を付ける操作が保存しなかった（印の時刻を上書きしていない。Bolt 37e2 の開発レビュー）。 */
    @ならば("見積り {int} の集約の版は控えた版から変わらない")
    public void 見積りの集約の版は変わらない(int quotationNo) {
        assertThat(quotation(quotationNo).aggregateVersion()).isEqualTo(rememberedQuotationVersion);
    }

    @ならば("見積り {int} には再設計要の印が付いていない")
    public void 印が付いていない(int quotationNo) {
        assertThat(assignedRoute(quotationNo).redesignRequired()).isFalse();
    }

    /** 予約が本予約の確定で呼ぶ照会（commit 時刻は今の時刻）。 */
    @ならば("見積り {int} の本予約の照会の結果は {string} である")
    public void 本予約の照会の結果(int quotationNo, String reason) {
        BookableQuotationResult result = bookableQuotationQuery.find(new BookableQuotationRequest(
                transportRequest().number().text(), quotationNo, new UtcInstant(clock.instant())));
        assertThat(result).isEqualTo(new BookableQuotationResult.NotBookable(reason));
    }

    private Quotation quotation(int quotationNo) {
        return quotations
                .findByTransportRequestIdAndNo(transportRequest().id(), quotationNo)
                .orElseThrow();
    }

    private AssignedRoute assignedRoute(int quotationNo) {
        return staffQueryService
                .find(transportRequest().number(), quotationNo, OPERATOR)
                .orElseThrow()
                .assignedRoute()
                .orElseThrow();
    }

    private TransportRequest transportRequest() {
        return transportRequests
                .findById(new TransportRequestId(context.transportRequestId()))
                .orElseThrow();
    }

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }
}

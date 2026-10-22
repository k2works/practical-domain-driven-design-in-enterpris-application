package com.example.cargotracker.quotation.interfaces.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteRedesignService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 見積りの公開 API の再設計要の伝達（ADR-014、Q-INV-10。Bolt 37e）。依頼を検査し、結果を公開 API の受領の型に変える。状態の組み合わせの
 * 表の行 17・18 と、付けた・付いていた。
 */
class RouteRedesignNotificationAdapterTest {

    private static final UtcInstant REDESIGN_AT = at("2026-10-07T07:00:00Z");
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());

    private final InMemoryQuotationRepository quotations = new InMemoryQuotationRepository();
    private final RouteRedesignNotificationAdapter adapter =
            new RouteRedesignNotificationAdapter(new RouteRedesignService(quotations));

    @Test
    void 経路版を割り当てた見積りに付けると付けた数を返しもう一度なら付いていたを返す() {
        awaitingApproval();
        RouteRedesignNotificationRequest request = new RouteRedesignNotificationRequest(caseNumber(), 1, REDESIGN_AT);

        assertThat(adapter.notifyRedesignRequired(request)).isEqualTo(new RouteRedesignNotificationReceipt.Marked(1));
        assertThat(adapter.notifyRedesignRequired(request))
                .isEqualTo(new RouteRedesignNotificationReceipt.AlreadyMarked());
    }

    @Test
    void その経路版を割り当てた見積りがなければ見つからないを返す() {
        // 行 17
        awaitingApproval();

        assertThat(adapter.notifyRedesignRequired(new RouteRedesignNotificationRequest(caseNumber(), 2, REDESIGN_AT)))
                .isEqualTo(new RouteRedesignNotificationReceipt.NotMarked(
                        RouteRedesignNotificationReceipt.NotMarked.QUOTATION_NOT_FOUND));
    }

    @ParameterizedTest(name = "案件番号「{0}」・経路版番号 {1}・時刻あり {2} は不正な依頼")
    @CsvSource({", 1, true", "'  ', 1, true", "RC-2026-0001, 0, true", "RC-2026-0001, 1, false"})
    void 不正な依頼は例外にせず理由で返す(String caseNumber, int routeVersionNo, boolean withTime) {
        // 行 18（再配信しても変わらないので listener を戻さない）
        RouteRedesignNotificationRequest request =
                new RouteRedesignNotificationRequest(caseNumber, routeVersionNo, withTime ? REDESIGN_AT : null);

        assertThat(adapter.notifyRedesignRequired(request))
                .isEqualTo(new RouteRedesignNotificationReceipt.NotMarked(
                        RouteRedesignNotificationReceipt.NotMarked.INVALID_REQUEST));
    }

    private void awaitingApproval() {
        Quotation quotation =
                Quotation.create(new QuotationId(UUID.randomUUID()), new TransportRequestId(UUID.randomUUID()), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        quotation.presentInternally(new UserId(UUID.randomUUID()), at("2026-10-05T04:30:00Z"));
        quotation.requestRouteDesign(SHIPPER_USER, at("2026-10-06T02:00:00Z"));
        quotation.assignRoute(QuotationFixture.assignedRoute(), at("2026-10-07T05:00:00Z"));
        quotation.clearDomainEvents();
        quotations.save(quotation);
    }

    private static String caseNumber() {
        return QuotationFixture.assignedRoute().routingCaseNumber();
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}

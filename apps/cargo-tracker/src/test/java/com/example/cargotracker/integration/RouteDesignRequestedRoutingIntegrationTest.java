package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.CalculationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.PresentationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteDesignRequestOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;

/**
 * DE-16 の経路設計への配信を PostgreSQL 18 で確かめる（R-INV-10、ADR-003。Bolt 17 の H2）。
 * 荷主の回答のコミット後に、イベント発行記録を経て非同期に購読され、経路設計が見積りの公開 API で経路条件を得て案件を作る。
 * 案件には輸送要求の荷主企業が写る（担当範囲の判定に使う。Bolt 38g の行 6c）。
 * このテストはコミットするため、待つ条件はこのテストで提出した輸送要求に限る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RouteDesignRequestedRoutingIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final CompanyId SHIPPER_COMPANY = new CompanyId(UUID.randomUUID());
    /** 営業担当者（提出する荷主企業を担当する。Bolt 38f）。 */
    private static final AuthenticatedActor OPERATOR = AuthenticatedActorFixture.salesHandling(STAFF, SHIPPER_COMPANY);

    private static final UserId SHIPPER = new UserId(UUID.randomUUID());
    /** 提出する荷主企業を担当する経路設計者（Bolt 38g）。 */
    private static final AuthenticatedActor DESIGNER =
            AuthenticatedActorFixture.routeDesignerHandling(new UserId(UUID.randomUUID()), SHIPPER_COMPANY);

    @Autowired
    TransportRequestCommandService transportRequestCommandService;

    @Autowired
    TransportRequestReviewService reviewService;

    @Autowired
    QuotationCommandService quotationCommandService;

    @Autowired
    QuotationResponseService quotationResponseService;

    @Autowired
    RoutingCaseQueryService routingCaseQueryService;

    @Autowired
    CompletedEventPublications completedEventPublications;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 詳細経路設計を依頼するとイベントが経路設計に配信され見積りの経路条件で案件が1つ作られる() {
        CompanyId shipper = SHIPPER_COMPANY;
        SubmissionOutcome.Submitted submitted = (SubmissionOutcome.Submitted) transportRequestCommandService.submit(
                new SubmitTransportRequestCommand(shipper, SHIPPER, ShipmentTermsFixture.completeInput()));
        TransportRequestNumber number = submitted.number();
        assertThat(reviewService.approve(new ApproveTransportRequestCommand(number, 1, STAFF, "根拠"), OPERATOR))
                .isInstanceOf(ReviewOutcome.Reviewed.class);
        assertThat(quotationCommandService.calculate(
                        new CalculateQuotationCommand(number, QuotationFixture.completeInput()), OPERATOR))
                .isEqualTo(new CalculationOutcome.Calculated(number, 1));
        assertThat(quotationCommandService.present(new PresentQuotationCommand(number, 1, STAFF), OPERATOR))
                .isEqualTo(new PresentationOutcome.Presented(number, 1));

        assertThat(quotationResponseService.requestRouteDesign(
                        new RequestRouteDesignCommand(number, 1, shipper, SHIPPER)))
                .isEqualTo(new RouteDesignRequestOutcome.Requested(number, 1));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(routingCaseQueryService.listCases(DESIGNER))
                        .filteredOn(summary -> summary.transportRequestNumber().equals(number.text()))
                        .singleElement()
                        .satisfies(summary -> {
                            assertThat(summary.transportRequestVersionNo()).isEqualTo(1);
                            assertThat(summary.origin().unLocode()).isEqualTo("JPTYO");
                            assertThat(summary.destination().unLocode()).isEqualTo("NLRTM");
                            assertThat(summary.arrivalDeadline()).isEqualTo(ShipmentTermsFixture.ARRIVAL_DEADLINE);
                            assertThat(summary.status()).isEqualTo(RouteVersionStatus.DRAFT);
                        }));
        // DE-16 の購読は見積り（輸送要求を経路設計中にする）と経路設計（案件を作る）の 2 つ。どちらの配信も完了する
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(completedEventPublications.findAll())
                        .filteredOn(publication -> publication.getEvent() instanceof RouteDesignRequested requested
                                && requested
                                        .transportRequestId()
                                        .equals(submitted.transportRequestId().value()))
                        .hasSize(2));
        assertThat(routingCaseQueryService.listCases(DESIGNER))
                .extracting(RoutingCaseSummary::transportRequestNumber)
                .containsOnlyOnce(number.text());
        assertThat(jdbc.queryForObject(
                        "SELECT shipper_company_id FROM routing.routing_case WHERE transport_request_number = ?",
                        UUID.class,
                        number.text()))
                .isEqualTo(shipper.value());
    }
}

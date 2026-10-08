package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.CalculationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.PresentationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.ReviewOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;

/**
 * DE-03 の配信を PostgreSQL 18 で確かめる（ADR-003、Bolt 10 の H1）。
 * 見積りの提示のコミット後に、イベント発行記録を経て非同期に購読され、別のトランザクションで輸送要求が見積提示済みになる。
 * アクセス・監査も同じイベントを購読し、KPI 計測記録に最初の提示時刻を記録する（Bolt 21）。
 * このテストはコミットするため、待つ条件はこのテストで提出した輸送要求に限る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuotationPresentedDeliveryIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final UserId STAFF = new UserId(UUID.randomUUID());

    @Autowired
    TransportRequestCommandService transportRequestCommandService;

    @Autowired
    TransportRequestReviewService reviewService;

    @Autowired
    QuotationCommandService quotationCommandService;

    @Autowired
    StaffTransportRequestQueryService staffQueryService;

    @Autowired
    KpiObservationQueryService kpiObservationQueryService;

    @Autowired
    CompletedEventPublications completedEventPublications;

    @Test
    void 見積りを提示するとイベントが非同期に配信され輸送要求が見積提示済みになり配信が完了する() {
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) transportRequestCommandService.submit(new SubmitTransportRequestCommand(
                        new CompanyId(UUID.randomUUID()), STAFF, ShipmentTermsFixture.completeInput()));
        TransportRequestNumber number = submitted.number();
        assertThat(reviewService.approve(new ApproveTransportRequestCommand(number, 1, STAFF, "根拠")))
                .isInstanceOf(ReviewOutcome.Reviewed.class);
        assertThat(quotationCommandService.calculate(
                        new CalculateQuotationCommand(number, QuotationFixture.completeInput())))
                .isEqualTo(new CalculationOutcome.Calculated(number, 1));

        assertThat(quotationCommandService.present(new PresentQuotationCommand(number, 1, STAFF)))
                .isEqualTo(new PresentationOutcome.Presented(number, 1));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(staffQueryService.findByNumber(number))
                        .hasValueSatisfying(
                                request -> assertThat(request.status()).isEqualTo(TransportRequestStatus.QUOTED)));
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(completedEventPublications.findAll())
                        .extracting(EventPublication::getEvent)
                        .filteredOn(QuotationPresented.class::isInstance)
                        .map(QuotationPresented.class::cast)
                        .extracting(QuotationPresented::transportRequestId)
                        .contains(submitted.transportRequestId().value()));
    }

    @Test
    void 見積りを提示するとKPI計測記録に最初の提示時刻が非同期に記録されリードタイムが求まる() {
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) transportRequestCommandService.submit(new SubmitTransportRequestCommand(
                        new CompanyId(UUID.randomUUID()), STAFF, ShipmentTermsFixture.completeInput()));
        TransportRequestNumber number = submitted.number();
        UUID transportRequestId = submitted.transportRequestId().value();
        reviewService.approve(new ApproveTransportRequestCommand(number, 1, STAFF, "根拠"));
        quotationCommandService.calculate(new CalculateQuotationCommand(number, QuotationFixture.completeInput()));
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(kpiObservationQueryService.findByTransportRequestId(transportRequestId))
                        .isPresent());

        quotationCommandService.present(new PresentQuotationCommand(number, 1, STAFF));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(kpiObservationQueryService.findByTransportRequestId(transportRequestId))
                        .hasValueSatisfying(observation -> {
                            assertThat(observation.firstPresentedAt()).isPresent();
                            assertThat(observation.leadTime())
                                    .hasValueSatisfying(
                                            leadTime -> assertThat(leadTime).isGreaterThanOrEqualTo(Duration.ZERO));
                        }));
    }
}

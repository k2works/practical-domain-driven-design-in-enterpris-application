package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
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
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    StaffQuotationQueryService staffQuotationQueryService;

    @Autowired
    KpiObservationRepository kpiObservationRepository;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    IncompleteEventPublications incompleteEventPublications;

    @Autowired
    EventPublicationRepository eventPublicationRepository;

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
                            UtcInstant presentedAt = presentedAtOf(number);
                            assertThat(observation.firstPresentedAt()).hasValue(presentedAt);
                            assertThat(observation.leadTime())
                                    .hasValue(Duration.between(
                                            observation.submittedAt().instant(), presentedAt.instant()));
                        }));
    }

    /** 見積り 1 の提示時刻（KPI 計測記録の最初の提示時刻と照らし合わせる）。 */
    private UtcInstant presentedAtOf(TransportRequestNumber number) {
        return staffQuotationQueryService
                .find(number, 1)
                .orElseThrow()
                .presentedAt()
                .orElseThrow();
    }

    @Test
    void 提出の記録がないまま届いたDE03は発行の記録が未完了のまま残り再配信で記録できる() {
        UUID transportRequestId = UUID.randomUUID();
        UtcInstant presentedAt = new UtcInstant(Instant.parse("2026-10-05T04:30:00Z"));
        transactionTemplate.executeWithoutResult(status -> events.publishEvent(new QuotationPresented(
                UUID.randomUUID(),
                1,
                transportRequestId,
                1,
                presentedAt,
                List.of(),
                presentedAt,
                presentedAt,
                presentedAt)));
        // 最初の配信は提出の記録がないため失敗し、発行の記録が失敗（未完了）のまま残る
        await().atMost(TIMEOUT)
                .untilAsserted(() ->
                        assertThat(failedKpiPublications(transportRequestId)).isNotEmpty());

        transactionTemplate.executeWithoutResult(
                status -> kpiObservationRepository.save(KpiObservation.recordSubmission(
                        transportRequestId,
                        "TR-2026-9999",
                        new CompanyId(UUID.randomUUID()),
                        new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")))));
        incompleteEventPublications.resubmitIncompletePublications(
                publication -> publication.getEvent() instanceof QuotationPresented presented
                        && presented.transportRequestId().equals(transportRequestId));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(kpiObservationQueryService.findByTransportRequestId(transportRequestId))
                        .hasValueSatisfying(observation ->
                                assertThat(observation.firstPresentedAt()).hasValue(presentedAt)));
    }

    /** KPI の listener への、この輸送要求の DE-03 の発行の記録のうち、失敗したもの。 */
    private List<TargetEventPublication> failedKpiPublications(UUID transportRequestId) {
        return eventPublicationRepository.findByStatus(EventPublication.Status.FAILED).stream()
                .filter(publication -> publication.getEvent() instanceof QuotationPresented presented
                        && presented.transportRequestId().equals(transportRequestId))
                .filter(publication ->
                        publication.getTargetIdentifier().getValue().contains("KpiObservationEventHandler"))
                .toList();
    }
}

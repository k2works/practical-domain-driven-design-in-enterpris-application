package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.application.internal.commands.ApproveQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.ShipperApprovalOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commands.ConfirmRouteCommand;
import com.example.cargotracker.routing.application.internal.commandservices.CandidateCalculationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RouteConfirmationOutcome;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;

/**
 * 経路の確定（DE-05）から見積りへの割当て（ADR-014）、輸送要求の荷主承認待ち（DE-21）、荷主の承認と予約待ち（DE-04）までを、
 * PostgreSQL 18 とイベント発行記録を経た非同期の配信で確かめる（US-24 AC4、R-INV-11。Bolt 20）。
 * このテストはコミットするため、航海は実行ごとに別の航海番号にし、待つ条件はこのテストで提出した輸送要求に限る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RouteConfirmedAssignmentIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final UserId ROUTE_DESIGNER = new UserId(UUID.randomUUID());

    @Autowired
    TransportRequestCommandService transportRequestCommandService;

    @Autowired
    TransportRequestReviewService reviewService;

    @Autowired
    QuotationCommandService quotationCommandService;

    @Autowired
    QuotationResponseService quotationResponseService;

    @Autowired
    StaffQuotationQueryService staffQuotationQueryService;

    @Autowired
    StaffTransportRequestQueryService staffTransportRequestQueryService;

    @Autowired
    RoutingCaseQueryService routingCaseQueryService;

    @Autowired
    RoutingCaseCommandService routingCaseCommandService;

    @Autowired
    RoutingCaseRepository routingCaseRepository;

    @Autowired
    CompletedEventPublications completedEventPublications;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 経路を確定すると見積りに割り当てられ荷主が承認すると輸送要求は予約待ちになる() {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        SubmissionOutcome.Submitted submitted = (SubmissionOutcome.Submitted) transportRequestCommandService.submit(
                new SubmitTransportRequestCommand(shipper, SHIPPER_USER, ShipmentTermsFixture.completeInput()));
        TransportRequestNumber number = submitted.number();
        reviewService.approve(new ApproveTransportRequestCommand(number, 1, STAFF, "根拠"));
        quotationCommandService.calculate(new CalculateQuotationCommand(number, QuotationFixture.completeInput()));
        quotationCommandService.present(new PresentQuotationCommand(number, 1, STAFF));
        quotationResponseService.requestRouteDesign(new RequestRouteDesignCommand(number, 1, shipper, SHIPPER_USER));
        RoutingCaseNumber caseNumber = await().atMost(TIMEOUT)
                .until(
                        () -> routingCaseQueryService.listCases().stream()
                                .filter(summary ->
                                        summary.transportRequestNumber().equals(number.text()))
                                .map(RoutingCaseSummary::number)
                                .findFirst(),
                        Optional::isPresent)
                .orElseThrow();
        String voyageNumber = directVoyage();

        assertThat(routingCaseCommandService.calculateCandidates(
                        new CalculateCandidatesCommand(caseNumber, aggregateVersion(caseNumber), ROUTE_DESIGNER)))
                .isInstanceOf(CandidateCalculationOutcome.Calculated.class);
        int candidateNo = candidateOf(caseNumber, voyageNumber);
        assertThat(routingCaseCommandService.confirm(new ConfirmRouteCommand(
                        caseNumber, candidateNo, "直行で期限まで 3 日ある。", aggregateVersion(caseNumber), ROUTE_DESIGNER, true)))
                .isInstanceOf(RouteConfirmationOutcome.Confirmed.class);

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(staffQuotationQueryService.find(number, 1))
                        .hasValueSatisfying(quotation -> {
                            assertThat(quotation.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
                            assertThat(quotation.assignedRoute()).hasValueSatisfying(route -> {
                                assertThat(route.routingCaseNumber()).isEqualTo(caseNumber.text());
                                assertThat(route.routeVersionNo()).isEqualTo(1);
                                assertThat(route.legs())
                                        .singleElement()
                                        .satisfies(leg ->
                                                assertThat(leg.voyageNumber()).isEqualTo(voyageNumber));
                            });
                        }));
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(staffTransportRequestQueryService.findByNumber(number))
                        .hasValueSatisfying(request ->
                                assertThat(request.status()).isEqualTo(TransportRequestStatus.AWAITING_APPROVAL)));

        assertThat(quotationResponseService.approve(new ApproveQuotationCommand(number, 1, shipper, SHIPPER_USER)))
                .isEqualTo(new ShipperApprovalOutcome.Approved(number, 1));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(staffTransportRequestQueryService.findByNumber(number))
                        .hasValueSatisfying(request ->
                                assertThat(request.status()).isEqualTo(TransportRequestStatus.READY_TO_BOOK)));
        // DE-05（経路設計の listener）・DE-21・DE-04 の配信は、どれも完了する
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(completedEventPublications.findAll())
                        .filteredOn(publication -> switch (publication.getEvent()) {
                            case RouteConfirmed confirmed ->
                                confirmed
                                        .transportRequestId()
                                        .equals(submitted.transportRequestId().value());
                            case QuotationRouteAssigned assigned ->
                                assigned.transportRequestId()
                                        .equals(submitted.transportRequestId().value());
                            case QuotationApprovedByShipper approved ->
                                approved.transportRequestId()
                                        .equals(submitted.transportRequestId().value());
                            default -> false;
                        })
                        .hasSize(3));
    }

    private long aggregateVersion(RoutingCaseNumber caseNumber) {
        return routingCaseRepository.findByNumber(caseNumber).orElseThrow().aggregateVersion();
    }

    private int candidateOf(RoutingCaseNumber caseNumber, String voyageNumber) {
        return routingCaseRepository.findByNumber(caseNumber).orElseThrow().routeVersion().candidates().stream()
                .filter(candidate -> candidate.legs().size() == 1
                        && candidate.legs().getFirst().voyageNumber().equals(voyageNumber))
                .findFirst()
                .orElseThrow()
                .candidateNo();
    }

    /** 東京からロッテルダムへの直行の航海（希望到着期限 2099-11-02 に間に合う）。実行ごとに別の航海番号にする。 */
    private String directVoyage() {
        String voyageNumber = "IT-B20-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO routing.voyage (voyage_number, adopted_info_version, source_kind, source_ref,"
                        + " acquired_at, version, updated_at) VALUES (?, ?, 'MANUAL_ENTRY', 'テスト',"
                        + " CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP)",
                voyageNumber,
                voyageNumber + "@1");
        jdbc.update(
                "INSERT INTO routing.port_call VALUES (?, 1, 'JPTYO', NULL, TIMESTAMP WITH TIME ZONE '2099-10-12 00:00:00+00')",
                voyageNumber);
        jdbc.update(
                "INSERT INTO routing.port_call VALUES (?, 2, 'NLRTM', TIMESTAMP WITH TIME ZONE '2099-10-30 00:00:00+00', NULL)",
                voyageNumber);
        return voyageNumber;
    }
}

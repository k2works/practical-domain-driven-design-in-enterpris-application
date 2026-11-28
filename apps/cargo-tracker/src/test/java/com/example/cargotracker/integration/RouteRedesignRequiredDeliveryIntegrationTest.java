package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.routing.domain.events.RouteRedesignRequired;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 経路設計が発行した DE-06（経路の再設計要）が、本番の組み立てでイベント発行記録を経た非同期の配信で listener に届き、見積りの公開 API を
 * 通して割り当てた見積りに印が付くことを PostgreSQL で確かめる（US-07 AC5、ADR-014。Bolt 37e2 の開発レビュー、T-180）。
 * このテストはコミットするため、業務番号は 2098 年を使い、待つ条件はこのテストで保存した見積りに限る。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RouteRedesignRequiredDeliveryIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String CASE = "RC-2098-0001";
    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final UtcInstant REQUIRED_AT = at("2098-01-08T06:00:00Z");

    @Autowired
    QuotationRepository quotations;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 経路設計が発行した再設計要が配信されると割り当てた見積りに印が付く() {
        QuotationId quotationId = transactionTemplate.execute(status -> awaitingApproval());

        transactionTemplate.executeWithoutResult(status -> events.publishEvent(new RouteRedesignRequired(
                UUID.randomUUID(),
                CASE,
                1,
                quotationId.value(),
                UUID.randomUUID(),
                1,
                REQUIRED_AT,
                List.of("DEADLINE_EXCEEDED"),
                List.of("V-301@2"))));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(quotations
                                .findById(quotationId)
                                .orElseThrow()
                                .assignedRoute()
                                .orElseThrow()
                                .redesignRequiredAt())
                        .isEqualTo(REQUIRED_AT));
    }

    /** 輸送要求を作り、案件 RC-2098-0001 の経路版 1 を割り当てた荷主承認待ちの見積りを保存する。 */
    private QuotationId awaitingApproval() {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2098, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                at("2098-01-05T01:00:00Z"));
        transportRequests.save(request);
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2098-01-05T04:00:00Z"));
        quotation.presentInternally(STAFF, at("2098-01-05T04:30:00Z"));
        quotation.requestRouteDesign(SHIPPER_USER, at("2098-01-06T02:00:00Z"));
        quotation.assignRoute(
                new AssignedRoute(
                        CASE,
                        1,
                        at("2098-01-07T05:00:00Z"),
                        List.of(new AssignedRouteLeg(
                                "V-301",
                                new Location("SGSIN"),
                                new Location("NLRTM"),
                                at("2099-10-20T12:00:00Z"),
                                at("2099-10-31T00:00:00Z")))),
                at("2098-01-07T05:00:00Z"));
        quotation.clearDomainEvents();
        quotations.save(quotation);
        return quotation.id();
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}

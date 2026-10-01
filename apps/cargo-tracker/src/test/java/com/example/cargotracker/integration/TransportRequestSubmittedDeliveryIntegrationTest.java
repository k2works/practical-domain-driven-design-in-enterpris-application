package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
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
 * DE-01 の配信を PostgreSQL 18 で確かめる（ADR-003）。
 * 提出のコミット後に、イベント発行記録を経て非同期に購読され、KPI 計測記録ができて配信が完了する。
 * このテストはコミットするため、待つ条件はこのテストで提出した輸送要求の ID に限る（他のテストの行で満たされないようにする）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransportRequestSubmittedDeliveryIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    TransportRequestCommandService commandService;

    @Autowired
    KpiObservationQueryService kpiObservationQueryService;

    @Autowired
    CompletedEventPublications completedEventPublications;

    @Test
    void 輸送要求を提出するとイベントが非同期に配信されKPI計測記録ができて配信が完了する() {
        TransportRequestId id = commandService.submit(new SubmitTransportRequestCommand(
                new CompanyId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                new Location("JPTYO"),
                new Location("NLRTM")));

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(kpiObservationQueryService.findByTransportRequestId(id.value()))
                        .isPresent());
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(completedEventPublications.findAll())
                        .extracting(EventPublication::getEvent)
                        .filteredOn(TransportRequestSubmitted.class::isInstance)
                        .map(TransportRequestSubmitted.class::cast)
                        .extracting(TransportRequestSubmitted::transportRequestId)
                        .contains(id.value()));
    }
}

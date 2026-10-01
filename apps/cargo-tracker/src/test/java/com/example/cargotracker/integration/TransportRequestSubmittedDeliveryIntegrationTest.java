package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
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
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

/**
 * DE-01 の配信を PostgreSQL 18 で確かめる（ADR-003）。
 * 提出のコミット後に、イベント発行記録を経て非同期に購読され、KPI 計測記録ができて配信が完了する。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnableScenarios
class TransportRequestSubmittedDeliveryIntegrationTest {

    @Autowired
    TransportRequestCommandService commandService;

    @Autowired
    KpiObservationQueryService kpiObservationQueryService;

    @Autowired
    CompletedEventPublications completedEventPublications;

    @Test
    void 輸送要求を提出するとイベントが非同期に配信されKPI計測記録ができる(Scenario scenario) {
        SubmitTransportRequestCommand command = new SubmitTransportRequestCommand(new CompanyId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()), new Location("JPTYO"), new Location("NLRTM"));

        scenario.stimulate(() -> commandService.submit(command))
                .andWaitAtMost(Duration.ofSeconds(10))
                .andWaitForStateChange(kpiObservationQueryService::findAll, observations -> !observations.isEmpty())
                .andVerify((observations, id) -> {
                    assertThat(observations).extracting(KpiObservation::transportRequestId).contains(id.value());
                    assertThat(completedEventPublications.findAll())
                            .extracting(publication -> publication.getEvent())
                            .filteredOn(TransportRequestSubmitted.class::isInstance)
                            .map(TransportRequestSubmitted.class::cast)
                            .extracting(TransportRequestSubmitted::transportRequestId)
                            .contains(id.value());
                });
    }
}

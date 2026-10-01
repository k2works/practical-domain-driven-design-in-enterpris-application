package com.example.cargotracker.acceptance;

import com.example.cargotracker.identity.acceptance.InMemoryKpiObservationRepository;
import com.example.cargotracker.identity.application.internal.eventhandlers.KpiObservationEventHandler;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/**
 * 業務ルール層の受入シナリオの組み立て（テスト戦略）。
 * DB とアプリケーション全体を起動せず、入力ポートの実装・メモリ上のリポジトリ・固定の Clock・
 * テスト用の同期のイベント配信で組み立てる。イベント配信そのものは統合テストで確かめる（ADR-003）。
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = AcceptanceTestConfiguration.Components.class)
public class AcceptanceTestConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Import(ScenarioContext.class)
    static class Components {

        @Bean
        MutableClock clock() {
            return new MutableClock();
        }

        @Bean
        InMemoryTransportRequestRepository transportRequestRepository() {
            return new InMemoryTransportRequestRepository();
        }

        @Bean
        InMemoryKpiObservationRepository kpiObservationRepository() {
            return new InMemoryKpiObservationRepository();
        }

        @Bean
        KpiObservationEventHandler kpiObservationEventHandler(InMemoryKpiObservationRepository repository) {
            return new KpiObservationEventHandler(repository);
        }

        @Bean
        KpiObservationQueryService kpiObservationQueryService(InMemoryKpiObservationRepository repository) {
            return new KpiObservationQueryService(repository);
        }

        @Bean
        TransportRequestCommandService transportRequestCommandService(InMemoryTransportRequestRepository repository,
                KpiObservationEventHandler kpiObservationEventHandler, MutableClock clock) {
            return new TransportRequestCommandService(repository,
                    synchronousDelivery(kpiObservationEventHandler), clock);
        }

        @Bean
        TransportRequestQueryService transportRequestQueryService(InMemoryTransportRequestRepository repository) {
            return new TransportRequestQueryService(repository);
        }

        /** テスト用の同期の配信。発行されたイベントをその場で購読側に渡す。 */
        private static ApplicationEventPublisher synchronousDelivery(KpiObservationEventHandler handler) {
            return event -> {
                if (event instanceof TransportRequestSubmitted submitted) {
                    handler.on(submitted);
                }
            };
        }
    }
}

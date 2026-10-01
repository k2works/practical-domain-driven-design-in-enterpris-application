package com.example.cargotracker.identity.infrastructure.config;

import com.example.cargotracker.identity.application.internal.eventhandlers.KpiObservationEventHandler;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.identity.domain.model.aggregates.KpiObservationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * アクセス・監査コンテキストの組み立て。アプリケーションサービスに送信アダプターをつなぐ。
 */
@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    @Bean
    KpiObservationEventHandler kpiObservationEventHandler(KpiObservationRepository repository) {
        return new KpiObservationEventHandler(repository);
    }

    @Bean
    KpiObservationQueryService kpiObservationQueryService(KpiObservationRepository repository) {
        return new KpiObservationQueryService(repository);
    }
}

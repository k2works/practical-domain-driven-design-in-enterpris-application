package com.example.cargotracker.externaldata.infrastructure.config;

import com.example.cargotracker.externaldata.application.internal.queryservices.PilotReadinessQueryService;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 外部データコンテキストの組み立て（Bolt 39b）。アプリケーションサービスは部品探索の外なので、ここで {@code @Bean} として組む
 * （CargoTrackerApplication、2026-10-03 の人の決定）。
 */
@Configuration(proxyBeanMethods = false)
public class ExternalDataConfiguration {

    @Bean
    PilotReadinessQueryService pilotReadinessQueryService(DataSourceRepository dataSources) {
        return new PilotReadinessQueryService(dataSources);
    }
}

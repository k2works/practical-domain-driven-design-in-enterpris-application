package com.example.cargotracker.operations.infrastructure.config;

import com.example.cargotracker.operations.application.internal.queryservices.EventBacklogQueryService;
import com.example.cargotracker.operations.domain.model.aggregates.IncompletePublications;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 運用の部品の組み立て。アプリケーションサービスは部品探索の外なので、ここで {@code @Bean} として組む。 */
@Configuration(proxyBeanMethods = false)
public class OperationsConfiguration {

    @Bean
    EventBacklogQueryService eventBacklogQueryService(IncompletePublications publications, Clock clock) {
        return new EventBacklogQueryService(publications, clock);
    }
}

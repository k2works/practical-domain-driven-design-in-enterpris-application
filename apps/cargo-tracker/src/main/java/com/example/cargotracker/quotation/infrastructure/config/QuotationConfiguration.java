package com.example.cargotracker.quotation.infrastructure.config;

import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 見積りコンテキストの組み立て。アプリケーションサービスに送信アダプターをつなぐ。
 */
@Configuration(proxyBeanMethods = false)
public class QuotationConfiguration {

    @Bean
    TransportRequestCommandService transportRequestCommandService(TransportRequestRepository repository,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        return new TransportRequestCommandService(repository, eventPublisher, clock);
    }

    @Bean
    TransportRequestQueryService transportRequestQueryService(TransportRequestRepository repository) {
        return new TransportRequestQueryService(repository);
    }
}

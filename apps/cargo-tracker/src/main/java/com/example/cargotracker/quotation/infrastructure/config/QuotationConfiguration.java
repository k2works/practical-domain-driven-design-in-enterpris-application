package com.example.cargotracker.quotation.infrastructure.config;

import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.infrastructure.persistence.MyBatisTransportRequestNumberIssuer;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 見積りコンテキストの組み立て。アプリケーションサービスに送信アダプターをつなぐ。
 */
@Configuration(proxyBeanMethods = false)
public class QuotationConfiguration {

    @Bean
    TransportRequestCommandService transportRequestCommandService(
            TransportRequestRepository repository,
            TransportRequestNumberIssuer numberIssuer,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new TransportRequestCommandService(
                repository, numberIssuer, new MvpAcceptancePolicy(), eventPublisher, clock);
    }

    /**
     * 起動時に、今年と来年（日本時間）の業務番号の採番の行を用意する（D-13）。
     * 年の最初の提出が同時に来ても、提出の中で 2 本目の接続を取らずに済むようにする。
     */
    @Bean
    ApplicationRunner transportRequestNumberYearPreparation(MyBatisTransportRequestNumberIssuer issuer, Clock clock) {
        return args -> {
            int year = TransportRequestNumber.yearOf(new UtcInstant(clock.instant()));
            issuer.prepareYear(year);
            issuer.prepareYear(year + 1);
        };
    }

    @Bean
    TransportRequestReviewService transportRequestReviewService(
            TransportRequestRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        return new TransportRequestReviewService(repository, eventPublisher, clock);
    }

    @Bean
    TransportRequestQueryService transportRequestQueryService(TransportRequestRepository repository) {
        return new TransportRequestQueryService(repository);
    }
}

package com.example.cargotracker.booking.infrastructure.config;

import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.TrackingNumberIssuer;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.infrastructure.persistence.RandomTrackingNumberIssuer;
import com.example.cargotracker.quotation.api.BookableQuotationQuery;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 予約コンテキストの組み立て。アプリケーションサービスに送信アダプターと、見積りの公開 API をつなぐ（Bolt 23）。
 */
@Configuration(proxyBeanMethods = false)
public class BookingConfiguration {

    /** 見積りの公開 API（予約確定に使える見積りの照会）を予約の予約条件に変える腐敗防止層（ADR-016）。 */
    @Bean
    QuotationBookability quotationBookability(BookableQuotationQuery bookableQuotationQuery) {
        return new QuotationBookability(bookableQuotationQuery);
    }

    /** 追跡番号の発行。暗号論的に安全な乱数を使う（BR-07）。 */
    @Bean
    TrackingNumberIssuer trackingNumberIssuer(BookingRepository repository) {
        return new RandomTrackingNumberIssuer(repository, new SecureRandom());
    }

    @Bean
    BookingCommandService bookingCommandService(
            BookingRepository repository,
            BookingSagaRepository sagaRepository,
            TrackingNumberIssuer trackingNumberIssuer,
            QuotationBookability quotationBookability,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new BookingCommandService(
                repository, sagaRepository, trackingNumberIssuer, quotationBookability, eventPublisher, clock);
    }
}

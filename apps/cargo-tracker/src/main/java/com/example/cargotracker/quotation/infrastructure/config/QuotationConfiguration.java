package com.example.cargotracker.quotation.infrastructure.config;

import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.RequiredDocumentStorage;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.infrastructure.persistence.MyBatisTransportRequestNumberIssuer;
import com.example.cargotracker.quotation.infrastructure.storage.DocumentStorageProperties;
import com.example.cargotracker.quotation.infrastructure.storage.LocalFileSystemRequiredDocumentStorage;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 見積りコンテキストの組み立て。アプリケーションサービスに送信アダプターをつなぐ。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocumentStorageProperties.class)
public class QuotationConfiguration {

    @Bean
    TransportRequestCommandService transportRequestCommandService(
            TransportRequestRepository repository,
            TransportRequestNumberIssuer numberIssuer,
            RequiredDocumentStorage documentStorage,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new TransportRequestCommandService(
                repository,
                numberIssuer,
                new MvpAcceptancePolicy(),
                new RequiredDocumentPolicy(),
                documentStorage,
                eventPublisher,
                clock);
    }

    /** 書類の保存。開発環境はローカルのファイルシステム（ADR-007）。S3 の実装は運用準備（W10、#28）で差し替える。 */
    @Bean
    RequiredDocumentStorage requiredDocumentStorage(DocumentStorageProperties properties) {
        return new LocalFileSystemRequiredDocumentStorage(properties.baseDir());
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
    StaffTransportRequestQueryService staffTransportRequestQueryService(
            TransportRequestRepository repository, RequiredDocumentStorage documentStorage) {
        return new StaffTransportRequestQueryService(repository, documentStorage);
    }

    @Bean
    TransportRequestQueryService transportRequestQueryService(
            TransportRequestRepository repository, RequiredDocumentStorage documentStorage) {
        return new TransportRequestQueryService(repository, documentStorage);
    }
}

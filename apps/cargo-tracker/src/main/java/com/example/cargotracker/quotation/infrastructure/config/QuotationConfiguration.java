package com.example.cargotracker.quotation.infrastructure.config;

import com.example.cargotracker.quotation.api.RouteAssignment;
import com.example.cargotracker.quotation.api.RouteConditionQuery;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteAssignmentService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.eventhandlers.QuotationApprovedByShipperEventHandler;
import com.example.cargotracker.quotation.application.internal.eventhandlers.QuotationPresentedEventHandler;
import com.example.cargotracker.quotation.application.internal.eventhandlers.QuotationRouteAssignedEventHandler;
import com.example.cargotracker.quotation.application.internal.eventhandlers.RouteDesignRequestedEventHandler;
import com.example.cargotracker.quotation.application.internal.queryservices.BookableQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.RouteConditionQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
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
    QuotationCommandService quotationCommandService(
            TransportRequestRepository transportRequestRepository,
            QuotationRepository quotationRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new QuotationCommandService(transportRequestRepository, quotationRepository, eventPublisher, clock);
    }

    @Bean
    QuotationResponseService quotationResponseService(
            TransportRequestRepository transportRequestRepository,
            QuotationRepository quotationRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new QuotationResponseService(transportRequestRepository, quotationRepository, eventPublisher, clock);
    }

    /** DE-16 を受けて輸送要求を経路設計中にする（Spring Modulith のイベントの記録を経て、別のトランザクションで動く。Bolt 12）。 */
    @Bean
    RouteDesignRequestedEventHandler routeDesignRequestedEventHandler(TransportRequestRepository repository) {
        return new RouteDesignRequestedEventHandler(repository);
    }

    /** DE-03 を受けて輸送要求を見積提示済みにする（Spring Modulith のイベントの記録を経て、別のトランザクションで動く）。 */
    @Bean
    QuotationPresentedEventHandler quotationPresentedEventHandler(TransportRequestRepository repository) {
        return new QuotationPresentedEventHandler(repository);
    }

    /** 見積りの公開 API の経路条件の照会（経路設計が DE-16 を受けて使う。Bolt 17）。 */
    @Bean
    RouteConditionQuery routeConditionQuery(TransportRequestRepository repository) {
        return new RouteConditionQueryService(repository);
    }

    /** 見積りの公開 API の予約確定に使える見積りの照会（予約が本予約の確定で使う。ADR-016。Bolt 23）。 */
    @Bean
    BookableQuotationQueryService bookableQuotationQuery(
            QuotationRepository quotationRepository, TransportRequestRepository transportRequestRepository) {
        return new BookableQuotationQueryService(quotationRepository, transportRequestRepository);
    }

    /** 見積りの公開 API の経路の割当て（経路設計が DE-05 を受けて使う。ADR-014。Bolt 20）。 */
    @Bean
    RouteAssignment routeAssignment(
            QuotationRepository quotationRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        return new RouteAssignmentService(quotationRepository, eventPublisher, clock);
    }

    /** DE-21 を受けて輸送要求を荷主承認待ちにする（別のトランザクション。Bolt 20）。 */
    @Bean
    QuotationRouteAssignedEventHandler quotationRouteAssignedEventHandler(TransportRequestRepository repository) {
        return new QuotationRouteAssignedEventHandler(repository);
    }

    /** DE-04 を受けて輸送要求を予約待ちにする（別のトランザクション。Bolt 20）。 */
    @Bean
    QuotationApprovedByShipperEventHandler quotationApprovedByShipperEventHandler(
            TransportRequestRepository repository) {
        return new QuotationApprovedByShipperEventHandler(repository);
    }

    @Bean
    QuotationQueryService quotationQueryService(
            TransportRequestRepository transportRequestRepository, QuotationRepository quotationRepository) {
        return new QuotationQueryService(transportRequestRepository, quotationRepository);
    }

    @Bean
    StaffQuotationQueryService staffQuotationQueryService(
            TransportRequestRepository transportRequestRepository, QuotationRepository quotationRepository) {
        return new StaffQuotationQueryService(transportRequestRepository, quotationRepository);
    }

    @Bean
    TransportRequestQueryService transportRequestQueryService(
            TransportRequestRepository repository, RequiredDocumentStorage documentStorage) {
        return new TransportRequestQueryService(repository, documentStorage);
    }
}

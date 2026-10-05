package com.example.cargotracker.acceptance;

import com.example.cargotracker.identity.acceptance.InMemoryKpiObservationRepository;
import com.example.cargotracker.identity.application.internal.eventhandlers.KpiObservationEventHandler;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryRequiredDocumentStorage;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestNumberIssuer;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.acceptance.RequiredDocumentAttachments;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.application.internal.eventhandlers.QuotationPresentedEventHandler;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/**
 * 業務ルール層の受入シナリオの組み立て（テスト戦略）。
 * DB とアプリケーション全体を起動せず、入力ポートの実装・メモリ上のリポジトリ・固定の Clock・
 * テスト用の同期のイベント配信で組み立てる。イベント配信そのものは統合テストで確かめる（ADR-003）。
 * 時刻・リポジトリ・ためたイベントはシナリオごとに初期化する（{@link ScenarioReset}）。
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = AcceptanceTestConfiguration.Components.class)
public class AcceptanceTestConfiguration {

    /** {@code @SpringBootTest} の部品探索に拾われないよう {@code @TestConfiguration} にする。 */
    @TestConfiguration(proxyBeanMethods = false)
    @Import({ScenarioContext.class, RequiredDocumentAttachments.class})
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
        InMemoryTransportRequestNumberIssuer transportRequestNumberIssuer() {
            return new InMemoryTransportRequestNumberIssuer();
        }

        @Bean
        InMemoryRequiredDocumentStorage requiredDocumentStorage() {
            return new InMemoryRequiredDocumentStorage();
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
        InMemoryQuotationRepository quotationRepository() {
            return new InMemoryQuotationRepository();
        }

        @Bean
        QuotationPresentedEventHandler quotationPresentedEventHandler(InMemoryTransportRequestRepository repository) {
            return new QuotationPresentedEventHandler(repository);
        }

        /** 購読側を登録した、テスト用の同期の配信。 */
        @Bean
        DeferredEventDelivery eventDelivery(
                KpiObservationEventHandler kpiObservationEventHandler,
                QuotationPresentedEventHandler quotationPresentedEventHandler) {
            DeferredEventDelivery delivery = new DeferredEventDelivery();
            delivery.subscribe(event -> {
                if (event instanceof TransportRequestSubmitted submitted) {
                    kpiObservationEventHandler.on(submitted);
                }
                if (event instanceof QuotationPresented presented) {
                    quotationPresentedEventHandler.on(presented);
                }
            });
            return delivery;
        }

        @Bean
        QuotationCommandService quotationCommandService(
                InMemoryTransportRequestRepository transportRequestRepository,
                InMemoryQuotationRepository quotationRepository,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new QuotationCommandService(transportRequestRepository, quotationRepository, eventDelivery, clock);
        }

        @Bean
        QuotationQueryService quotationQueryService(
                InMemoryTransportRequestRepository transportRequestRepository,
                InMemoryQuotationRepository quotationRepository) {
            return new QuotationQueryService(transportRequestRepository, quotationRepository);
        }

        @Bean
        StaffQuotationQueryService staffQuotationQueryService(
                InMemoryTransportRequestRepository transportRequestRepository,
                InMemoryQuotationRepository quotationRepository) {
            return new StaffQuotationQueryService(transportRequestRepository, quotationRepository);
        }

        @Bean
        KpiObservationQueryService kpiObservationQueryService(InMemoryKpiObservationRepository repository) {
            return new KpiObservationQueryService(repository);
        }

        @Bean
        TransportRequestCommandService transportRequestCommandService(
                InMemoryTransportRequestRepository repository,
                InMemoryTransportRequestNumberIssuer numberIssuer,
                InMemoryRequiredDocumentStorage documentStorage,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new TransportRequestCommandService(
                    repository,
                    numberIssuer,
                    new MvpAcceptancePolicy(),
                    new RequiredDocumentPolicy(),
                    documentStorage,
                    eventDelivery,
                    clock);
        }

        @Bean
        TransportRequestReviewService transportRequestReviewService(
                InMemoryTransportRequestRepository repository,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new TransportRequestReviewService(repository, eventDelivery, clock);
        }

        @Bean
        TransportRequestQueryService transportRequestQueryService(
                InMemoryTransportRequestRepository repository, InMemoryRequiredDocumentStorage documentStorage) {
            return new TransportRequestQueryService(repository, documentStorage);
        }

        @Bean
        StaffTransportRequestQueryService staffTransportRequestQueryService(
                InMemoryTransportRequestRepository repository, InMemoryRequiredDocumentStorage documentStorage) {
            return new StaffTransportRequestQueryService(repository, documentStorage);
        }
    }
}

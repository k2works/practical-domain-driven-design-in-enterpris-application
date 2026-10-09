package com.example.cargotracker.acceptance;

import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingSagaCommandService;
import com.example.cargotracker.booking.application.internal.eventhandlers.BookingConfirmedEventHandler;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookingNotifications;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.booking.infrastructure.persistence.RandomTrackingNumberIssuer;
import com.example.cargotracker.booking.interfaces.api.internal.TrackingStartNotificationAdapter;
import com.example.cargotracker.identity.acceptance.InMemoryKpiObservationRepository;
import com.example.cargotracker.identity.application.internal.eventhandlers.KpiObservationEventHandler;
import com.example.cargotracker.identity.application.internal.queryservices.KpiObservationQueryService;
import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryRequiredDocumentStorage;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestNumberIssuer;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.acceptance.RequiredDocumentAttachments;
import com.example.cargotracker.quotation.application.internal.commandservices.BookingNotificationService;
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
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.events.TransportRequestSubmitted;
import com.example.cargotracker.quotation.domain.model.rules.MvpAcceptancePolicy;
import com.example.cargotracker.quotation.domain.model.rules.RequiredDocumentPolicy;
import com.example.cargotracker.quotation.interfaces.api.internal.BookableQuotationQueryAdapter;
import com.example.cargotracker.quotation.interfaces.api.internal.BookingNotificationAdapter;
import com.example.cargotracker.quotation.interfaces.api.internal.RouteAssignmentAdapter;
import com.example.cargotracker.quotation.interfaces.api.internal.RouteConditionQueryAdapter;
import com.example.cargotracker.routing.acceptance.InMemoryConnectionRuleRepository;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseNumberIssuer;
import com.example.cargotracker.routing.acceptance.InMemoryRoutingCaseRepository;
import com.example.cargotracker.routing.acceptance.InMemoryVoyageRepository;
import com.example.cargotracker.routing.application.internal.commandservices.RoutingCaseCommandService;
import com.example.cargotracker.routing.application.internal.eventhandlers.QuotationRouteAssignmentEventHandler;
import com.example.cargotracker.routing.application.internal.eventhandlers.RoutingCaseOpeningEventHandler;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteAssignments;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.QuotationRouteConditions;
import com.example.cargotracker.routing.application.internal.queryservices.RoutingCaseQueryService;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.interfaces.api.internal.RouteVersionLegQueryAdapter;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.application.internal.eventhandlers.BookingTrackingStartNotificationEventHandler;
import com.example.cargotracker.tracking.application.internal.eventhandlers.TrackingStartEventHandler;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTrackingStarts;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.RoutingScheduledLegs;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import io.cucumber.spring.CucumberContextConfiguration;
import java.util.Random;
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

        @Bean
        RouteDesignRequestedEventHandler routeDesignRequestedEventHandler(
                InMemoryTransportRequestRepository repository) {
            return new RouteDesignRequestedEventHandler(repository);
        }

        @Bean
        InMemoryRoutingCaseRepository routingCaseRepository() {
            return new InMemoryRoutingCaseRepository();
        }

        @Bean
        InMemoryRoutingCaseNumberIssuer routingCaseNumberIssuer() {
            return new InMemoryRoutingCaseNumberIssuer();
        }

        @Bean
        InMemoryVoyageRepository voyageRepository() {
            return new InMemoryVoyageRepository();
        }

        @Bean
        InMemoryConnectionRuleRepository connectionRuleRepository() {
            return new InMemoryConnectionRuleRepository();
        }

        /** 経路設計は見積りの公開 API（経路条件の照会）越しに経路条件を得る（Bolt 17）。 */
        @Bean
        RoutingCaseOpeningEventHandler routingCaseOpeningEventHandler(
                InMemoryRoutingCaseRepository repository,
                InMemoryRoutingCaseNumberIssuer numberIssuer,
                InMemoryTransportRequestRepository transportRequestRepository) {
            return new RoutingCaseOpeningEventHandler(
                    repository,
                    numberIssuer,
                    new QuotationRouteConditions(new RouteConditionQueryAdapter(
                            new RouteConditionQueryService(transportRequestRepository))));
        }

        @Bean
        RoutingCaseCommandService routingCaseCommandService(
                InMemoryRoutingCaseRepository repository,
                InMemoryVoyageRepository voyageRepository,
                InMemoryConnectionRuleRepository connectionRuleRepository,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new RoutingCaseCommandService(
                    repository, voyageRepository, connectionRuleRepository, eventDelivery, clock);
        }

        @Bean
        RoutingCaseQueryService routingCaseQueryService(InMemoryRoutingCaseRepository repository) {
            return new RoutingCaseQueryService(repository);
        }

        @Bean
        InMemoryBookingRepository bookingRepository() {
            return new InMemoryBookingRepository();
        }

        @Bean
        InMemoryBookingSagaRepository bookingSagaRepository() {
            return new InMemoryBookingSagaRepository();
        }

        /** 予約は見積りの公開 API（予約確定に使える見積りの照会）越しに予約条件を得る（ADR-016。Bolt 23）。 */
        @Bean
        BookingCommandService bookingCommandService(
                InMemoryBookingRepository bookingRepository,
                InMemoryBookingSagaRepository bookingSagaRepository,
                InMemoryQuotationRepository quotationRepository,
                InMemoryTransportRequestRepository transportRequestRepository,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new BookingCommandService(
                    bookingRepository,
                    bookingSagaRepository,
                    new RandomTrackingNumberIssuer(bookingRepository, new Random(23)),
                    new QuotationBookability(new BookableQuotationQueryAdapter(
                            new BookableQuotationQueryService(quotationRepository, transportRequestRepository))),
                    eventDelivery,
                    clock);
        }

        /** 予約は DE-07 を受けて見積りの公開 API（予約確定済みの通知）を呼ぶ（ADR-014。Bolt 23）。 */
        @Bean
        BookingConfirmedEventHandler bookingConfirmedEventHandler(InMemoryTransportRequestRepository repository) {
            return new BookingConfirmedEventHandler(new QuotationBookingNotifications(
                    new BookingNotificationAdapter(new BookingNotificationService(repository))));
        }

        @Bean
        InMemoryTrackingRecordRepository trackingRecordRepository() {
            return new InMemoryTrackingRecordRepository();
        }

        /** 追跡は DE-07 を受け、経路設計の公開 API から確定した経路版の区間を引いて追跡を開始する（ADR-015。Bolt 25）。 */
        @Bean
        TrackingStartEventHandler trackingStartEventHandler(
                InMemoryTrackingRecordRepository repository,
                RoutingCaseQueryService routingCaseQueryService,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new TrackingStartEventHandler(
                    repository,
                    new RoutingScheduledLegs(new RouteVersionLegQueryAdapter(routingCaseQueryService)),
                    eventDelivery,
                    clock);
        }

        /** 追跡は DE-22 を受けて予約の公開 API で予約サガを完了にする（ADR-014・015。Bolt 25）。 */
        @Bean
        BookingTrackingStartNotificationEventHandler bookingTrackingStartNotificationEventHandler(
                InMemoryBookingSagaRepository bookingSagaRepository) {
            return new BookingTrackingStartNotificationEventHandler(new BookingTrackingStarts(
                    new TrackingStartNotificationAdapter(new BookingSagaCommandService(bookingSagaRepository))));
        }

        /** テスト用の同期の配信。購読側は {@link EventSubscriptions} が登録する（発行する部品と購読する部品が互いに依存するため）。 */
        @Bean
        DeferredEventDelivery eventDelivery() {
            return new DeferredEventDelivery();
        }

        /** 経路の割当て（見積りの公開 API。ADR-014。Bolt 20）。 */
        @Bean
        RouteAssignmentService routeAssignment(
                InMemoryQuotationRepository quotationRepository,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new RouteAssignmentService(quotationRepository, eventDelivery, clock);
        }

        /** 経路設計は DE-05 を受けて見積りの公開 API（経路の割当て）を呼ぶ（Bolt 20）。 */
        @Bean
        QuotationRouteAssignmentEventHandler quotationRouteAssignmentEventHandler(
                InMemoryRoutingCaseRepository repository, RouteAssignmentService routeAssignment) {
            return new QuotationRouteAssignmentEventHandler(
                    repository, new QuotationRouteAssignments(new RouteAssignmentAdapter(routeAssignment)));
        }

        @Bean
        QuotationRouteAssignedEventHandler quotationRouteAssignedEventHandler(
                InMemoryTransportRequestRepository repository) {
            return new QuotationRouteAssignedEventHandler(repository);
        }

        @Bean
        QuotationApprovedByShipperEventHandler quotationApprovedByShipperEventHandler(
                InMemoryTransportRequestRepository repository) {
            return new QuotationApprovedByShipperEventHandler(repository);
        }

        /** 購読側を配信に登録する。 */
        @Bean
        EventSubscriptions eventSubscriptions(
                DeferredEventDelivery delivery,
                KpiObservationEventHandler kpiObservationEventHandler,
                QuotationPresentedEventHandler quotationPresentedEventHandler,
                RouteDesignRequestedEventHandler routeDesignRequestedEventHandler,
                RoutingCaseOpeningEventHandler routingCaseOpeningEventHandler,
                QuotationRouteAssignmentEventHandler quotationRouteAssignmentEventHandler,
                QuotationRouteAssignedEventHandler quotationRouteAssignedEventHandler,
                QuotationApprovedByShipperEventHandler quotationApprovedByShipperEventHandler,
                BookingConfirmedEventHandler bookingConfirmedEventHandler,
                TrackingStartEventHandler trackingStartEventHandler,
                BookingTrackingStartNotificationEventHandler bookingTrackingStartNotificationEventHandler) {
            delivery.subscribe(event -> {
                switch (event) {
                    case TransportRequestSubmitted submitted -> kpiObservationEventHandler.on(submitted);
                    case QuotationPresented presented -> {
                        quotationPresentedEventHandler.on(presented);
                        kpiObservationEventHandler.on(presented);
                    }
                    case RouteDesignRequested requested -> {
                        routeDesignRequestedEventHandler.on(requested);
                        routingCaseOpeningEventHandler.on(requested);
                    }
                    case RouteConfirmed confirmed -> quotationRouteAssignmentEventHandler.on(confirmed);
                    case QuotationRouteAssigned assigned -> quotationRouteAssignedEventHandler.on(assigned);
                    case QuotationApprovedByShipper approved -> quotationApprovedByShipperEventHandler.on(approved);
                    case BookingConfirmed confirmed -> {
                        bookingConfirmedEventHandler.on(confirmed);
                        trackingStartEventHandler.on(confirmed);
                    }
                    case TrackingStarted started -> bookingTrackingStartNotificationEventHandler.on(started);
                    default -> {
                        // 購読者のないイベント
                    }
                }
            });
            return new EventSubscriptions();
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
        QuotationResponseService quotationResponseService(
                InMemoryTransportRequestRepository transportRequestRepository,
                InMemoryQuotationRepository quotationRepository,
                DeferredEventDelivery eventDelivery,
                MutableClock clock) {
            return new QuotationResponseService(transportRequestRepository, quotationRepository, eventDelivery, clock);
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

    /** 購読側を配信に登録したことを表す印（{@link Components#eventSubscriptions}）。 */
    static final class EventSubscriptions {}
}

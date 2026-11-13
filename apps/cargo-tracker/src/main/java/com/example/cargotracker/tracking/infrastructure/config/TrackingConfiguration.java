package com.example.cargotracker.tracking.infrastructure.config;

import com.example.cargotracker.booking.interfaces.api.TrackingStartNotification;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotification;
import com.example.cargotracker.routing.interfaces.api.RouteVersionLegQuery;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseActionService;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseAuthorization;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseCommandService;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingRecordCommandService;
import com.example.cargotracker.tracking.application.internal.eventhandlers.BookingTrackingStartNotificationEventHandler;
import com.example.cargotracker.tracking.application.internal.eventhandlers.BookingTransportPhaseNotificationEventHandler;
import com.example.cargotracker.tracking.application.internal.eventhandlers.ExternalMilestoneEventHandler;
import com.example.cargotracker.tracking.application.internal.eventhandlers.TrackingStartEventHandler;
import com.example.cargotracker.tracking.application.internal.eventhandlers.VoyageArrivalEventHandler;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTrackingStarts;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTransportPhases;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.RoutingScheduledLegs;
import com.example.cargotracker.tracking.application.internal.queryservices.CustomerTrackingQueryService;
import com.example.cargotracker.tracking.application.internal.queryservices.ServiceCaseOverdueQueryService;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordQueryService;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordScope;
import com.example.cargotracker.tracking.domain.model.aggregates.BusinessCalendarRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestoneRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.rules.FreshnessPolicy;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 追跡コンテキストの組み立て。腐敗防止層に経路設計と予約の公開 API をつなぎ、DE-07・DE-22 の listener を組み立てる（ADR-015。Bolt 25）。
 * 追跡記録の照会（S-11・S-12）も組み立てる（Bolt 26）。
 */
@Configuration(proxyBeanMethods = false)
public class TrackingConfiguration {

    /** 経路設計の公開 API（確定した経路版の区間）を追跡の予定区間に変える腐敗防止層（units.md の U3 → U2）。 */
    @Bean
    RoutingScheduledLegs routingScheduledLegs(RouteVersionLegQuery routeVersionLegQuery) {
        return new RoutingScheduledLegs(routeVersionLegQuery);
    }

    /** 予約の公開 API（追跡の開始の結果の通知）を呼ぶ腐敗防止層（ADR-014・015）。 */
    @Bean
    BookingTrackingStarts bookingTrackingStarts(TrackingStartNotification trackingStartNotification) {
        return new BookingTrackingStarts(trackingStartNotification);
    }

    /** DE-07 を受けて追跡を開始する（Spring Modulith のイベントの記録を経て、別のトランザクションで動く）。 */
    @Bean
    TrackingStartEventHandler trackingStartEventHandler(
            TrackingRecordRepository repository,
            RoutingScheduledLegs scheduledLegs,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        return new TrackingStartEventHandler(repository, scheduledLegs, eventPublisher, clock);
    }

    /** DE-22 を受けて予約サガを完了にする（追跡記録の保存とは別のトランザクションで動く。ADR-014）。 */
    @Bean
    BookingTrackingStartNotificationEventHandler bookingTrackingStartNotificationEventHandler(
            BookingTrackingStarts trackingStarts) {
        return new BookingTrackingStartNotificationEventHandler(trackingStarts);
    }

    /** 予約の公開 API（輸送段階の通知）を呼ぶ腐敗防止層（ADR-014。Bolt 39g2）。 */
    @Bean
    BookingTransportPhases bookingTransportPhases(TransportPhaseNotification transportPhaseNotification) {
        return new BookingTransportPhases(transportPhaseNotification);
    }

    /** DE-09・DE-10 を受けて予約の状態と輸送段階を進める（追跡記録の保存とは別のトランザクションで動く。ADR-014。Bolt 39g2）。 */
    @Bean
    BookingTransportPhaseNotificationEventHandler bookingTransportPhaseNotificationEventHandler(
            BookingTransportPhases transportPhases) {
        return new BookingTransportPhaseNotificationEventHandler(transportPhases);
    }

    /** DE-13 を受けて追跡の実績か拒否の記録を書く（Spring Modulith のイベントの記録を経て、別のトランザクションで動く。Bolt 39g）。 */
    @Bean
    ExternalMilestoneEventHandler externalMilestoneEventHandler(
            TrackingRecordRepository repository, RejectedMilestoneRepository rejectedMilestones, Clock clock) {
        return new ExternalMilestoneEventHandler(repository, rejectedMilestones, clock);
    }

    /** DE-12 を受けて最後の区間の航海の到着予定を追跡記録に受けさせる（Spring Modulith のイベントの記録を経て、別のトランザクションで動く。Bolt 39g4b）。 */
    @Bean
    VoyageArrivalEventHandler voyageArrivalEventHandler(TrackingRecordRepository repository) {
        return new VoyageArrivalEventHandler(repository);
    }

    /** 有人案件の起票と受領（US-20。認可の仮の定めは台帳 D-159。Bolt 40-7）。 */
    @Bean
    ServiceCaseCommandService serviceCaseCommandService(
            ServiceCaseRepository repository,
            ServiceCaseNumberIssuer numberIssuer,
            BusinessCalendarRepository calendars,
            Clock clock) {
        return new ServiceCaseCommandService(repository, numberIssuer, calendars, clock);
    }

    /** 有人案件の進捗・回答・終結・再開・回答期限の変更（US-20 AC4〜AC7。Bolt 40-8）。 */
    @Bean
    ServiceCaseActionService serviceCaseActionService(ServiceCaseRepository repository, Clock clock) {
        return new ServiceCaseActionService(repository, clock);
    }

    /** 受領期限を過ぎた有人案件の照会（US-20 AC3、SVC-04。読むときの判定。Bolt 40-9）。 */
    @Bean
    ServiceCaseOverdueQueryService serviceCaseOverdueQueryService(ServiceCaseRepository repository, Clock clock) {
        return new ServiceCaseOverdueQueryService(repository, clock, ServiceCaseAuthorization::canOperate);
    }

    /** 追跡管理者による主要実績の登録（US-12。Bolt 26b）。 */
    @Bean
    TrackingRecordCommandService trackingRecordCommandService(
            TrackingRecordRepository repository,
            Clock clock,
            TrackingRecordScope trackingRecordScope,
            ApplicationEventPublisher eventPublisher) {
        return new TrackingRecordCommandService(repository, clock, trackingRecordScope, eventPublisher);
    }

    /** 追跡記録の照会（S-11 追跡一覧、S-12 追跡の詳細。Bolt 26）。鮮度の判定の判定の時刻は時計から得る（Bolt 39g4）。 */
    @Bean
    TrackingRecordQueryService trackingRecordQueryService(
            TrackingRecordRepository repository,
            TrackingRecordScope trackingRecordScope,
            Clock clock,
            FreshnessPolicy freshnessPolicy) {
        return new TrackingRecordQueryService(repository, trackingRecordScope, clock, freshnessPolicy);
    }

    /** 鮮度の判定（FRS-02。許容範囲は設定の値。0 以下は組み立てで拒む。Bolt 39g4）。 */
    @Bean
    FreshnessPolicy freshnessPolicy(@Value("${cargotracker.tracking.freshness-tolerance}") Duration tolerance) {
        return new FreshnessPolicy(tolerance);
    }

    /** 社内の追跡記録の担当範囲の判定（照会とコマンドのサービスが使う。Bolt 38g）。 */
    @Bean
    TrackingRecordScope trackingRecordScope(TrackingRecordRepository repository) {
        return new TrackingRecordScope(repository);
    }

    /** 荷主の追跡の照会（C-10。社内の照会と分ける。Bolt 27）。 */
    @Bean
    CustomerTrackingQueryService customerTrackingQueryService(TrackingRecordRepository repository) {
        return new CustomerTrackingQueryService(repository);
    }
}

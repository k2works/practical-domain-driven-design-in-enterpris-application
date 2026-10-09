package com.example.cargotracker.tracking.infrastructure.config;

import com.example.cargotracker.booking.interfaces.api.TrackingStartNotification;
import com.example.cargotracker.routing.interfaces.api.RouteVersionLegQuery;
import com.example.cargotracker.tracking.application.internal.eventhandlers.BookingTrackingStartNotificationEventHandler;
import com.example.cargotracker.tracking.application.internal.eventhandlers.TrackingStartEventHandler;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTrackingStarts;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.RoutingScheduledLegs;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 追跡コンテキストの組み立て。腐敗防止層に経路設計と予約の公開 API をつなぎ、DE-07・DE-22 の listener を組み立てる（ADR-015。Bolt 25）。
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
}

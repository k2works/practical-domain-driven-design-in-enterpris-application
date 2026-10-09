package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.outboundservices.acl.RoutingScheduledLegs;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingStart;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-07 本予約を確定した を受けて追跡を開始する（ADR-015、T-INV-11・T-INV-12。Bolt 25）。経路設計の公開 API から確定した経路版の区間を
 * 引いて予定として採用し、追跡記録を作り、同じトランザクションで DE-22 を発行する。予約サガの完了は DE-22 を受けた別の listener が
 * 別のトランザクションで行う（2 つのコンテキストの集約を 1 つのトランザクションで更新しない。ADR-014）。
 *
 * <p>本予約の確定のコミットの後に、非同期で、新しいトランザクションの中で処理する。予約 ID で冪等で、同じ予約の再配信は何もしない。
 * 経路版が見つからない・区間が不正・企業 ID がない（Bolt 23・24 の形の DE-07）は、再配信で直らない欠けなので、警告のログを残して開始
 * しない（例外にしない。予約サガは処理中のまま残り、W8 の処理中の滞留の有人確認要が拾う。T-62）。同時の配信の負けた側は追跡記録の
 * 予約 ID の一意制約の違反で戻り、再配信で既存の追跡記録を見つけて何もしない。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class TrackingStartEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingStartEventHandler.class);

    private final TrackingRecordRepository repository;
    private final RoutingScheduledLegs scheduledLegs;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TrackingStartEventHandler(
            TrackingRecordRepository repository,
            RoutingScheduledLegs scheduledLegs,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.scheduledLegs = scheduledLegs;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void on(BookingConfirmed event) {
        if (repository.findByBookingId(event.bookingId()).isPresent()) {
            return;
        }
        if (event.shipperCompanyId() == null || event.consigneeCompanyId() == null) {
            LOG.warn("DE-07 に荷主・荷受人の企業 ID がないため追跡を開始しなかった: 予約 {}、追跡番号 {}", event.bookingId(), event.trackingNumber());
            return;
        }
        TrackingStart start;
        try {
            List<ScheduledLeg> legs = scheduledLegs.confirmedLegsOf(event.routingCaseNumber(), event.routeVersionNo());
            if (legs.isEmpty()) {
                LOG.warn(
                        "確定した経路版の区間がないため追跡を開始しなかった: 予約 {}、追跡番号 {}、案件 {}、経路版 {}",
                        event.bookingId(),
                        event.trackingNumber(),
                        event.routingCaseNumber(),
                        event.routeVersionNo());
                return;
            }
            start = TrackingRecord.start(
                    new TrackingNumber(event.trackingNumber()),
                    event.bookingId(),
                    event.shipperCompanyId(),
                    event.consigneeCompanyId(),
                    new Schedule(event.routingCaseNumber(), event.routeVersionNo(), legs),
                    new UtcInstant(clock.instant()));
        } catch (IllegalArgumentException invalid) {
            // 区間の写し・予定・追跡番号の検査（T-INV-12）。再配信しても直らないので、例外にせず警告のログで終える（T-62。Bolt 25 レビュー P-1）
            LOG.warn(
                    "DE-07 の値が追跡の規則に合わないため追跡を開始しなかった: 予約 {}、追跡番号 {}、理由 {}",
                    event.bookingId(),
                    event.trackingNumber(),
                    invalid.getMessage());
            return;
        }
        repository.save(start.trackingRecord());
        eventPublisher.publishEvent(start.event());
    }
}

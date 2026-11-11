package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTransportPhases;
import com.example.cargotracker.tracking.domain.events.DeliveryConfirmed;
import com.example.cargotracker.tracking.domain.events.PickupMilestoneAdopted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-09 集荷実績を採用した・DE-10 引渡しを確認した を追跡の中で受け、予約の公開 API で予約の状態と輸送段階を進める（ADR-014。Bolt 39g2）。予約は追跡の
 * イベントを購読しない（依存が循環する）。追跡記録の保存のコミットの後に、非同期で、新しいトランザクションの中で処理する。通知は予約 ID と通知の種類で
 * 冪等なので、再配信はそのまま呼び直す。予約が記録しなかった（予約がない・取消済み・申請中）ときは、警告のログを残して終える（追跡は戻さない。結果整合）。
 * 公開 API の例外（ロックの待ちの上限など）はそのまま上げ、発行の記録の再配信に任せる。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class BookingTransportPhaseNotificationEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BookingTransportPhaseNotificationEventHandler.class);

    private final BookingTransportPhases transportPhases;

    public BookingTransportPhaseNotificationEventHandler(BookingTransportPhases transportPhases) {
        this.transportPhases = transportPhases;
    }

    @ApplicationModuleListener
    public void on(PickupMilestoneAdopted event) {
        transportPhases
                .notifyPickedUp(
                        event.bookingId(),
                        event.trackingNumber(),
                        event.milestoneNo(),
                        event.occurredAt(),
                        event.operator())
                .ifPresent(reason -> LOG.warn(
                        "集荷を予約に伝えたが、予約は記録しなかった: 予約 {}、追跡番号 {}、実績番号 {}、理由 {}",
                        event.bookingId(),
                        event.trackingNumber(),
                        event.milestoneNo(),
                        reason));
    }

    @ApplicationModuleListener
    public void on(DeliveryConfirmed event) {
        transportPhases
                .notifyDelivered(
                        event.bookingId(),
                        event.trackingNumber(),
                        event.milestoneNo(),
                        event.occurredAt(),
                        event.operator())
                .ifPresent(reason -> LOG.warn(
                        "引渡しを予約に伝えたが、予約は記録しなかった: 予約 {}、追跡番号 {}、実績番号 {}、理由 {}",
                        event.bookingId(),
                        event.trackingNumber(),
                        event.milestoneNo(),
                        reason));
    }
}

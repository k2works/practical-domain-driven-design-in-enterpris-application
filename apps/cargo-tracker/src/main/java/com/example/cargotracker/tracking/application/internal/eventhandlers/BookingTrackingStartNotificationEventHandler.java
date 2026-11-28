package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.tracking.application.internal.outboundservices.acl.BookingTrackingStarts;
import com.example.cargotracker.tracking.domain.events.TrackingStarted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-22 追跡を開始した を追跡の中で受け、予約の公開 API で予約サガを完了にする（ADR-014・015。Bolt 25）。予約は追跡のイベントを購読しない
 * （依存が循環する）。追跡記録の保存のコミットの後に、非同期で、新しいトランザクションの中で処理する。通知は予約 ID で冪等なので、再配信は
 * そのまま呼び直す。予約が完了にしなかった（予約サガがない）ときは、警告のログを残して終える（追跡の開始は戻さない。結果整合）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class BookingTrackingStartNotificationEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BookingTrackingStartNotificationEventHandler.class);

    private final BookingTrackingStarts trackingStarts;

    public BookingTrackingStartNotificationEventHandler(BookingTrackingStarts trackingStarts) {
        this.trackingStarts = trackingStarts;
    }

    @ApplicationModuleListener
    public void on(TrackingStarted event) {
        trackingStarts
                .notifyStarted(event.bookingId(), event.trackingNumber(), event.startedAt())
                .ifPresent(reason -> LOG.warn(
                        "追跡の開始を予約に返したが、予約サガを完了にしなかった: 予約 {}、追跡番号 {}、理由 {}",
                        event.bookingId(),
                        event.trackingNumber(),
                        reason));
    }
}

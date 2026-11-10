package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.externaldata.domain.events.MilestoneAdoptedValueAccepted;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.ExternalMilestoneReceipt;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestoneRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-13 主要実績の採用値を受け付けた を購読し、追跡の実績か拒否の記録を書く（US-12 AC1・AC2・AC4・AC5、US-14 AC1。Bolt 39g）。取込か採用の判断のコミットの後に、非同期で、
 * 新しいトランザクションの中で処理する。配信は少なくとも 1 回で順も保たれない。
 *
 * <p>同じ追跡番号の DE-13 は追跡番号の行を押さえて直列にする（決定 (4)。ロックの後は競合しないので読み直しの繰り返しは作らない）。判定の順は、拒否の記録あり → 知らない追跡番号 → 同じ出典 →
 * 取消済み → 完了後 → 通常（決定 (7)）。取消済みと知らない追跡番号は実績を作らず、拒否の記録と WARN のログに残す（例外にしない。直らないイベントを発行記録に残さない）。
 *
 * <p>内容の欠けた DE-13 は再配信しても直らないので、ERROR のログを残して捨てる。DB の失敗（ロックの待ちの上限を含む）とプログラムの誤りは例外を上げて発行記録を未完了に残す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class ExternalMilestoneEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalMilestoneEventHandler.class);

    private final TrackingRecordRepository repository;
    private final RejectedMilestoneRepository rejectedMilestones;
    private final Clock clock;

    public ExternalMilestoneEventHandler(
            TrackingRecordRepository repository, RejectedMilestoneRepository rejectedMilestones, Clock clock) {
        this.repository = repository;
        this.rejectedMilestones = rejectedMilestones;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void on(MilestoneAdoptedValueAccepted event) {
        ExternalMilestone external;
        try {
            external = toExternalMilestone(event);
        } catch (ExternalMilestone.InvalidEventValueException e) {
            LOG.error(
                    "DE-13 を追跡に書かずに捨てた（イベントの内容が欠けている。再配信しても直らない。調査が要る）: 追跡番号 {}、採用値 {}、欠け {}",
                    event.trackingNumber(),
                    event.adoptedValueId(),
                    e.getMessage());
            return;
        }
        Optional<TrackingRecord> found = repository.findByTrackingNumberForExternalMilestone(external.trackingNumber());
        if (rejectedMilestones.existsBySource(external.source())) {
            return;
        }
        if (found.isEmpty()) {
            reject(external, MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER);
            return;
        }
        UtcInstant receivedAt = new UtcInstant(clock.instant());
        switch (found.get().receiveExternalMilestone(external, receivedAt)) {
            case ExternalMilestoneReceipt.Received received -> repository.update(received.trackingRecord());
            case ExternalMilestoneReceipt.AlreadyReceived _ -> {
                // 同じ出典の実績がある（再配信。T-INV-02）
            }
            case ExternalMilestoneReceipt.BookingCancelled _ ->
                reject(external, MilestoneRejectionReason.BOOKING_CANCELLED);
        }
    }

    private void reject(ExternalMilestone external, MilestoneRejectionReason reason) {
        rejectedMilestones.saveIfAbsent(
                RejectedMilestone.of(UUID.randomUUID(), external, reason, new UtcInstant(clock.instant())));
        LOG.warn(
                "DE-13 を実績にせず拒否の記録に残した: 追跡番号 {}、理由 {}、受信記録 {}",
                external.trackingNumber().value(),
                reason,
                external.source().reference());
    }

    private static ExternalMilestone toExternalMilestone(MilestoneAdoptedValueAccepted event) {
        return new ExternalMilestone(
                ExternalMilestone.parseTrackingNumber(event.trackingNumber()),
                ExternalMilestone.parseKind(event.kind()),
                event.location(),
                event.occurredAt(),
                event.source(),
                ExternalMilestone.externalOrigin(event.dataSourceId(), event.externalId(), event.publishedAt()),
                event.operatorUserId());
    }
}

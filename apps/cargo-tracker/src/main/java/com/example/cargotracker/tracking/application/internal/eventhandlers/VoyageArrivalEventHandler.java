package com.example.cargotracker.tracking.application.internal.eventhandlers;

import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * 航海の到着予定のイベントハンドラー。DE-12 航海の採用値を更新した を購読し、その航海を最後の区間に持つ追跡記録に航海の到着予定を受けさせる（T-INV-10、ADR-004。
 * Bolt 39g4b。【業務の判断】(at)・(au)・(av)）。取込か採用の判断のコミットの後に、非同期で、新しいトランザクションの中で処理する。配信は少なくとも 1 回で順も
 * 保たれないので、追跡記録が保存した公開の順より厳密に新しい知らせだけを受ける（冪等。比べるのは追跡記録）。
 *
 * <p>対象の追跡番号は照会が追跡番号の順に返し、その順に 1 件ずつ行を押さえてから読み直して受け、更新する（1 つのトランザクション。同じ航海の DE-12 どうしは同じ順で
 * 押さえるのでデッドロックしない。決定 (7)）。最後の区間でない・完了後・取消済み・新しくない知らせは追跡記録が変えないので保存しない。イベントの型はこの listener だけが
 * 読み、追跡の値（航海の寄港・公開の順）に写す。
 *
 * <p>内容の欠けた DE-12 は再配信しても直らないので、ERROR のログを残して捨てる。DB の失敗（ロックの待ちの上限を含む）とプログラムの誤りは例外を上げて発行記録を未完了に
 * 残す（再配信は公開の順で冪等。1 つの追跡記録の失敗で同じ DE-12 のほかの追跡記録も戻る）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class VoyageArrivalEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(VoyageArrivalEventHandler.class);

    private final TrackingRecordRepository repository;

    public VoyageArrivalEventHandler(TrackingRecordRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(VoyageAdoptedValueUpdated event) {
        String voyageNumber;
        List<VoyagePortCall> portCalls;
        PublicationOrder order;
        try {
            voyageNumber = VoyageArrivalInput.voyageNumberOf(event.voyageNumber());
            portCalls = VoyageArrivalInput.portCallsOf(event.portCalls().stream()
                    .map(call -> VoyageArrivalInput.portCallOf(call.port(), call.arrivalAt(), call.departureAt()))
                    .toList());
            order = VoyageArrivalInput.orderOf(event.publishedAt(), event.source());
        } catch (VoyageArrivalInput.InvalidEventValueException e) {
            LOG.error(
                    "DE-12 を追跡に受けずに捨てた（イベントの内容が欠けている。再配信しても直らない。調査が要る）: 航海 {}、採用値 {}、欠け {}",
                    event.voyageNumber(),
                    event.adoptedValueId(),
                    e.getMessage());
            return;
        }
        for (TrackingNumber trackingNumber : repository.findTrackingNumbersByFinalLegVoyage(voyageNumber)) {
            repository
                    .findByTrackingNumberForUpdate(trackingNumber)
                    .flatMap(trackingRecord -> trackingRecord.receiveVoyageArrival(voyageNumber, portCalls, order))
                    .ifPresent(repository::update);
        }
    }
}

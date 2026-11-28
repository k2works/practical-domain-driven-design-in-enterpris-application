package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.application.internal.TransportRequestProgression;
import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-16 荷主が詳細経路設計を依頼した を購読し、輸送要求を経路設計中にする（US-24 AC1。2026-10-06 の決定）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 * 配信は少なくとも 1 回なので、見積り作成中・見積提示済みで同じ版のときだけ変える（冪等）。再配信（すでに同じ版で経路設計中）の
 * ほかに状態を変えなかったときは、結果整合が崩れた手がかりとして警告のログを残す（DE-03 と同じ。Bolt 9・10 レビュー R-06）。
 * ほかの listener と同じ輸送要求を並行して更新して競合したら、読み直してやり直す（{@link TransportRequestProgression}。Bolt 22 の割り込み）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class RouteDesignRequestedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RouteDesignRequestedEventHandler.class);

    private final TransportRequestProgression progression;

    public RouteDesignRequestedEventHandler(TransportRequestRepository repository) {
        this.progression = new TransportRequestProgression(
                repository,
                LOG,
                "DE-16",
                TransportRequestStatus.ROUTING,
                "経路設計中",
                TransportRequest::markRoutingRequested);
    }

    @ApplicationModuleListener
    public void on(RouteDesignRequested event) {
        progression.advance(event.transportRequestId(), event.quotationId(), event.transportRequestVersionNo());
    }
}

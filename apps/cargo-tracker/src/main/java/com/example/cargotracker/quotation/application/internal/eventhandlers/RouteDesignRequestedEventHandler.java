package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.RouteDesignRequested;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-16 荷主が詳細経路設計を依頼した を購読し、輸送要求を経路設計中にする（US-24 AC1。2026-10-06 の決定）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 * 配信は少なくとも 1 回なので、見積り作成中・見積提示済みで同じ版のときだけ変える（冪等）。再配信（すでに同じ版で経路設計中）の
 * ほかに状態を変えなかったときは、結果整合が崩れた手がかりとして警告のログを残す（DE-03 と同じ。Bolt 9・10 レビュー R-06）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class RouteDesignRequestedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RouteDesignRequestedEventHandler.class);

    private final TransportRequestRepository repository;

    public RouteDesignRequestedEventHandler(TransportRequestRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(RouteDesignRequested event) {
        Optional<TransportRequest> found = repository.findById(new TransportRequestId(event.transportRequestId()));
        if (found.isEmpty()) {
            LOG.warn("DE-16 の輸送要求が見つからない: 輸送要求 {}、見積り {}", event.transportRequestId(), event.quotationId());
            return;
        }
        TransportRequest request = found.get();
        if (request.markRoutingRequested(event.transportRequestVersionNo())) {
            repository.update(request);
        } else if (!isAlreadyRoutingForVersion(request, event)) {
            LOG.warn(
                    "DE-16 で輸送要求を経路設計中にしなかった: 輸送要求 {}、見積り {} の版 {}、現在の版 {}、状態 {}",
                    event.transportRequestId(),
                    event.quotationId(),
                    event.transportRequestVersionNo(),
                    request.currentVersion().versionNo(),
                    request.status());
        }
    }

    /** 同じ版で経路設計中（DE-16 の再配信。変えなくてよい）。 */
    private static boolean isAlreadyRoutingForVersion(TransportRequest request, RouteDesignRequested event) {
        return request.status() == TransportRequestStatus.ROUTING
                && request.currentVersion().versionNo() == event.transportRequestVersionNo();
    }
}

package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
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
 * DE-21 経路版を見積りに割り当てた を購読し、輸送要求を荷主承認待ちにする（US-24 AC4。Bolt 20）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 * 配信は少なくとも 1 回なので、先の状態へだけ進め、同じ版のときだけ変える（冪等。DE-03・DE-16 と同じ）。再配信のほかに状態を
 * 変えなかったときは、結果整合が崩れた手がかりとして警告のログを残す（Bolt 9・10 レビュー R-06）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationRouteAssignedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationRouteAssignedEventHandler.class);

    private final TransportRequestRepository repository;

    public QuotationRouteAssignedEventHandler(TransportRequestRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(QuotationRouteAssigned event) {
        Optional<TransportRequest> found = repository.findById(new TransportRequestId(event.transportRequestId()));
        if (found.isEmpty()) {
            LOG.warn("DE-21 の輸送要求が見つからない: 輸送要求 {}、見積り {}", event.transportRequestId(), event.quotationId());
            return;
        }
        TransportRequest request = found.get();
        if (request.markAwaitingApproval(event.transportRequestVersionNo())) {
            repository.update(request);
        } else if (!request.hasReached(TransportRequestStatus.AWAITING_APPROVAL, event.transportRequestVersionNo())) {
            LOG.warn(
                    "DE-21 で輸送要求を荷主承認待ちにしなかった: 輸送要求 {}、見積り {} の版 {}、現在の版 {}、状態 {}",
                    event.transportRequestId(),
                    event.quotationId(),
                    event.transportRequestVersionNo(),
                    request.currentVersion().versionNo(),
                    request.status());
        }
    }
}

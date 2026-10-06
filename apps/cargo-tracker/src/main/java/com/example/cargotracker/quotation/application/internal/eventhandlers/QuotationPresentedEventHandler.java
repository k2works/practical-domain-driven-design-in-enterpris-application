package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.QuotationPresented;
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
 * DE-03 見積りを提示した を購読し、輸送要求を見積提示済みにする（US-03。2026-10-05 の決定）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 * 配信は少なくとも 1 回なので、見積り作成中でなければ何もしない（冪等）。
 * 再配信（すでに同じ版で見積提示済み）のほかに状態を変えなかったとき（版の食い違い・輸送要求がない）は、
 * 結果整合が崩れた手がかりとして警告のログを残す（Bolt 9・10 レビュー R-06）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationPresentedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationPresentedEventHandler.class);

    private final TransportRequestRepository repository;

    public QuotationPresentedEventHandler(TransportRequestRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(QuotationPresented event) {
        Optional<TransportRequest> found = repository.findById(new TransportRequestId(event.transportRequestId()));
        if (found.isEmpty()) {
            LOG.warn("DE-03 の輸送要求が見つからない: 輸送要求 {}、見積り {}", event.transportRequestId(), event.quotationId());
            return;
        }
        TransportRequest request = found.get();
        if (request.markQuotationPresented(event.transportRequestVersionNo())) {
            repository.update(request);
        } else if (!isAlreadyQuotedForVersion(request, event)) {
            LOG.warn(
                    "DE-03 で輸送要求を見積提示済みにしなかった: 輸送要求 {}、見積り {} の版 {}、現在の版 {}、状態 {}",
                    event.transportRequestId(),
                    event.quotationId(),
                    event.transportRequestVersionNo(),
                    request.currentVersion().versionNo(),
                    request.status());
        }
    }

    /**
     * 同じ版で見積提示済み（DE-03 の再配信か、再見積りの後の新しい見積りの提示。Bolt 11 レビュー R-25）か、同じ版で経路設計中
     * （DE-16 が先に届いた後の DE-03。Bolt 12 レビュー R-04）。どれも変えなくてよい。
     */
    private static boolean isAlreadyQuotedForVersion(TransportRequest request, QuotationPresented event) {
        return (request.status() == TransportRequestStatus.QUOTED || request.status() == TransportRequestStatus.ROUTING)
                && request.currentVersion().versionNo() == event.transportRequestVersionNo();
    }
}

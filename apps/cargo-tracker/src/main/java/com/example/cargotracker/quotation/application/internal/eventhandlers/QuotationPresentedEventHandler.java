package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-03 見積りを提示した を購読し、輸送要求を見積提示済みにする（US-03。2026-10-05 の決定）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 * 配信は少なくとも 1 回なので、見積り作成中でなければ何もしない（冪等）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationPresentedEventHandler {

    private final TransportRequestRepository repository;

    public QuotationPresentedEventHandler(TransportRequestRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(QuotationPresented event) {
        repository.findById(new TransportRequestId(event.transportRequestId())).ifPresent(request -> {
            if (request.markQuotationPresented(event.transportRequestVersionNo())) {
                repository.update(request);
            }
        });
    }
}

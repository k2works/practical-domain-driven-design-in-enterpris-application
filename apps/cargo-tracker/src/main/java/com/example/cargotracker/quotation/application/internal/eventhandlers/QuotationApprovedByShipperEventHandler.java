package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-04 見積りを荷主が承認した を購読し、輸送要求を予約待ちにする（US-24 AC4。Bolt 20）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationApprovedByShipperEventHandler {

    private final TransportRequestRepository repository;

    public QuotationApprovedByShipperEventHandler(TransportRequestRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(QuotationApprovedByShipper event) {
        // Bolt 20 ステップ 3 の Green で作る
    }
}

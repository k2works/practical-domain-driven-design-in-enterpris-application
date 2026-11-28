package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.application.internal.TransportRequestProgression;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
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
 * ほかの listener と同じ輸送要求を並行して更新して競合したら、読み直してやり直す（{@link TransportRequestProgression}。Bolt 22 の割り込み）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationPresentedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationPresentedEventHandler.class);

    private final TransportRequestProgression progression;

    public QuotationPresentedEventHandler(TransportRequestRepository repository) {
        this.progression = new TransportRequestProgression(
                repository,
                LOG,
                "DE-03",
                TransportRequestStatus.QUOTED,
                "見積提示済み",
                TransportRequest::markQuotationPresented);
    }

    @ApplicationModuleListener
    public void on(QuotationPresented event) {
        progression.advance(event.transportRequestId(), event.quotationId(), event.transportRequestVersionNo());
    }
}

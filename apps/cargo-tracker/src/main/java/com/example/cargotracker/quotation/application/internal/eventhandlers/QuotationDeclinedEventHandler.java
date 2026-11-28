package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.application.internal.TransportRequestProgression;
import com.example.cargotracker.quotation.domain.events.QuotationDeclined;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-17 荷主が見積りを辞退した を購読し、輸送要求を辞退で終了にする（US-24 AC2。W9 の Bolt 48-1。台帳 D-173）。見積りの保存のコミットの後に、
 * 非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。配信は少なくとも 1 回なので、見積り作成中・
 * 見積提示済みで同じ版のときだけ変える（冪等）。すでに辞退で終了なら変えない。ほかの listener と同じ輸送要求を並行して更新して競合したら、
 * 読み直してやり直す（{@link TransportRequestProgression}）。辞退は輸送要求を先へ進める操作ではなく終わらせる操作なので、「すでに
 * 届いているか」は辞退で終了かで見る。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationDeclinedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationDeclinedEventHandler.class);

    private final TransportRequestProgression progression;

    public QuotationDeclinedEventHandler(TransportRequestRepository repository) {
        this.progression = new TransportRequestProgression(
                        repository,
                        LOG,
                        "DE-17",
                        TransportRequestStatus.WITHDRAWN,
                        "辞退で終了",
                        TransportRequest::markWithdrawn)
                .reachedWhen(TransportRequest::isWithdrawn);
    }

    @ApplicationModuleListener
    public void on(QuotationDeclined event) {
        progression.advance(event.transportRequestId(), event.quotationId(), event.transportRequestVersionNo());
    }
}

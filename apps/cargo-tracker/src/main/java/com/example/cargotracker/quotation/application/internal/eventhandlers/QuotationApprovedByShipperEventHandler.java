package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.application.internal.QuotationGate;
import com.example.cargotracker.quotation.application.internal.TransportRequestProgression;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-04 見積りを荷主が承認した を購読し、輸送要求を予約待ちにする（US-24 AC4。Bolt 20）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 * 配信は少なくとも 1 回なので、先の状態へだけ進め、同じ版のときだけ変える（冪等。DE-03・DE-16 と同じ）。再配信のほかに状態を
 * 変えなかったときは、結果整合が崩れた手がかりとして警告のログを残す（Bolt 9・10 レビュー R-06）。
 * ほかの listener と同じ輸送要求を並行して更新して競合したら、読み直してやり直す（{@link TransportRequestProgression}。Bolt 22 の割り込み）。
 * 予約待ちに届いていても輸送要求を書いて版を上げ、再見積りの提示で荷主承認待ちへ戻す DE-24 と楽観ロックでぶつける。再見積りで
 * 置換済み・失効になった見積りの DE-04 が遅れて届いても進めない（輸送要求を読んだ後に見積りを読む。ADR-018。Bolt 35b2）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationApprovedByShipperEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationApprovedByShipperEventHandler.class);

    private final TransportRequestProgression progression;

    public QuotationApprovedByShipperEventHandler(
            TransportRequestRepository repository, QuotationRepository quotationRepository) {
        this.progression = new TransportRequestProgression(
                        repository,
                        LOG,
                        "DE-04",
                        TransportRequestStatus.READY_TO_BOOK,
                        "予約待ち",
                        TransportRequest::markReadyToBook)
                .checking(QuotationGate.notRetired(quotationRepository))
                .writingWhenReached();
    }

    @ApplicationModuleListener
    public void on(QuotationApprovedByShipper event) {
        progression.advance(event.transportRequestId(), event.quotationId(), event.transportRequestVersionNo());
    }
}

package com.example.cargotracker.quotation.application.internal.eventhandlers;

import com.example.cargotracker.quotation.application.internal.QuotationGate;
import com.example.cargotracker.quotation.application.internal.TransportRequestProgression;
import com.example.cargotracker.quotation.domain.events.QuotationPresentedForReapproval;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * DE-24 再見積りを荷主の再承認待ちにした を購読し、輸送要求を予約待ちから荷主承認待ちへ戻す（D-85。Bolt 35b2）。
 * 見積りの保存のコミットの後に、非同期で、新しいトランザクションの中で処理する（2 つの集約を 1 つのトランザクションで更新しない）。
 * 届く順の入れ替わりの守り（ADR-018）: 輸送要求を読んだ後に見積りを読み、見積りがまだ荷主承認待ちのときだけ戻す。承認済みなら
 * その見積りの DE-04 が進めるので何もしない。輸送要求がすでに荷主承認待ちでも書いて版を上げ、先に旧版を読んだ DE-04 の listener を
 * 楽観ロックでぶつける。置換済み・失効・見つからない見積りと、予約確定済みの輸送要求（D-87）は変えず警告のログを残す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class QuotationPresentedForReapprovalEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QuotationPresentedForReapprovalEventHandler.class);

    private final TransportRequestProgression progression;

    public QuotationPresentedForReapprovalEventHandler(
            TransportRequestRepository repository, QuotationRepository quotationRepository) {
        this.progression = new TransportRequestProgression(
                        repository,
                        LOG,
                        "DE-24",
                        TransportRequestStatus.AWAITING_APPROVAL,
                        "荷主承認待ち",
                        TransportRequest::returnToAwaitingApproval)
                .checking(QuotationGate.awaitingReapproval(quotationRepository))
                .reachedWhen(QuotationPresentedForReapprovalEventHandler::isAwaitingApproval)
                .writingWhenReached();
    }

    @ApplicationModuleListener
    public void on(QuotationPresentedForReapproval event) {
        progression.advance(event.transportRequestId(), event.quotationId(), event.transportRequestVersionNo());
    }

    /** ちょうど荷主承認待ちか（予約待ち・予約確定済みは届いていない扱いにする。予約確定済みは警告のログになる）。 */
    private static boolean isAwaitingApproval(TransportRequest request, int versionNo) {
        return request.hasReached(TransportRequestStatus.AWAITING_APPROVAL, versionNo)
                && !request.hasReached(TransportRequestStatus.READY_TO_BOOK, versionNo);
    }
}

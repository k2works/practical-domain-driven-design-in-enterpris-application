package com.example.cargotracker.quotation.application.internal;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import java.util.Optional;
import java.util.UUID;

/**
 * 輸送要求を進める・戻す前に、イベントの見積りを読んで決める判定（ADR-018。Bolt 35b2）。{@link TransportRequestProgression} が輸送要求を
 * 読んだ後に呼び、楽観ロックの競合でやり直すたびに呼び直す。
 *
 * <p>守りが成り立つ条件: この判定が読む見積りの状態を変える操作の後には、必ず輸送要求を書く listener が続くこと（荷主の承認の後の
 * DE-04、再見積りの提示の後の DE-24。どちらも届いていても書く）。判定に新しい見積りの状態を使うときは、その状態の変化の後に輸送要求を
 * 書く listener を用意する（荷主の辞退・D-86 を作るとき）。
 */
@FunctionalInterface
public interface QuotationGate {

    /** 見積りを読まずに進める（DE-03・DE-16・DE-07）。 */
    QuotationGate OPEN = _ -> Verdict.PROCEED;

    /**
     * @param quotationId イベントの見積り ID
     * @return 判定
     */
    Verdict check(UUID quotationId);

    /**
     * 置換済み・失効の記録がある見積り（再見積りで旧版になった）のイベントでは進めない（DE-21・DE-04）。見積りが見つからないときは
     * 今までどおり進める（見積りの有無はこの判定の役目でない）。
     */
    static QuotationGate notRetired(QuotationRepository quotations) {
        return quotationId -> find(quotations, quotationId)
                .filter(quotation -> quotation.status().isRetired())
                .map(_ -> Verdict.RETIRED)
                .orElse(Verdict.PROCEED);
    }

    /**
     * 再見積りした見積りがまだ荷主承認待ちのときだけ戻す（DE-24）。承認済みならその見積りの DE-04 が進めるので何もしない。
     * 置換済み・失効・見つからない・ほかの状態では戻さない。
     */
    static QuotationGate awaitingReapproval(QuotationRepository quotations) {
        return quotationId -> find(quotations, quotationId)
                .map(quotation -> switch (quotation.status()) {
                    case AWAITING_SHIPPER_APPROVAL -> Verdict.PROCEED;
                    case APPROVED -> Verdict.SETTLED;
                    case EXPIRED, REPLACED -> Verdict.RETIRED;
                    case DRAFT, PENDING_APPROVAL, PRESENTED, ROUTING_REQUESTED -> Verdict.NOT_AWAITING;
                })
                .orElse(Verdict.NOT_FOUND);
    }

    private static Optional<Quotation> find(QuotationRepository quotations, UUID quotationId) {
        return quotations.findById(new QuotationId(quotationId));
    }

    /** 判定。 */
    enum Verdict {
        /** 進める・戻す。 */
        PROCEED("進めてよい"),
        /** すでに決着している（何もせず、ログも残さない）。 */
        SETTLED("承認済み"),
        /** 置換済み・失効の記録がある（警告のログ）。 */
        RETIRED("置換済み・失効"),
        /** 見つからない（警告のログ）。 */
        NOT_FOUND("見つからない"),
        /** 荷主承認待ちでない（警告のログ）。 */
        NOT_AWAITING("荷主承認待ちでない");

        private final String description;

        Verdict(String description) {
            this.description = description;
        }

        /** ログに出す説明。 */
        public String description() {
            return description;
        }
    }
}

package com.example.cargotracker.quotation.interfaces.api;

/**
 * 予約確定に使える見積りの照会（見積りの公開 API。ADR-016、Q-INV-06。Bolt 23）。予約が本予約の確定のトランザクションの中で、
 * commit 時刻を渡して呼ぶ。失効の判定（BR-10。有効期限と同時刻以後は失効）は見積りの 1 か所に置き、予約は期限を比べない。
 */
public interface BookableQuotationQuery {

    /**
     * 見積りが commit 時刻に予約確定に使えるかを確かめる。
     *
     * @param request 業務番号・見積り番号と commit 時刻
     * @return 使えるなら確定に要る写し、使えないなら理由
     */
    BookableQuotationResult find(BookableQuotationRequest request);

    /**
     * 本予約の確定のトランザクションの中で、見積りの行をそのコミットまで押さえてから {@link #find} と同じ判定をする（ADR-016、
     * US-07 AC5。Bolt 37e2）。再設計要の印を付ける操作は、確定のコミットを待つか、先に付けた印を確定に見せる。呼び手のトランザクションが
     * なければ例外にする。
     *
     * @param request 業務番号・見積り番号と commit 時刻
     * @return 使えるなら確定に要る写し、使えないなら理由
     */
    BookableQuotationResult findForConfirmation(BookableQuotationRequest request);
}

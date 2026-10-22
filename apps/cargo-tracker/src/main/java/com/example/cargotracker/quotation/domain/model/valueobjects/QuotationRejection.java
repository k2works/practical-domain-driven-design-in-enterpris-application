package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 見積りの操作を受け付けなかった理由（業務の結果。Q-INV-07・09・17・18）。
 */
public enum QuotationRejection {
    /** 輸送要求が見積りを作れる状態でない（審査中・下書きなど。再見積りは、見積り作成中・見積提示済み・経路設計中・荷主承認待ち・予約待ちのどれでもないとき。Bolt 35b）。 */
    TRANSPORT_REQUEST_NOT_QUOTING,
    /** 輸送要求が本予約を確定済みのため、再見積りできない（D-85。Bolt 35c）。 */
    TRANSPORT_REQUEST_BOOKED,
    /** 作成中・承認待ち・提示済みの見積りがすでにある（失効した提示済みも含む。新しくするには再見積りする）。 */
    ALREADY_QUOTED,
    /** 承認待ちでない見積りを社内承認して提示しようとした。 */
    NOT_PENDING_APPROVAL,
    /** 見積りが失効している（判定時刻が有効期限と同時刻または後。Q-INV-07。再見積りが必要）。 */
    EXPIRED,
    /** 見積りが置換済み（Q-INV-07。置換先の見積りを使う）。 */
    REPLACED,
    /** 見積りの対象の版が、輸送要求の現在の版でない（古い版に対する見積りは再見積りできない。Bolt 11 レビュー R-06）。 */
    OUTDATED_VERSION,
    /** 荷主に提示していない見積りに回答しようとした（Q-INV-09。Bolt 12）。 */
    NOT_PRESENTED,
    /** 見積りが詳細設計依頼済み（回答済み。Q-INV-09。経路設計の途中の再見積りは US-05・US-07 で決める。Bolt 12）。 */
    ROUTING_REQUESTED,
    /** 荷主承認待ちでない見積りを荷主が承認しようとした（経路版の割当ての前など。Q-INV-10。Bolt 20）。 */
    NOT_AWAITING_SHIPPER_APPROVAL,
    /** 見積りが承認済み（Q-INV-10。二重送信など。Bolt 20）。 */
    ALREADY_APPROVED,
    /** 見積りが荷主の承認済みでないため、予約確定に使えない（Q-INV-06。Bolt 23）。 */
    NOT_APPROVED,
    /** 見積りが荷主の承認済みで、まだ有効期限内なので再見積りできない（本予約の確定へ進む。D-85、Q-INV-07。Bolt 35b）。 */
    APPROVED_AND_STILL_VALID,
    /**
     * 割り当てた経路版が再設計要のため、荷主の承認と予約確定に使えない（Q-INV-10、B-INV-05。確定した後に外部情報の更新で条件を満たさなく
     * なった。US-07 AC5。Bolt 37e）。
     */
    ROUTE_REDESIGN_REQUIRED
}

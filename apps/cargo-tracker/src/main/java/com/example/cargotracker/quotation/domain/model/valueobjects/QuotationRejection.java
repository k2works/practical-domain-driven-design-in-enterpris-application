package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 見積りの操作を受け付けなかった理由（業務の結果。Q-INV-07・09・17・18）。
 */
public enum QuotationRejection {
    /** 輸送要求が見積り作成中でない（審査中・下書きなど）。 */
    TRANSPORT_REQUEST_NOT_QUOTING,
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
    ROUTING_REQUESTED
}

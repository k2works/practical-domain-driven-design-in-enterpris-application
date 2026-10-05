package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 見積りの操作を受け付けなかった理由（業務の結果。Q-INV-17・18）。
 */
public enum QuotationRejection {
    /** 輸送要求が見積り作成中でない（審査中・下書きなど）。 */
    TRANSPORT_REQUEST_NOT_QUOTING,
    /** 作成中・承認待ち・提示済みの見積りがすでにある（置換は Bolt 11）。 */
    ALREADY_QUOTED,
    /** 承認待ちでない見積りを社内承認して提示しようとした。 */
    NOT_PENDING_APPROVAL
}

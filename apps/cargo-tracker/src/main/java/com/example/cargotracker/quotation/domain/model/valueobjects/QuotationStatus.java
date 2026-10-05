package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 見積りの状態。値の名前はデータモデルの状態の値と同じにする。Bolt 10 は提示までの 3 つ。
 */
public enum QuotationStatus {
    /** 作成中（算出の前）。 */
    DRAFT,
    /** 承認待ち（算出した後、社内承認の前）。 */
    PENDING_APPROVAL,
    /** 提示済み（社内承認して荷主へ提示した）。 */
    PRESENTED
}

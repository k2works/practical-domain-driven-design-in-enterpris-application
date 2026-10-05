package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 見積りの状態。値の名前はデータモデルの状態の値と同じにする。Bolt 10 は提示までの 3 つ、Bolt 11 で失効と置換済みを足した。
 * 失効は、有効期限を過ぎても状態を書き換えず判定時刻で決める（{@code Quotation#isExpiredAt}）。状態の失効は、再見積りのときに
 * 有効期限を過ぎていた旧版にだけ記録する（2026-10-05 の決定）。
 */
public enum QuotationStatus {
    /** 作成中（算出の前）。 */
    DRAFT,
    /** 承認待ち（算出した後、社内承認の前）。 */
    PENDING_APPROVAL,
    /** 提示済み（社内承認して荷主へ提示した）。 */
    PRESENTED,
    /** 失効（再見積りのときに有効期限を過ぎていた。終わりの状態）。 */
    EXPIRED,
    /** 置換済み（再見積りで新しい見積りに置き換えた。終わりの状態）。 */
    REPLACED
}

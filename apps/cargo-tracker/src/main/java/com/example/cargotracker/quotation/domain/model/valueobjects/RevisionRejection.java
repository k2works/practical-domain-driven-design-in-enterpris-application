package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 本予約前の条件の変更を受け付けなかった理由（US-05 AC1。Bolt 62。輸送条件の入力の不足・誤りは別に、提出の検証結果として返す）。
 */
public enum RevisionRejection {
    /** 下書き（出し直しを使う）・辞退で終了・予約確定済みのため、条件を変更できない。 */
    NOT_REVISABLE,
    /** 見ていた版が現在の版でない（ほかの人が先に変更した。上書きしないで最新の版の確認を求める）。 */
    STALE_VERSION
}

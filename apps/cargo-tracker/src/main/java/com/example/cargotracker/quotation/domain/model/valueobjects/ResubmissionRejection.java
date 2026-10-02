package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 再提出を受け付けなかった理由（輸送条件の入力の不足・誤りは別に、提出の検証結果として返す）。
 */
public enum ResubmissionRejection {
    /** 下書きでないため再提出できない（Q-INV-15）。 */
    NOT_DRAFT
}

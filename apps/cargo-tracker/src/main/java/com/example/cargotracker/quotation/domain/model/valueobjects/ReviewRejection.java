package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 審査（確定・差戻し）を受け付けなかった理由。利用者が受け止めて次の行動を選べる業務の結果なので、例外でなく値で返す。
 */
public enum ReviewRejection {
    /** 審査中でないため、審査を確定・差戻しできない（Q-INV-14）。 */
    NOT_UNDER_REVIEW,
    /** 審査の対象の版が現在の版でない（古い版。Q-INV-04）。最新版の再審査が要る。 */
    STALE_VERSION,
    /** 確定の根拠・差戻しの理由がない（Q-INV-14）。 */
    RATIONALE_REQUIRED,
    /** 確定の根拠・差戻しの理由が 4,000 文字を超える（Q-INV-14）。 */
    RATIONALE_TOO_LONG,
    /** 差戻しの不足事項が 4,000 文字を超える（Q-INV-14）。 */
    MISSING_ITEMS_TOO_LONG
}

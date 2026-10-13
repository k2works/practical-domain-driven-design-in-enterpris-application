package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 認証を受け付けなかった理由（監査記録の理由。IA-INV-09）。利用者には、どの理由でも同じ応答を返す（US-18 AC2、SEC-11）。
 */
public enum AuthenticationRejection {
    /** password が誤っている。 */
    BAD_CREDENTIALS,
    /** メールアドレスの利用者がいない（監査記録にメールアドレスを残さない）。 */
    UNKNOWN_USER,
    /** 利用者が利用停止。 */
    SUSPENDED,
    /** 所属企業が無効。 */
    COMPANY_INACTIVE,
    /** TOTP の認証コードが前後 1 つの刻みのどれとも一致しない（Bolt 31）。 */
    INVALID_TOTP,
    /** 使った刻み以下の TOTP の認証コード（同じコードの再利用。IA-INV-10、Bolt 31）。 */
    TOTP_REUSED,
    /** TOTP を登録していない利用者が認証コードを送った（A-02 の画面は A-07 へ送るので、画面を経ない送信。Bolt 31d）。 */
    TOTP_NOT_ENROLLED,
    /** 回復コードが形の違い・誤り・使用済みのどれか、または回復コードと認証コードの両方を送った（Bolt 31c）。 */
    INVALID_RECOVERY_CODE
}

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
    COMPANY_INACTIVE
}

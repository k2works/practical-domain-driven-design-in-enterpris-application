package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 監査記録の操作（データモデル `audit_record.action`）。Bolt 14 はログインの成功・失敗とログアウト。Bolt 31 で password の段の
 * 成功を分けた（ADR-012 の見直しの 1）。
 */
public enum AuditAction {
    /** password を確かめた（2 段階のログインの 1 段目。まだ業務の画面は開けない）。 */
    PASSWORD_VERIFIED,
    /** ログインに成功した（password と TOTP の 2 つの要素がそろった）。 */
    LOGIN_SUCCEEDED,
    /** ログインに失敗した。 */
    LOGIN_FAILED,
    /** TOTP を登録した（Bolt 31b）。 */
    TOTP_ENROLLED,
    /** 認証アプリを登録し直した（回復コードでログインした後。秘密を置き換え、回復コードを発行し直した。Bolt 33b）。 */
    TOTP_RE_ENROLLED,
    /** 回復コードを 1 つ使った（A-02 で TOTP の代わりに使った。Bolt 31c。続けて LOGIN_SUCCEEDED を書く）。 */
    RECOVERY_CODE_USED,
    /** 5 回続けて失敗してロックした（Bolt 32。US-18 AC3）。 */
    ACCOUNT_LOCKED,
    /** request ごとの確かめで利用停止・無効な企業・利用者なしを見つけて session を捨てた（Bolt 32c。US-18 AC6）。 */
    SESSION_REVOKED,
    /** ログアウトした。 */
    LOGOUT,
    /**
     * password の再設定を依頼した（Bolt 33。US-18 AC8）。有効な利用者なら成功、登録なし・利用停止・企業の無効なら失敗（理由は
     * {@link AuthenticationRejection} の値）。メールを送ったかは記録しない（通知の送信の記録で分かる）。
     */
    PASSWORD_RESET_REQUESTED,
    /**
     * password の再設定を終えた（Bolt 33a。US-18 AC8）。A-06 で新しい password を設定した。成功だけを記録する（方針の誤り・無効なリンクは
     * 記録しない）。
     */
    PASSWORD_RESET_COMPLETED
}

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
    /** 回復コードを 1 つ使った（A-02 で TOTP の代わりに使った。Bolt 31c。続けて LOGIN_SUCCEEDED を書く）。 */
    RECOVERY_CODE_USED,
    /** 5 回続けて失敗してロックした（Bolt 32。US-18 AC3）。 */
    ACCOUNT_LOCKED,
    /** ログアウトした。 */
    LOGOUT
}

package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 監査記録の操作（データモデル `audit_record.action`）。Bolt 14 はログインの成功・失敗とログアウト。
 */
public enum AuditAction {
    /** ログインに成功した。 */
    LOGIN_SUCCEEDED,
    /** ログインに失敗した。 */
    LOGIN_FAILED,
    /** ログアウトした。 */
    LOGOUT
}

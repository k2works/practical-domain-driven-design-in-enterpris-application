package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 利用者の利用状態（データモデル `app_user.status`）。利用停止の操作は利用者の管理（US-16）で作る。
 */
public enum UserStatus {
    /** 利用中。 */
    ACTIVE,
    /** 利用停止。認証できない（IA-INV-09）。 */
    SUSPENDED
}

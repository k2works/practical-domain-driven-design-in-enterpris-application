package com.example.cargotracker.identity.domain.model.valueobjects;

/**
 * 利用者の利用状態（データモデル `app_user.status`）。システム管理者が S-20 で停止し再開する（Bolt 34d。US-16 AC5）。
 */
public enum UserStatus {
    /** 利用中。 */
    ACTIVE,
    /** 利用停止。認証できない（IA-INV-09）。 */
    SUSPENDED
}

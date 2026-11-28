package com.example.cargotracker.shared.domain;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 役割。パイロットの固定の 8 役割（BR-15、IA-INV-01）。
 * ほかのコンテキストが認証された利用者の役割を知るため、共有カーネルに置く（ADR-012）。
 */
@ValueObject
public enum Role {
    /** 荷主担当者。 */
    SHIPPER("荷主担当者"),
    /** 営業担当者。 */
    SALES("営業担当者"),
    /** 経路設計者。 */
    ROUTE_DESIGNER("経路設計者"),
    /** 追跡管理者。 */
    TRACKING_MANAGER("追跡管理者"),
    /** データ責任者。 */
    DATA_STEWARD("データ責任者"),
    /** カスタマーサポート。 */
    CUSTOMER_SUPPORT("カスタマーサポート"),
    /** システム管理者。 */
    SYSTEM_ADMIN("システム管理者"),
    /** 監査担当者。 */
    AUDITOR("監査担当者");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す役割の名前。 */
    public String displayName() {
        return displayName;
    }
}

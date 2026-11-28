package com.example.cargotracker.tracking.domain.model.valueobjects;

/** 有人案件の優先度（Bolt 40-4。SVC-01・02）。 */
public enum ServicePriority {
    /** 高（輸送中の例外、外部停止、予約サガの失敗など）。 */
    HIGH,
    /** 通常（顧客照会、変更・取消しなど）。 */
    NORMAL
}

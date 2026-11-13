package com.example.cargotracker.tracking.domain.model.valueobjects;

/** 有人案件の種類（Bolt 40-5。表 {@code service_case.kind} の値）。 */
public enum ServiceCaseKind {
    /** 顧客照会。 */
    CUSTOMER_INQUIRY,
    /** 見積り相談。 */
    QUOTATION_CONSULTATION,
    /** 変更・取消し。 */
    AMENDMENT,
    /** 経路の再設計。 */
    REDESIGN,
    /** 外部停止。復旧照合が必須。 */
    EXTERNAL_OUTAGE,
    /** 追跡が実績にしなかった DE-13（登録拒否）。 */
    REJECTED_MILESTONE,
    /** 予約サガの失敗。 */
    SAGA_FAILURE
}

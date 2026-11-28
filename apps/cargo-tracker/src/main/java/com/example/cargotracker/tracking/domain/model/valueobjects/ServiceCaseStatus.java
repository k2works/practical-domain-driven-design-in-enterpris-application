package com.example.cargotracker.tracking.domain.model.valueobjects;

/** 有人案件の状態（Bolt 40-5。表 {@code service_case.status} の値。domain_model の状態遷移）。 */
public enum ServiceCaseStatus {
    /** 受付済み（担当者が受領する前）。 */
    RECEIVED,
    /** 上位対応中（受領期限を超過して上位の運用責任者へ回した）。 */
    ESCALATED,
    /** 対応中。 */
    IN_PROGRESS,
    /** 回答済み。 */
    ANSWERED,
    /** 終結。 */
    CLOSED,
    /** 重複終了。 */
    DUPLICATE_CLOSED
}

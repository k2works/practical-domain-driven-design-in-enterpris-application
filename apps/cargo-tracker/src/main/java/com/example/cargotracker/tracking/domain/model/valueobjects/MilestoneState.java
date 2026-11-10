package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 実績の状態。現在状態の導出に入れるかを決める（ドメインモデルの主要実績の状態。Bolt 26b）。追跡管理者が登録した実績は採用。
 * 下書きと保持のみは外部原本の取込（DE-13。Bolt 39g）で作り、確認中は時系列の矛盾（39gc）で使う。
 */
public enum MilestoneState {
    /** 下書き。 */
    DRAFT,
    /** 採用。 */
    ADOPTED,
    /** 確認中（T-INV-03）。 */
    UNDER_REVIEW,
    /** 保持のみ（T-INV-05）。 */
    RETAINED_ONLY
}

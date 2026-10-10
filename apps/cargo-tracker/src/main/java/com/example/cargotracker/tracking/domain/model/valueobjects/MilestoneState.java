package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 実績の状態。現在状態の導出に入れるかを決める（ドメインモデルの主要実績の状態。Bolt 26b）。追跡管理者が登録した実績は採用。
 * 下書きは外部原本の取込（W7）、確認中と保持のみは時系列の矛盾と完了後の実績（W7）で使う。
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

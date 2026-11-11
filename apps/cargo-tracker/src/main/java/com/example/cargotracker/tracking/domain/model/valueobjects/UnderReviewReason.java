package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 確認中の理由。追跡の現在状態が確認中になった理由（T-INV-03。Bolt 39gc）。表の {@code tracking_record.under_review_reason} に区分値で残す。
 * 矛盾・鮮度超過による確認中は、判定を作る Bolt で値を足す（W7 CP7 の境界）。
 */
@ValueObject
public enum UnderReviewReason {
    /** 時系列に反する実績がある（【業務の判断】(ag)）。 */
    OUT_OF_SEQUENCE
}

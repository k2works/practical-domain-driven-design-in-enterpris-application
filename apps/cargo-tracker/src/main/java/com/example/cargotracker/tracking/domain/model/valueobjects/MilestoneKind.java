package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 実績の種類。主要実績が表す事実（ドメインモデルの主要実績の種類。Bolt 26b）。積替港での到着は積替、積替港からの出発は出発として
 * 登録する（場所では判定しない）。
 */
public enum MilestoneKind {
    /** 集荷。 */
    PICKUP,
    /** 搬入（出発地）。 */
    RECEIPT_AT_ORIGIN,
    /** 出発（積替港からの出発を含む）。 */
    DEPARTURE,
    /** 積替（積替港到着）。 */
    TRANSSHIPMENT,
    /** 到着（目的地）。 */
    ARRIVAL,
    /** 引渡し。 */
    DELIVERY
}

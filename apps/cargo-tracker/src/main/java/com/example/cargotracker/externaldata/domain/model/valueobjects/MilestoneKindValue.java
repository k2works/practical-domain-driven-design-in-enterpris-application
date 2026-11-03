package com.example.cargotracker.externaldata.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;

/**
 * 実績の種類の値。主要実績の値の種類（Bolt 39b2b）。追跡の実績の種類（{@code MilestoneKind}）の写しで、外部データは追跡の型を参照しない
 * （モジュールの規則）。写しを守るテストで値の集合の一致を確かめる。
 */
@ValueObject
public enum MilestoneKindValue {
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

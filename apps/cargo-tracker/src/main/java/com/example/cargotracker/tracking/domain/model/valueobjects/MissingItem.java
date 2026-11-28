package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 有人案件の起票・終結で不足する項目（Bolt 40-5。SC-INV-01・02）。呼び手が入力を保持して不足を示せるよう、業務の拒否は例外でなくこの値で返す。
 */
public enum MissingItem {
    /** 原因・照会内容。 */
    CAUSE,
    /** 担当部門。 */
    DEPARTMENT,
    /** 荷主企業。 */
    SHIPPER_COMPANY,
    /** 予約。 */
    BOOKING,
    /** 追跡番号。 */
    TRACKING_NUMBER,
    /** 予約か追跡番号のどちらか。 */
    BOOKING_OR_TRACKING,
    /** 旧版。 */
    OLD_VERSION,
    /** 新版。 */
    NEW_VERSION,
    /** 手動入力の出典。 */
    MANUAL_SOURCE,
    /** 参照した出典（1 つ以上）。 */
    SOURCES,
    /** 復旧照合の結果（終結の前に要る）。 */
    RECONCILIATION_RESULT
}

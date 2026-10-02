package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 貨物種別。MVP は一般だけを受け付ける（BR-03）。値の名前はデータモデルの値と同じにする。
 */
public enum CargoCategory {
    /** 一般。 */
    GENERAL,
    /** 危険物。 */
    DANGEROUS,
    /** 冷凍。 */
    REEFER,
    /** その他特殊。 */
    OTHER_SPECIAL
}

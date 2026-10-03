package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 書類の種類。値の名前はデータモデルの値と同じにする（2026-10-03 の D-20。Bolt 7）。
 */
public enum DocumentType {
    /** 商業送り状（1 つの版に 1 件まで）。 */
    COMMERCIAL_INVOICE,
    /** 梱包明細（1 つの版に 1 件まで）。 */
    PACKING_LIST,
    /** その他の書類（1 つの版に 3 件まで）。 */
    OTHER
}

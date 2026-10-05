package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 書類の種類。値の名前はデータモデルの値と同じにする（2026-10-03 の D-20。Bolt 7）。
 * 1 つの版に持てる件数の上限（Q-INV-16）を種類ごとに持つ。合計は 5 件になる。
 */
public enum DocumentType {
    /** 商業送り状（1 つの版に 1 件まで）。 */
    COMMERCIAL_INVOICE(1),
    /** 梱包明細（1 つの版に 1 件まで）。 */
    PACKING_LIST(1),
    /** その他の書類（1 つの版に 3 件まで）。 */
    OTHER(3);

    private final int maxPerVersion;

    DocumentType(int maxPerVersion) {
        this.maxPerVersion = maxPerVersion;
    }

    /** 1 つの版に持てる件数の上限。 */
    public int maxPerVersion() {
        return maxPerVersion;
    }
}

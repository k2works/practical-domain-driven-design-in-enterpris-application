package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 荷姿。貨物の梱包の形（2026-10-02 に承認した仮の値。業務責任者の確認で見直す）。値の名前はデータモデルの値と同じにする。宣言の順が荷主の画面の選択肢の順になる（Bolt 29c）。
 */
public enum PackageType {
    /** パレット。 */
    PALLET,
    /** カートン。 */
    CARTON,
    /** クレート。 */
    CRATE,
    /** その他。 */
    OTHER
}

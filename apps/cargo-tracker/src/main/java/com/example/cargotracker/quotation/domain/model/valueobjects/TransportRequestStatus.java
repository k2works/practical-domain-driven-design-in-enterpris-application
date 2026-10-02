package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 輸送要求の状態。値の名前はデータモデルの状態の値と同じにする。
 */
public enum TransportRequestStatus {
    /** 下書き（差戻しの後。下書きの保存は R1.0）。 */
    DRAFT,
    /** 審査中。 */
    UNDER_REVIEW,
    /** 見積り作成中（審査を確定した後）。 */
    QUOTING
}

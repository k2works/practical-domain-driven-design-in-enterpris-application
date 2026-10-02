package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 審査の判断。値の名前はデータモデルの値と同じにする。
 */
public enum ReviewDecision {
    /** 充足（審査を確定し、見積りの作成へ進める）。 */
    APPROVED,
    /** 差戻し（下書きに戻す）。 */
    SENT_BACK
}

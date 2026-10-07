package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 除外理由の区分。値の名前はデータモデルの `exclusion_reason.reason_code` と同じにする（R-INV-02）。
 * R0.1（Bolt 17）は期限超過・接続不足・接続できないを判定する。貨物種別は判定せず、情報不足は AC4（W6）で判定する。
 */
public enum ExclusionReasonCode {
    /** 期限超過（到着予定が希望到着期限より後）。 */
    DEADLINE_EXCEEDED,
    /** 接続不足（接続時間が必要最小接続時間未満）。 */
    CONNECTION_TOO_SHORT,
    /** 貨物種別に対応しない。 */
    CARGO_NOT_SUPPORTED,
    /** 接続できない（積替えの港に有効な接続時間規則がない）。 */
    NOT_CONNECTABLE,
    /** 情報不足（外部情報源の停止中）。 */
    INFO_INSUFFICIENT
}

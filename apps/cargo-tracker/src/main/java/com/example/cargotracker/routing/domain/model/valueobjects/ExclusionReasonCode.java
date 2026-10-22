package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 除外理由の区分。値の名前はデータモデルの `exclusion_reason.reason_code` と同じにする（R-INV-02）。
 * R0.1（Bolt 17）は期限超過・接続不足・接続できないを判定する。貨物種別は判定しない。情報不足は Bolt 37b2（US-06 AC4）から情報鮮度の判定で判定する。
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
    /** 情報不足（航海の取得時刻から許容時間を過ぎてから算出した。R-INV-09、D-89）。 */
    INFO_INSUFFICIENT
}

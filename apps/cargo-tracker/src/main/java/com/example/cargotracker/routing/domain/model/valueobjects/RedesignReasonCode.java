package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 再設計要の理由の区分。確定した経路版を外部情報の更新で評価して再設計要にした不適合の区分（US-07 AC4、R-INV-07。Bolt 37d）。
 * 期限超過・接続不足・接続できないは除外理由の区分（{@link ExclusionReasonCode}）と同じ名前にし、制約適合判定の理由から写す。
 * 値の名前は保存（`route_version.redesign_reasons`）と DE-06 の理由の語彙になる。
 */
public enum RedesignReasonCode {
    /** 期限超過（今の予定で到着予定が希望到着期限より後）。 */
    DEADLINE_EXCEEDED,
    /** 接続不足（今の予定で接続時間が必要最小接続時間未満）。 */
    CONNECTION_TOO_SHORT,
    /** 接続できない（今の予定の接続の時刻に有効な接続時間規則がない）。 */
    NOT_CONNECTABLE,
    /** 航海を引き直せない（区間の航海がなくなった、または区間の積地・揚地に寄らなくなった）。 */
    VOYAGE_UNAVAILABLE
}

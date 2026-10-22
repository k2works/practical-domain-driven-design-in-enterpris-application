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
    /**
     * 航海を引き直せない（区間の航海がなくなった、区間の積地・揚地に寄らなくなった、寄る順が逆になった、出発予定・到着予定がない、
     * 到着が出発より後でない、積地か揚地に 2 度寄って確定した寄港が分からない）。画面の語は「航海の予定が使えない」。
     */
    VOYAGE_UNAVAILABLE;

    /**
     * 制約適合判定の理由の区分を写す（Bolt 37d2）。
     *
     * @throws IllegalStateException 貨物種別・情報不足（評価は貨物種別と鮮度を見ないので出ない。出たら評価の前提が崩れている。
     *     制約適合判定が貨物種別を判定するようになったら、写しを見直す）
     */
    public static RedesignReasonCode from(ExclusionReasonCode code) {
        return switch (code) {
            case DEADLINE_EXCEEDED -> DEADLINE_EXCEEDED;
            case CONNECTION_TOO_SHORT -> CONNECTION_TOO_SHORT;
            case NOT_CONNECTABLE -> NOT_CONNECTABLE;
            case CARGO_NOT_SUPPORTED, INFO_INSUFFICIENT -> throw new IllegalStateException("評価に出ない除外理由の区分です: " + code);
        };
    }
}

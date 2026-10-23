package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 経路の確定を拒否した理由（US-07 AC2、R-INV-03〜06。Bolt 19。情報版の更新と情報不足は US-07 AC3。Bolt 37c）。
 */
public enum RouteConfirmationRejectionReason {
    /** 承認者が経路設計者の役割を持たない。 */
    NOT_ROUTE_DESIGNER,
    /** 確定できない経路版の状態（作成中、確定済み）。 */
    NOT_CONFIRMABLE_STATE,
    /** 経路版にない候補番号。 */
    CANDIDATE_NOT_FOUND,
    /** 除外の候補。 */
    CANDIDATE_EXCLUDED,
    /** 候補を算出した後に、区間の航海の情報版が更新された（航海がなくなったときも。US-07 AC3、R-INV-03。Bolt 37c）。 */
    INFO_VERSION_CHANGED,
    /** 判断根拠がない（空・空白だけ）。 */
    RATIONALE_MISSING,
    /** 判断根拠が 4,000 文字を超える。 */
    RATIONALE_TOO_LONG,
    /** 最初の区間の出発予定が確定の時刻と同時刻または前。 */
    ALREADY_DEPARTED,
    /**
     * 確定の時刻に、区間の航海の情報が取得から許容時間を過ぎている（情報不足。R-INV-09、D-89。Bolt 37c）。算出では同じ判定で
     * 候補を除外する（除外理由の区分 {@code ExclusionReasonCode.INFO_INSUFFICIENT}）。
     */
    INFO_INSUFFICIENT,
    /** 確定のときに規則を読み直して判定し直すと不適合（規則は接続の時刻に有効なもの。D-90）。 */
    NO_LONGER_CONFORMING,
    /** 経路版が再設計要（確定し直せない。R-INV-06、US-07 AC5。Bolt 37e3）。 */
    ROUTE_REDESIGN_REQUIRED
}

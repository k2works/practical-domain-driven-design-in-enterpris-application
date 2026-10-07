package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 経路の確定を拒否した理由（US-07 AC2、R-INV-03〜06。Bolt 19）。
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
    /** 判断根拠がない（空・空白だけ）。 */
    RATIONALE_MISSING,
    /** 判断根拠が 4,000 文字を超える。 */
    RATIONALE_TOO_LONG,
    /** 最初の区間の出発予定が確定の時刻と同時刻または前。 */
    ALREADY_DEPARTED,
    /** 確定の時刻で判定し直すと不適合。 */
    NO_LONGER_CONFORMING
}

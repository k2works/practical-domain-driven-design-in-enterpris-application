package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 経路版の割当ての結果（R-INV-11。Bolt 20）。割り当てたか、同じ経路版をすでに割り当てていたか（冪等）か、割り当てなかった理由。
 */
public enum RouteAssignmentResult {
    /** 割り当てた（詳細設計依頼済み → 荷主承認待ち）。 */
    ASSIGNED,
    /** 同じ経路版をすでに割り当てている（DE-05 の再配信。何もしない）。 */
    ALREADY_ASSIGNED,
    /** 別の経路版をすでに割り当てている（R0.1 は経路版 1 だけ。経路版の差し替えは再設計（US-08）で決める）。 */
    ANOTHER_ROUTE_VERSION_ASSIGNED,
    /** 見積りが詳細設計依頼済みでない（作成中・承認待ち・提示済み）。 */
    NOT_ROUTING_REQUESTED,
    /** 見積りが置換済み・失効（記録）。 */
    RETIRED
}

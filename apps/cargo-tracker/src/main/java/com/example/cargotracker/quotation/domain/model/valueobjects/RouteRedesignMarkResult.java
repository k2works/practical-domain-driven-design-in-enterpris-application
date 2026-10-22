package com.example.cargotracker.quotation.domain.model.valueobjects;

/**
 * 割り当てた経路に再設計要の印を付けた結果（US-07 AC5、Q-INV-10。Bolt 37e）。付けたか、同じ経路版にすでに付いていたか（冪等）か、
 * 付けなかった理由。
 */
public enum RouteRedesignMarkResult {
    /** 付けた（再設計要にした時刻を写した。見積りの状態は変えない）。 */
    MARKED,
    /** 同じ経路版にすでに付いている（DE-06 の再配信。最初の時刻を残す）。 */
    ALREADY_MARKED,
    /** 経路を割り当てていない（詳細設計依頼済みより前など）。 */
    NO_ROUTE,
    /** 別の経路版（別の案件か別の経路版番号）を割り当てている。 */
    ANOTHER_ROUTE_VERSION
}

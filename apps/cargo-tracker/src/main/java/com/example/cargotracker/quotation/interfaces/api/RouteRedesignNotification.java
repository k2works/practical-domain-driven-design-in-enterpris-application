package com.example.cargotracker.quotation.interfaces.api;

/**
 * 再設計要の伝達（見積りの公開 API の操作。ADR-014、Q-INV-10、B-INV-05。Bolt 37e）。経路設計が経路版を再設計要にした（DE-06）ことを
 * 経路設計の listener が受けて呼び（Bolt 37e2）、その経路版を割り当てた見積り（再見積りでできた新しい見積りを含む）に再設計要の印を
 * 付ける。印のある見積りは荷主の承認と予約確定に使えない。冪等で、すでに付いていれば最初の時刻を残す。業務の理由で付けないときは
 * 例外にせず結果で返す。
 */
public interface RouteRedesignNotification {

    /**
     * 経路版を割り当てた見積りに再設計要の印を付ける。
     *
     * @param request 再設計要にした経路版（案件番号・経路版番号・再設計要にした時刻）
     * @return 伝達の結果
     */
    RouteRedesignNotificationReceipt notifyRedesignRequired(RouteRedesignNotificationRequest request);
}

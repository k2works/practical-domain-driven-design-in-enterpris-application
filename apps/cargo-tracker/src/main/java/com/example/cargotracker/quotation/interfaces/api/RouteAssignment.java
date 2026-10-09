package com.example.cargotracker.quotation.interfaces.api;

/**
 * 経路の割当て（見積りの公開 API の操作。ADR-014、R-INV-11。Bolt 20）。経路設計が経路を確定した（DE-05）ことを経路設計の
 * listener が受けて呼び、依頼元の見積りに確定した経路版を割り当てて荷主承認待ちにする。冪等で、同じ経路版の割当ては何もしない。
 * 業務の理由で割り当てないときは例外にせず結果で返す。
 */
public interface RouteAssignment {

    /**
     * 依頼元の見積りに経路版を割り当てる。
     *
     * @param request 割り当てる経路版（案件番号・経路版番号・確定の時刻・区間の写し）
     * @return 割当ての結果
     */
    RouteAssignmentReceipt assign(RouteAssignmentRequest request);
}

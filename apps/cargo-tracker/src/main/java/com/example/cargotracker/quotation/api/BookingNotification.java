package com.example.cargotracker.quotation.api;

/**
 * 予約確定済みの通知（見積りの公開 API の操作。ADR-014、DE-07。Bolt 23）。予約が本予約を確定した（DE-07）ことを予約の listener が
 * 受けて呼び、輸送要求を予約確定済みにする。冪等で、すでに予約確定済みなら何もしない。業務の理由で進めないときは例外にせず結果で返す。
 */
public interface BookingNotification {

    /**
     * 輸送要求を予約確定済みにする。
     *
     * @param request 輸送要求と版、見積り、予約
     * @return 受け付けた結果
     */
    BookingNotificationReceipt notifyBooked(BookingNotificationRequest request);
}

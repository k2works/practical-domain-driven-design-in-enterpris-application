package com.example.cargotracker.notification.interfaces.api;

/**
 * 荷主への業務の通知（通知の公開 API。ADR-017。US-22。Bolt 43）。見積りの提示・経路の承認・見積りの期限が近い・本予約の確定・追跡が確認中になった、を
 * 荷主担当者へメールで送る。本文は件名と画面への導線だけ。契機（イベントの ID）と宛先で冪等で、同じ組の 2 回目は送らずに {@code AlreadySent} を返す
 * （N-INV-02）。宛先は呼ぶ側が渡す（通知は identity に依存しない）。
 */
public interface ShipperNotificationMail {

    /**
     * 通知のメールを送り、送信の結果を記録する。
     *
     * @param request 送る内容
     * @return 送信の結果。送信に失敗したときも例外にせず {@code Failed} で返す
     */
    ShipperNotificationMailReceipt send(ShipperNotificationMailRequest request);
}

package com.example.cargotracker.notification.interfaces.api;

/**
 * 再設定のメール（通知の公開 API。ADR-017。Bolt 33）。password の再設定のリンクを含むメール 1 通を、呼ぶ側が渡した宛先へ送る。
 * 依頼の ID と宛先で冪等で、同じ組の 2 回目は送らずに {@code AlreadySent} を返す（N-INV-02）。通知は宛先を自分で解決しない
 * （identity に依存しない）。
 */
public interface PasswordResetMail {

    /**
     * password の再設定のメールを送り、送信の結果を記録する。本文とリンクは記録しない。
     *
     * @param request 送る内容
     * @return 送信の結果。送信に失敗したときも例外にせず {@code Failed} で返す
     */
    PasswordResetMailReceipt send(PasswordResetMailRequest request);
}

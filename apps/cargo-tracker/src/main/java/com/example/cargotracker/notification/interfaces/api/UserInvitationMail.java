package com.example.cargotracker.notification.interfaces.api;

/**
 * 利用者の招待のメール（通知の公開 API。ADR-017。Bolt 38b2b）。システム管理者が登録した利用者に、初回の password を設定するリンクを含む
 * メール 1 通を、呼ぶ側が渡した宛先へ送る。登録の ID と宛先で冪等で、同じ組の 2 回目は送らずに {@code AlreadySent} を返す（N-INV-02）。
 * 通知は宛先を自分で解決しない（identity に依存しない）。種類ごとの口か一般の口かは 38d で見直す。
 */
public interface UserInvitationMail {

    /**
     * 利用者の招待のメールを送り、送信の結果を記録する。本文とリンクは記録しない。
     *
     * @param request 送る内容
     * @return 送信の結果。送信に失敗したときも例外にせず {@code Failed} で返す
     */
    UserInvitationMailReceipt send(UserInvitationMailRequest request);
}

package com.example.cargotracker.notification.interfaces.api;

/**
 * 管理者による TOTP の登録の解除のお知らせ（通知の公開 API。ADR-017。Bolt 38d、US-18 AC9、SEC-10）。システム管理者が認証アプリの登録を
 * 解除したことを、本人の登録メールアドレスへ 1 通送る（リンクを持たない）。契機（監査記録の ID）と宛先で冪等で、同じ組の 2 回目は送らずに
 * {@code AlreadySent} を返す（N-INV-02）。通知は宛先を自分で解決しない（identity に依存しない）。
 */
public interface TotpResetMail {

    /**
     * 解除のお知らせを送り、送信の結果を記録する。本文は記録しない（監査の日時から再現できる）。
     *
     * @param request 送る内容
     * @return 送信の結果。送信に失敗したときも例外にせず {@code Failed} で返す
     */
    TotpResetMailReceipt send(TotpResetMailRequest request);
}

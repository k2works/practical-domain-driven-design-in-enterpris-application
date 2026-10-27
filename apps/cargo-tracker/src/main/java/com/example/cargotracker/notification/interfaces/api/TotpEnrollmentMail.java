package com.example.cargotracker.notification.interfaces.api;

/**
 * 認証アプリの登録と再登録のお知らせ（通知の公開 API。ADR-017。Bolt 38d、SEC-10）。A-07 で認証アプリを登録したとき、または回復コードの経路で
 * 登録し直したときに、本人の登録メールアドレスへ 1 通送る（リンクを持たない）。登録と再登録は本文の作りが同じなので 1 つの口で送り、要求が
 * 種類を持つ。契機（監査記録の ID）と宛先で冪等で、同じ組の 2 回目は送らずに {@code AlreadySent} を返す（N-INV-02）。
 */
public interface TotpEnrollmentMail {

    /**
     * 登録か再登録のお知らせを送り、送信の結果を記録する。本文は記録しない（監査の日時から再現できる）。
     *
     * @param request 送る内容
     * @return 送信の結果。送信に失敗したときも例外にせず {@code Failed} で返す
     */
    TotpEnrollmentMailReceipt send(TotpEnrollmentMailRequest request);
}

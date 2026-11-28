package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.SessionId;

/**
 * 利用者の session の失効。利用者のログイン中の session を消すポート（SEC-08。Bolt 33a。今の session を残して消すのは Bolt 33b の
 * 認証アプリの再登録）。実装は
 * {@code identity.infrastructure.security} の Spring Session の主体の名前（ログインのメールアドレス）の索引で探して消す。Spring Session は
 * 自分のトランザクションで動くので、呼ぶ側は確定の後に呼ぶ。
 */
public interface UserSessionRevoker {

    /**
     * 利用者の session をすべて消す。
     *
     * @param email ログインのメールアドレス（session の主体の名前）
     * @return 消した数
     */
    int revokeAll(EmailAddress email);

    /**
     * 利用者の session のうち、残す session のほかをすべて消す（Bolt 33b。認証アプリを登録し直した session は残す）。
     *
     * @param email ログインのメールアドレス（session の主体の名前）
     * @param keep 残す session の ID（Bolt 33e から値）
     * @return 消した数
     */
    int revokeOthers(EmailAddress email, SessionId keep);
}

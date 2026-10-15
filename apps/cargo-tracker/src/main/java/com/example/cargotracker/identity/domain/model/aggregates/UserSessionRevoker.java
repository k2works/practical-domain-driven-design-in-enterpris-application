package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;

/**
 * 利用者の session の失効。利用者のログイン中の session をすべて消すポート（SEC-08。Bolt 33a）。実装は
 * {@code identity.infrastructure.security} の Spring Session の主体の名前（ログインのメールアドレス）の索引で探して消す。Spring Session は
 * 自分のトランザクションで動くので、呼ぶ側は確定の後に呼ぶ。
 */
@FunctionalInterface
public interface UserSessionRevoker {

    /**
     * 利用者の session をすべて消す。
     *
     * @param email ログインのメールアドレス（session の主体の名前）
     * @return 消した数
     */
    int revokeAll(EmailAddress email);
}

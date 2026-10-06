package com.example.cargotracker.identity.devlogin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 開発環境だけのログインの入力済み（2026-10-06 の人の決定、ADR-011 の決定 3、ADR-012）。dev の設定にだけ値を書く。
 * 既定では空で、入力済みにしない。
 *
 * @param email A-01 に入れるメールアドレス（開発用の利用者）
 * @param password A-01 に入れる password（開発用の固定の値。本物の秘密を使わない）
 */
@ConfigurationProperties("cargotracker.dev-login")
public record DevLoginProperties(String email, String password) {

    /** 値が 1 つでも設定されているか。 */
    public boolean configured() {
        return email != null || password != null;
    }
}

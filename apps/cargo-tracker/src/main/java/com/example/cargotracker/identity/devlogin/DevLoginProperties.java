package com.example.cargotracker.identity.devlogin;

import com.example.cargotracker.shared.domain.Role;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 開発環境だけのログインの開発用の利用者（2026-10-06 の人の決定、ADR-011 の決定 3、ADR-012）。dev の設定にだけ値を書く。
 * 既定では空で、ログインの画面を入力済みにせず、利用者を選ぶボタンも出さない。
 *
 * @param accounts 開発用の利用者（最初の利用者で A-01 を入力済みにする）
 */
@ConfigurationProperties("cargotracker.dev-login")
public record DevLoginProperties(List<Account> accounts) {

    public DevLoginProperties {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    /** 値が 1 つでも設定されているか。 */
    public boolean configured() {
        return !accounts.isEmpty();
    }

    /**
     * 開発用の利用者 1 人（db/dev-data の利用者と同じ値）。
     *
     * @param role 役割（ボタンの名前に使う）
     * @param email メールアドレス
     * @param password password（開発用の固定の値。本物の秘密を使わない）
     */
    public record Account(Role role, String email, String password) {}
}

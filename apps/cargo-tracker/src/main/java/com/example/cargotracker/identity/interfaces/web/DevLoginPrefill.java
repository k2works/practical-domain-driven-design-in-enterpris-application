package com.example.cargotracker.identity.interfaces.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 開発環境のログインの入力済み（守りの 1 層目）。dev プロファイルでだけ部品になり、ほかの環境ではこの部品自体がない。
 * 設定の値が誤ってほかの環境に入っても、部品がなければ画面は入力済みにならない。
 */
@Component
@Profile("dev")
public class DevLoginPrefill {

    private final DevLoginProperties properties;

    public DevLoginPrefill(DevLoginProperties properties) {
        this.properties = properties;
    }

    public String email() {
        return properties.email();
    }

    public String password() {
        return properties.password();
    }
}

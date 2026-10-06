package com.example.cargotracker.identity.interfaces.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ログイン（A-01）と再認証の案内（A-03）の画面（US-18 の password の段）。ログインの送信とログアウトは Spring Security が受ける。
 * ログインに失敗したときは、入れたメールアドレスを残し password を消す（UI 設計「A-01」）。
 * 開発環境（dev）だけ、開発用の利用者で入力済みにし、荷主担当者・営業担当者の開発用の利用者を選んでそのままログインできる
 * ボタンを出す（2026-10-06 の人の決定）。
 */
@Controller
public class LoginController {

    /** 入力欄に出すメールアドレスと password のモデルの名前。 */
    static final String USERNAME = "username";

    static final String PASSWORD = "password";

    /** 開発環境で選んでそのままログインできる開発用の利用者のモデルの名前。 */
    static final String DEV_ACCOUNTS = "devAccounts";

    /** dev プロファイルでだけある。ほかの環境では空で、画面は入力済みにならない（守りの 1 層目）。 */
    private final ObjectProvider<DevLoginPrefill> devLogin;

    public LoginController(ObjectProvider<DevLoginPrefill> devLogin) {
        this.devLogin = devLogin;
    }

    @GetMapping("/login")
    public String login(Model model) {
        devLogin.ifAvailable(prefill -> {
            if (!model.containsAttribute(USERNAME)) {
                model.addAttribute(USERNAME, prefill.email());
                model.addAttribute(PASSWORD, prefill.password());
            }
            model.addAttribute(DEV_ACCOUNTS, prefill.accounts());
        });
        return "identity/login";
    }

    @GetMapping("/session-expired")
    public String sessionExpired() {
        return "identity/session-expired";
    }
}

package com.example.cargotracker.identity.interfaces.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ログイン（A-01）と再認証の案内（A-03）の画面（US-18 の password の段）。ログインの送信とログアウトは Spring Security が受ける。
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "identity/login";
    }

    @GetMapping("/session-expired")
    public String sessionExpired() {
        return "identity/session-expired";
    }
}

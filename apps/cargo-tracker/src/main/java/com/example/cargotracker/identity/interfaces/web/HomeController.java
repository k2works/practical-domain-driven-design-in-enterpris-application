package com.example.cargotracker.identity.interfaces.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ルート（{@code /}）。ログインした利用者を役割のホームへ移す（UI 設計「ホーム」。Bolt 3 の画面の入口の一覧を置き換えた）。
 * 荷主担当者は C-02、それ以外は S-02。未認証なら Spring Security が A-01 へ移す。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(HttpServletRequest request) {
        return request.isUserInRole("SHIPPER")
                ? "redirect:/customer/transport-requests"
                : "redirect:/staff/transport-requests";
    }
}

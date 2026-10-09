package com.example.cargotracker.identity.interfaces.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * まだ作っていない画面の仮の画面（Bolt 14。開発戦略の序盤の手順 3「ナビゲーションの骨格」）。
 * 役割ごとのナビゲーションから開けるようにし、画面を作る Bolt でその画面のコントローラーに置き換える。
 * 権限は URL の領域（{@code /customer/**}・{@code /staff/**}）で Spring Security が確かめる。
 */
@Controller
public class PlaceholderController {

    /** 荷主担当者の準備中の画面（URL の名前と画面の名前）。 */
    private static final Map<String, String> CUSTOMER_SCREENS =
            Map.of("bookings", "予約", "tracking", "追跡の照会", "inquiries", "問い合わせ", "notifications", "通知");

    @GetMapping("/customer/{screen:bookings|tracking|inquiries|notifications}")
    public String customer(@PathVariable String screen, Model model) {
        return show("layout/customer", screen, CUSTOMER_SCREENS, model);
    }

    private static String show(String layout, String screen, Map<String, String> screens, Model model) {
        String name = screens.get(screen);
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("layout", layout);
        model.addAttribute("active", screen);
        model.addAttribute("screenName", name);
        return "identity/placeholder";
    }
}

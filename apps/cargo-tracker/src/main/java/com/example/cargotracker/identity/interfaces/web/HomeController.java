package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * ルート（{@code /}）。ログインした利用者を役割のホームへ移す（UI 設計「ホーム」。Bolt 3 の画面の入口の一覧を置き換えた）。
 * 役割とホームの対応はここだけに置く（ログインの後もここを通る。Bolt 14 レビュー）。経路設計者は Bolt 17 で足した。画面のまだない役割は、
 * 行き先が循環しないよう A-04 にする。その役割の画面を作る Bolt で対応を足す。未認証なら Spring Security が A-01 へ移す。
 */
@Controller
public class HomeController {

    /** 役割のホーム。上から順に、持っている役割で最初に当たるものを使う。 */
    private static final List<Map.Entry<Role, String>> HOMES = List.of(
            Map.entry(Role.SHIPPER, "/customer/transport-requests"),
            Map.entry(Role.SALES, "/staff/transport-requests"),
            Map.entry(Role.ROUTE_DESIGNER, "/staff/routing-cases"));

    @GetMapping("/")
    public String home(AuthenticatedActor actor) {
        return HOMES.stream()
                .filter(home -> actor.hasRole(home.getKey()))
                .findFirst()
                .map(home -> "redirect:" + home.getValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}

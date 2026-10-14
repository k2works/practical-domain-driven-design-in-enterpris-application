package com.example.cargotracker.identity.interfaces.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * session の延長（Bolt 32b、ACC-06。session の警告の「ログインを延長する」）。公開の URL にせず、認証・request ごとの利用者の
 * 確かめ・上限の判定・CSRF の今のフィルターをすべて通す。Spring Session がこの request で session の最終アクセスを新しくするので、
 * 204 を返すだけにする（画面を移らず、入力を消さない）。8 時間の上限は延ばさない。延長はログインでも権限の変更でもないので、
 * 監査記録は書かない（ADR-012）。
 */
@Controller
public class SessionController {

    /** 延長の URL。 */
    public static final String EXTEND_URL = "/session/extend";

    @PostMapping(EXTEND_URL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void extend() {
        // 本体はない。フィルターを通ったこと（session が生きていること）と Spring Session の最終アクセスの更新が延長になる
    }
}

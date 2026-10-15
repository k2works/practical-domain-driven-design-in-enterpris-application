package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 認証の入口と画面が共有する URL を、テンプレートに渡す（Bolt 33c）。テンプレートは URL を直書きせず、この値を参照する（Thymeleaf の
 * リンク式は静的なクラスを参照できないため、モデルで渡す）。
 */
@ControllerAdvice
public class SessionUrlsAdvice {

    /**
     * テンプレートが読む URL。
     *
     * @param reEnroll A-08 認証アプリの再登録
     * @param expired A-03 再認証の案内
     */
    public record SessionUrls(String reEnroll, String expired) {}

    private static final SessionUrls URLS = new SessionUrls(SessionKeys.RE_ENROLL_URL, SessionKeys.EXPIRED_URL);

    @ModelAttribute("sessionUrls")
    public SessionUrls sessionUrls() {
        return URLS;
    }
}

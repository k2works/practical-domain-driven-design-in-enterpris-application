package com.example.cargotracker.identity.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * ログインの後の行き先（UI 設計「A-01」）。ログインの前に開いた画面があればそこへ、なければルート（{@code /}）へ移す。
 * 役割のホームへの振り分けはルートのコントローラー（{@code HomeController}）の 1 か所で行う（Bolt 14 レビュー）。
 * 発行から 8 時間の上限のため、ログインの時刻を session に置く。
 */
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final Clock clock;

    public LoginSuccessHandler(Clock clock) {
        this.clock = clock;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        request.getSession().setAttribute(SessionLifetime.AUTHENTICATED_AT, clock.instant());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}

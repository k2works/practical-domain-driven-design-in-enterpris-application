package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.TotpVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * password の段の成功（UI 設計「A-01」、Bolt 31）。password を確かめた時刻を session に置き（password の段だけの session の上限の
 * 起点）、TOTP を登録していれば A-02、していなければ登録の案内へ移す。ログインの前に開いた画面は覚えたままにし、TOTP が通った後に
 * そこへ戻す（{@link TotpAuthenticationFilter}）。最長 8 時間の起点は TOTP が初めて通った時刻（ADR-011）。
 */
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    /** 登録の案内の URL（Bolt 31b で A-07 に置き換える）。 */
    public static final String ENROLL_URL = "/login/totp/enroll";

    private final TotpVerificationService totp;
    private final Clock clock;

    public LoginSuccessHandler(TotpVerificationService totp, Clock clock) {
        this.totp = totp;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        request.getSession().setAttribute(SessionLifetime.PASSWORD_VERIFIED_AT, clock.instant());
        boolean enrolled = authentication.getPrincipal() instanceof CargoUserDetails details
                && totp.isEnrolled(details.actor().userId());
        response.sendRedirect(request.getContextPath() + (enrolled ? TotpAuthenticationFilter.URL : ENROLL_URL));
    }
}

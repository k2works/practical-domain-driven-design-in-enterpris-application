package com.example.cargotracker.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * password の段の成功（UI 設計「A-01」、Bolt 31）。password を確かめた時刻を session に置き（password の段だけの session の上限の
 * 起点）、A-02 へ移す（未登録の利用者は A-02 の画面が A-07 へ送る。Bolt 31b）。ログインの前に開いた画面は覚えたままにし、TOTP が通った後に
 * そこへ戻す（{@link TotpAuthenticationFilter}）。最長 8 時間の起点は TOTP が初めて通った時刻（ADR-011）。
 */
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final Clock clock;

    public LoginSuccessHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        HttpSession session = request.getSession();
        session.setAttribute(SessionLifetime.PASSWORD_VERIFIED_AT, clock.instant());
        // 別の利用者の password で要素が合わさらなかった（TOTP の要素がない）なら、前の利用者の TOTP の時刻を残さない。
        // 残すと、この利用者の 8 時間を前の利用者の時刻から数えてしまう（Bolt 31 の開発レビュー）
        boolean totpCarried = authentication.getAuthorities().stream()
                .anyMatch(granted -> TotpAuthenticationToken.FACTOR_TOTP.equals(granted.getAuthority()));
        if (!totpCarried) {
            session.removeAttribute(SessionLifetime.AUTHENTICATED_AT);
        }
        // 登録の途中の値は、この password の段の利用者のものとして始め直す（前の利用者の暗号文と時刻を残さない。T-102、Bolt 31b）
        session.removeAttribute(SessionLifetime.ENROLLMENT_PENDING_SECRET);
        session.removeAttribute(SessionLifetime.ENROLLMENT_STARTED_AT);
        // 回復コードでログインした印も、前の利用者のものとして消す（別の利用者に A-08 を開かせない。T-102、Bolt 33b）
        session.removeAttribute(SessionLifetime.RECOVERY_LOGIN_USER);
        session.removeAttribute(SessionLifetime.RECOVERY_LOGIN_AT);
        // 登録の有無は見ない。A-02 の画面が未登録の利用者を A-07 へ送る（認証の入口から問い合わせのサービスを呼ばない。Bolt 31b）
        response.sendRedirect(request.getContextPath() + TotpAuthenticationFilter.URL);
    }
}

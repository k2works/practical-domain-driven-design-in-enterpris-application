package com.example.cargotracker.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * session の上限（US-18 AC5、SEC-03）。TOTP が初めて通った時刻から 8 時間、password の段だけの session は password を確かめた
 * 時刻から 5 分（Bolt 31）。起点の時刻を session に置き、request ごとにアプリケーションの時計と比べる。
 * 認証の処理より前に置く（Bolt 13 の Try T-40）。認証済みなのにログインの時刻がない session も失効させる。上限を過ぎたら session を捨てて業務データを出さずに A-03 へ移す。
 * 古い session のままのログインの送信も A-03 へ移す（上限を過ぎた session を、ログインの送信で延命させない）。
 */
public class SessionLifetimeFilter extends OncePerRequestFilter {

    private final Clock clock;

    public SessionLifetimeFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && expired(session)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + SessionLifetime.EXPIRED_URL);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 上限を過ぎたか（Bolt 31）。2 つの要素がそろった session は TOTP が初めて通った時刻から 8 時間、password の段だけの session は
     * password を確かめた時刻から 5 分。認証済みなのに起点の時刻がない session も失効として扱う（fail-closed。Bolt 14 レビュー）。
     * 起点を置かない認証の経路を足しても、上限が黙って外れないようにする。
     */
    private boolean expired(HttpSession session) {
        Instant now = clock.instant();
        if (session.getAttribute(SessionLifetime.AUTHENTICATED_AT) instanceof Instant at) {
            return !now.isBefore(at.plus(SessionLifetime.MAX_LIFETIME));
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        if (hasAuthority(authentication, TotpAuthenticationToken.FACTOR_TOTP)) {
            return true;
        }
        return !(session.getAttribute(SessionLifetime.PASSWORD_VERIFIED_AT) instanceof Instant verifiedAt)
                || !now.isBefore(verifiedAt.plus(SessionLifetime.PASSWORD_STAGE_LIFETIME));
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}

package com.example.cargotracker.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 発行から 8 時間の上限（US-18 AC5、SEC-03）。ログインの時刻を session に置き、request ごとにアプリケーションの時計と比べる。
 * 認証の処理より前に置く（Bolt 13 の Try T-40）。上限を過ぎたら session を捨てて業務データを出さずに A-03 へ移す。
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
        Object authenticatedAt = session == null ? null : session.getAttribute(SessionLifetime.AUTHENTICATED_AT);
        if (authenticatedAt instanceof Instant at && !clock.instant().isBefore(at.plus(SessionLifetime.MAX_LIFETIME))) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + SessionLifetime.EXPIRED_URL);
            return;
        }
        chain.doFilter(request, response);
    }
}

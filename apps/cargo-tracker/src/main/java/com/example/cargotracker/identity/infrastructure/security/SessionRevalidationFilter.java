package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.commandservices.SessionRevalidationService;
import com.example.cargotracker.identity.application.internal.commandservices.SessionStanding;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * request ごとの利用者の確かめ（Bolt 32c、US-18 AC6、IA-INV-04、ADR-012 の見直しの 3）。認証済みの主体
 * （{@link CargoUserDetails}。password の段だけの主体を含む）を持つ request で、アプリケーションのサービスに今の利用者・役割・
 * 企業を確かめさせ、結果を session と主体に写すだけにする（判定と監査はサービス）。
 *
 * <ul>
 *   <li>拒否（利用停止・無効な企業・利用者なし）なら、session を捨て、セキュリティの文脈を消し、業務データを出さずに A-03 へ移す
 *   <li>拒否でなければ、今の役割・表示名・企業名で主体を作り直し、今の認証の結果を {@code toBuilder()} で主体と {@code ROLE_} の
 *       権限だけ替える（要素の権限は残す）。新しい文脈に入れ、session には保存しない（毎回作り直すので古い役割は戻らない）
 *   <li>読み出し・監査の誤り、役割が 0 件の利用者は、session を捨ててから投げ直し 500 にする（fail-closed）
 * </ul>
 *
 * <p>{@link SessionLifetimeFilter} の直後、認証のフィルター（password・TOTP）より前に置く。期限の切れた session は先に A-03 へ
 * 送られ、ここでは読まない。止められた利用者の A-02・A-07 の送信も、認証の前に A-03 になる。
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(SessionRevalidationFilter.class);
    private static final String ROLE_PREFIX = "ROLE_";

    private final SessionRevalidationService service;

    public SessionRevalidationFilter(SessionRevalidationService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null
                || !current.isAuthenticated()
                || !(current.getPrincipal() instanceof CargoUserDetails principal)) {
            chain.doFilter(request, response);
            return;
        }
        AuthenticatedActor sessionActor = principal.actor();
        SessionStanding standing;
        try {
            standing = service.revalidate(sessionActor.userId(), sessionActor.companyId());
        } catch (RuntimeException e) {
            LOG.error(
                    "request ごとの利用者の確かめに失敗した。session を捨てる（利用者 {}）",
                    sessionActor.userId().value(),
                    e);
            discard(request);
            throw e;
        }
        switch (standing) {
            case SessionStanding.Revoked _ -> {
                discard(request);
                response.sendRedirect(request.getContextPath() + SessionLifetime.EXPIRED_URL);
            }
            case SessionStanding.Current now -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(replace(current, now));
                SecurityContextHolder.setContext(context);
                chain.doFilter(request, response);
            }
        }
    }

    /** 主体と ROLE_ の権限だけを今の値に替える。要素の権限・認証の型・認証済みの印は残す（特性テストで固定した）。 */
    private static Authentication replace(Authentication current, SessionStanding.Current now) {
        CargoUserDetails principal =
                new CargoUserDetails(now.actor(), now.email().value(), true, null);
        return current.toBuilder()
                .principal(principal)
                .authorities(authorities -> {
                    authorities.removeIf(SessionRevalidationFilter::isRole);
                    authorities.addAll(principal.getAuthorities());
                })
                .build();
    }

    private static boolean isRole(GrantedAuthority granted) {
        String authority = granted.getAuthority();
        return authority != null && authority.startsWith(ROLE_PREFIX);
    }

    private static void discard(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}

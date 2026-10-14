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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * request ごとの利用者の確かめ（Bolt 32c、US-18 AC6、IA-INV-04、ADR-012 の見直しの 3）。認証済みの主体
 * （{@link CargoUserDetails}。password の段だけの主体を含む）を持つ request で、アプリケーションのサービスに今の利用者・役割・
 * 企業を確かめさせ、結果を session と主体に写すだけにする（判定と監査はサービス）。
 *
 * <ul>
 *   <li>拒否（利用停止・無効な企業・利用者なし）なら、session を捨て、セキュリティの文脈を消し、業務データを出さずに A-03 へ移す
 *   <li>拒否でなければ、今の役割・表示名・企業名で主体を作り直し、今の認証の結果を {@code toBuilder()} で主体と {@code ROLE_} の
 *       権限だけ替える（要素の権限は残す）。新しい文脈に入れ、このフィルターは session に保存しない（毎回作り直すので古い役割は
 *       戻らない。A-02・A-07 が通ったときは、後ろの認証のフィルターが差し替えた後の主体で保存する）
 *   <li>読み出し・監査の誤り、役割が 0 件の利用者は、session を捨ててから投げ直し 500 にする（fail-closed）
 * </ul>
 *
 * <p>{@link SessionLifetimeFilter} の直後、認証のフィルター（password・TOTP）より前に置く。期限の切れた session は先に A-03 へ
 * 送られ、ここでは読まない。止められた利用者の A-02・A-07 の送信も、認証の前に A-03 になる。公開の URL（ログイン・再認証の案内・
 * エラー・静的な資源）では確かめない。ログアウトの送信は確かめるので、止められた利用者のログアウトは A-03 で終わり、監査記録は
 * {@code LOGOUT} でなく {@code SESSION_REVOKED} になる。
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

    /** 確かめに失敗したときの例外の文言（利用者 ID を足す）。 */
    static final String FAILURE_MESSAGE = "request ごとの利用者の確かめに失敗した。session を捨てた";

    private static final String ROLE_PREFIX = "ROLE_";

    private final SessionRevalidationService service;
    private final RequestMatcher publicRequests;

    /**
     * @param service 確かめのサービス
     * @param publicRequests 確かめない公開の URL（ログイン、再認証の案内、エラー、静的な資源）。1 画面の表示で静的な資源の数だけ
     *     読まず、止められた利用者の並行の資源の request で取消しを重ねて書かないため（Bolt 32c の開発レビュー）
     */
    public SessionRevalidationFilter(SessionRevalidationService service, RequestMatcher publicRequests) {
        this.service = service;
        this.publicRequests = publicRequests;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicRequests.matches(request);
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
            // 利用者 ID を文脈に足して投げ直す。エラーのログはコンテナが 500 にするときに残す（ここで残すと二重になる）
            IllegalStateException failure = new IllegalStateException(
                    FAILURE_MESSAGE + "（利用者 " + sessionActor.userId().value() + "）", e);
            try {
                discard(request);
            } catch (RuntimeException discardFailure) {
                // session の行を消せなくても、元の誤りを投げ直す（原因を失わない）
                failure.addSuppressed(discardFailure);
            }
            throw failure;
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

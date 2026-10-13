package com.example.cargotracker.identity.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * A-02 の送信（{@code POST /login/totp}）で TOTP の要素を認証する（Bolt 31、US-18 AC1、ADR-011）。password の要素がそろった
 * session でだけ受け付ける。mfaEnabled を有効にし、認証の結果（{@code FACTOR_TOTP}）を同じ利用者の password の要素の権限に
 * 合わせる。成功したら session の ID を変え CSRF のトークンを作り直す（権限が上がる段の固定化を防ぐ。T-40）。
 * 最長 8 時間の起点（{@link SessionLifetime#AUTHENTICATED_AT}）は、その session で初めて TOTP が通ったときだけ置く（送り直しで
 * 延ばさせない）。誤り・再利用は password の要素を捨てて A-01 に戻す（UI 設計「A-02」。1 つの session で TOTP を試し続けさせない）。
 */
public class TotpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    /** A-02 の URL。 */
    public static final String URL = "/login/totp";

    /** TOTP の誤りで戻す A-01 の URL（UI 設計「A-02」の文言を出す）。 */
    public static final String FAILURE_URL = "/login?totp-error";

    public TotpAuthenticationFilter(AuthenticationManager authenticationManager, Clock clock) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, URL), authenticationManager);
        setMfaEnabled(true);
        setSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository()));
        setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository()))));
        setAuthenticationSuccessHandler(new FirstTotpSuccessHandler(clock));
        setAuthenticationFailureHandler(new DiscardPasswordFactorFailureHandler());
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean passwordVerified = current != null
                && current.isAuthenticated()
                && current.getPrincipal() instanceof CargoUserDetails
                && current.getAuthorities().stream()
                        .anyMatch(authority ->
                                FactorGrantedAuthority.PASSWORD_AUTHORITY.equals(authority.getAuthority()));
        if (!passwordVerified) {
            throw new InsufficientAuthenticationException("password の要素がない");
        }
        String code = request.getParameter("code");
        return getAuthenticationManager()
                .authenticate(TotpAuthenticationToken.unauthenticated(
                        current.getPrincipal(), code == null ? "" : code.strip()));
    }

    /** 初めて TOTP が通った時刻を置き、ログインの前に開いた画面か、ルート（役割のホームへ振り分ける）へ移す。 */
    static final class FirstTotpSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

        private final Clock clock;

        FirstTotpSuccessHandler(Clock clock) {
            this.clock = clock;
            setDefaultTargetUrl("/");
        }

        @Override
        public void onAuthenticationSuccess(
                HttpServletRequest request, HttpServletResponse response, Authentication authentication)
                throws ServletException, IOException {
            HttpSession session = request.getSession();
            if (session.getAttribute(SessionLifetime.AUTHENTICATED_AT) == null) {
                session.setAttribute(SessionLifetime.AUTHENTICATED_AT, clock.instant());
            }
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }

    /** password の要素を捨てて（session を捨てて）A-01 に戻す。 */
    static final class DiscardPasswordFactorFailureHandler implements AuthenticationFailureHandler {

        @Override
        public void onAuthenticationFailure(
                HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
                throws IOException {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + FAILURE_URL);
        }
    }
}

package spike.totp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * POST /login/totp で TOTP の要素を認証する。password の要素がそろった session でだけ受け付ける。
 * mfaEnabled を有効にし、認証の結果（FACTOR_TOTP）を、同じ利用者の password の要素の権限に合わせる（Spring Security 7）。
 * 成功したら session の ID を変え CSRF のトークンを作り直す（session の固定化を防ぐ。form login と同じ扱い）。
 * 本人確認の時刻はその session で初めて TOTP が通ったときだけ残し、session の最長 8 時間の起点にする（送り直しで延ばさせない）。
 */
public class TotpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    public static final String AUTHENTICATED_AT = "spike.totp.authenticatedAt";

    public TotpAuthenticationFilter(AuthenticationManager authenticationManager, Clock clock) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login/totp"), authenticationManager);
        setMfaEnabled(true);
        setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository()))));
        SimpleUrlAuthenticationSuccessHandler success = new SimpleUrlAuthenticationSuccessHandler("/staff") {
            @Override
            public void onAuthenticationSuccess(
                    HttpServletRequest request, HttpServletResponse response, Authentication authentication)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                if (request.getSession().getAttribute(AUTHENTICATED_AT) == null) {
                    request.getSession().setAttribute(AUTHENTICATED_AT, clock.instant());
                }
                super.onAuthenticationSuccess(request, response, authentication);
            }
        };
        setAuthenticationSuccessHandler(success);
        setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler("/login/totp?error"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean passwordVerified = current != null
                && current.isAuthenticated()
                && current.getAuthorities().stream()
                        .anyMatch(authority -> FactorGrantedAuthority.PASSWORD_AUTHORITY.equals(authority.getAuthority()));
        if (!passwordVerified) {
            throw new InsufficientAuthenticationException("password の要素がない");
        }
        String code = request.getParameter("code");
        return getAuthenticationManager()
                .authenticate(TotpAuthenticationToken.unauthenticated(current.getName(), code == null ? "" : code.strip()));
    }
}

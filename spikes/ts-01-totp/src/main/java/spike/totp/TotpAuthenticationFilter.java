package spike.totp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * POST /login/totp で TOTP の要素を認証する。password の要素がそろった session でだけ受け付ける。
 * mfaEnabled を有効にし、認証の結果（FACTOR_TOTP）を、同じ利用者の password の要素の権限に合わせる（Spring Security 7）。
 * 成功したら本人確認の時刻を session に残し、session の最長 8 時間の起点にする。
 */
public class TotpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    public static final String AUTHENTICATED_AT = "spike.totp.authenticatedAt";

    public TotpAuthenticationFilter(AuthenticationManager authenticationManager, Clock clock) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login/totp"), authenticationManager);
        setMfaEnabled(true);
        setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        SimpleUrlAuthenticationSuccessHandler success = new SimpleUrlAuthenticationSuccessHandler("/staff") {
            @Override
            public void onAuthenticationSuccess(
                    HttpServletRequest request, HttpServletResponse response, Authentication authentication)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                request.getSession().setAttribute(AUTHENTICATED_AT, clock.instant());
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

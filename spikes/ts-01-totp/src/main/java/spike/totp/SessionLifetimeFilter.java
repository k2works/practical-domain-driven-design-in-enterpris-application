package spike.totp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 本人確認から最長 8 時間の session（US-18 AC1）。無操作 30 分はサーブレットの session の期限（server.servlet.session.timeout）に任せ、
 * 最長の時間は Spring Security に既製の部品がないため、本人確認の時刻を session に残して request ごとに比べる。
 * 期限を過ぎたら session を捨てる。ログインの送信（POST /login）だけは新しい session で続けさせ、ほかはログインの画面へ導く。
 */
public class SessionLifetimeFilter extends OncePerRequestFilter {

    private static final Duration MAX_LIFETIME = Duration.ofHours(8);

    private final Clock clock;

    public SessionLifetimeFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object authenticatedAt = session == null ? null : session.getAttribute(TotpAuthenticationFilter.AUTHENTICATED_AT);
        if (authenticatedAt instanceof Instant at && !clock.instant().isBefore(at.plus(MAX_LIFETIME))) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            if (isLoginSubmission(request)) {
                chain.doFilter(request, response);
                return;
            }
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isLoginSubmission(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/login".equals(request.getServletPath());
    }
}

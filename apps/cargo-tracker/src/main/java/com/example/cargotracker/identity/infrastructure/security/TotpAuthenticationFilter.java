package com.example.cargotracker.identity.infrastructure.security;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.identity.application.internal.session.SessionPaths;
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
 * 最長 8 時間の起点（{@code SessionKeys.AUTHENTICATED_AT}）は、その session で初めて TOTP が通ったときだけ置く（送り直しで
 * 延ばさせない）。誤り・再利用は password の要素を捨てて A-01 に戻す（UI 設計「A-02」。1 つの session で TOTP を試し続けさせない）。
 * 回復コードの欄（{@code recoveryCode}）があれば回復コードの入力にし、{@link RecoveryCodeAuthenticationProvider} で確かめる。
 * 結果は TOTP と同じ要素の権限なので、session の ID の変更・8 時間の起点・失敗の扱いは同じ（Bolt 31c、ADR-011）。
 */
public class TotpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    /** A-02 の URL。 */
    public static final String URL = "/login/totp";

    /** A-07 認証アプリの登録の URL（GET・POST。password の要素だけで開ける。Bolt 31b）。 */
    public static final String ENROLL_URL = "/login/totp/enroll";

    /** A-02 の回復コードの欄の名前（Bolt 31c）。 */
    public static final String RECOVERY_CODE_PARAMETER = "recoveryCode";

    /** TOTP の誤りで戻す A-01 の URL（UI 設計「A-02」の文言を出す）。 */
    public static final String FAILURE_URL = "/login?totp-error";

    public TotpAuthenticationFilter(AuthenticationManager authenticationManager, Clock clock) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, URL), authenticationManager);
        setMfaEnabled(true);
        setSecurityContextRepository(new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository()));
        // formLogin と同じ既定（session ID の付け替えと、session に置く CSRF のトークンの作り直し）を組む。フィルターを作る時点では
        // 共有の戦略がまだ組まれていないため。CSRF のトークンの置き場所や session の管理を変えたら、ここも合わせる（統合テストで
        // 段ごとの session ID の変化と CSRF を見張る。Bolt 31 の開発レビュー）
        setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(new HttpSessionCsrfTokenRepository()))));
        setAuthenticationSuccessHandler(new FirstTotpSuccessHandler(clock));
        setAuthenticationFailureHandler(new DiscardPasswordFactorFailureHandler());
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null
                || !(current.getPrincipal() instanceof CargoUserDetails)
                || !AuthenticationFactors.passwordVerified(current)) {
            throw new InsufficientAuthenticationException("password の要素がない");
        }
        String code = request.getParameter("code");
        String recoveryCode = request.getParameter(RECOVERY_CODE_PARAMETER);
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            return getAuthenticationManager()
                    .authenticate(RecoveryCodeAuthenticationToken.unauthenticated(
                            current.getPrincipal(), recoveryCode, code != null && !code.isBlank()));
        }
        return getAuthenticationManager()
                .authenticate(TotpAuthenticationToken.unauthenticated(
                        current.getPrincipal(), code == null ? "" : code.strip()));
    }

    /**
     * 初めて TOTP が通った時刻を置き、ログインの前に開いた画面か、ルート（役割のホームへ振り分ける）へ移す。回復コードで通ったときは、
     * 回復コードでログインした印（利用者の ID）を置いて A-08 へ移す（覚えた要求は残し、A-08 の「後で登録する」・「続ける」が使う。
     * Bolt 33b、US-18 AC9。session の ID は認証の入口の {@code ChangeSessionIdAuthenticationStrategy} がすでに変えている）。
     */
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
            if (session.getAttribute(SessionKeys.AUTHENTICATED_AT) == null) {
                session.setAttribute(SessionKeys.AUTHENTICATED_AT, clock.instant());
            }
            // 登録を始めた時刻は、ログインを終えたら使わない（登録の直後の A-02 の案内を出さない。期限は要素で選ぶので変わらない。
            // Bolt 31c の確認ポイント 9）
            session.removeAttribute(SessionKeys.ENROLLMENT_STARTED_AT);
            // 経路は認証の結果のトークンが持つ（Bolt 33e。request の属性で伝えていたのをやめた）
            if (authentication instanceof TotpAuthenticationToken token
                    && token.path() == SecondFactorPath.RECOVERY_CODE
                    && authentication.getPrincipal() instanceof CargoUserDetails user) {
                session.setAttribute(
                        SessionKeys.RECOVERY_LOGIN_USER,
                        user.actor().userId().value().toString());
                session.setAttribute(SessionKeys.RECOVERY_LOGIN_AT, clock.instant());
                clearAuthenticationAttributes(request);
                response.sendRedirect(request.getContextPath() + SessionPaths.RE_ENROLL_URL);
                return;
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
            // 覚えていた要求（失効の前に開いていた画面）は新しい session に持ち越す（ログインし直した後に戻れるように。Bolt 32e）
            ExpiredSessionRequestSaver.invalidateCarryingSavedRequest(request);
            SecurityContextHolder.clearContext();
            // password の段を経ない送信には、認証コードの誤りの文言を出さない（Bolt 31 の開発レビュー）
            String target = exception instanceof InsufficientAuthenticationException ? "/login" : FAILURE_URL;
            response.sendRedirect(request.getContextPath() + target);
        }
    }
}

package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Spring Security の特性テスト（Bolt 13 のレビュー R-16、D-47、ADR-011 のコンプライアンス。Bolt 31d でスパイクから移した）。本体が頼る
 * 枠組みの既定の振る舞いを固定し、Spring Security の版を上げたときに変わったら落ちるようにする（T-108: どの部品がいつ呼ぶか）。
 */
class SpringSecurityCharacterizationTest {

    private static final String URL = "/characterization";

    /** {@code toBuilder()} を宣言しない認証の結果（比べるため）。 */
    private static final class PlainToken extends AbstractAuthenticationToken {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Object principal;

        PlainToken(Object principal, GrantedAuthority factor) {
            super(List.of(factor));
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }

    /** 決めた結果を返す認証のフィルター（本物の AbstractAuthenticationProcessingFilter）。 */
    private static final class FixedResultFilter extends AbstractAuthenticationProcessingFilter {
        private final Authentication result;

        FixedResultFilter(Authentication result) {
            super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, URL));
            this.result = result;
            setAuthenticationSuccessHandler((request, response, authentication) -> {});
        }

        @Override
        public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
            return result;
        }
    }

    @AfterEach
    void 文脈を消す() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication passwordStage() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "shipper",
                null,
                List.of(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));
    }

    private static MockHttpServletRequest postTo(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", URL);
        request.setSession(session);
        return request;
    }

    private static List<String> authoritiesAfter(AbstractAuthenticationProcessingFilter filter, MockHttpSession session)
            throws Exception {
        return authoritiesAfter(filter, postTo(session));
    }

    private static List<String> authoritiesAfter(
            AbstractAuthenticationProcessingFilter filter, MockHttpServletRequest request) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(passwordStage());
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    void mfaEnabledは結果の型がtoBuilderを持つときだけ前の要素の権限を合わせる() throws Exception {
        // ① TotpAuthenticationToken が toBuilder() を持つ理由（ADR-011 の決定 2、Bolt 13 のスパイク）
        GrantedAuthority totp = FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP);
        FixedResultFilter withBuilder = new FixedResultFilter(TotpAuthenticationToken.authenticated("shipper", totp));
        withBuilder.setMfaEnabled(true);
        FixedResultFilter withoutBuilder = new FixedResultFilter(new PlainToken("shipper", totp));
        withoutBuilder.setMfaEnabled(true);

        assertThat(authoritiesAfter(withBuilder, new MockHttpSession()))
                .contains(FactorGrantedAuthority.PASSWORD_AUTHORITY, TotpAuthenticationToken.FACTOR_TOTP);
        assertThat(authoritiesAfter(withoutBuilder, new MockHttpSession()))
                .contains(TotpAuthenticationToken.FACTOR_TOTP)
                .doesNotContain(FactorGrantedAuthority.PASSWORD_AUTHORITY);
    }

    @Test
    void 認証のフィルターの既定の置き場所はrequestの属性でsessionには残らない() throws Exception {
        // ② TotpAuthenticationFilter が session の置き場所を明示する理由（R-16 の「既定のリポジトリ」）。対照として session の置き場所を
        // 明示したフィルターと比べ、このテストが違いを捉えられることを示す
        GrantedAuthority totp = FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP);
        FixedResultFilter defaultRepository =
                new FixedResultFilter(TotpAuthenticationToken.authenticated("shipper", totp));
        FixedResultFilter sessionRepository =
                new FixedResultFilter(TotpAuthenticationToken.authenticated("shipper", totp));
        sessionRepository.setSecurityContextRepository(new HttpSessionSecurityContextRepository());

        MockHttpSession defaultSession = new MockHttpSession();
        MockHttpServletRequest defaultRequest = postTo(defaultSession);
        assertThat(authoritiesAfter(defaultRepository, defaultRequest))
                .as("フィルターが認証した")
                .contains(TotpAuthenticationToken.FACTOR_TOTP);
        assertThat(defaultRequest.getAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME))
                .as("既定の置き場所は request の属性")
                .isNotNull();
        assertThat(defaultSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .as("session には置かれない（次の request に残らない）")
                .isNull();

        MockHttpSession explicitSession = new MockHttpSession();
        authoritiesAfter(sessionRepository, postTo(explicitSession));
        assertThat(explicitSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .as("session の置き場所を明示すると session に置かれる")
                .isNotNull();
    }

    @Test
    void 要素の権限の発行時刻はシステムの時計で決まる() {
        // ③ fromAuthority の issuedAt はシステムの時計（Instant.now）で決まり、アプリケーションの Clock を受けない。最長 8 時間の判定に
        // issuedAt を使わず、session の時刻（アプリケーションの Clock）で数える理由
        Instant before = Instant.now();
        FactorGrantedAuthority factor = FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP);

        assertThat(factor.getIssuedAt()).isBetween(before, Instant.now().plus(Duration.ofSeconds(1)));
    }
}

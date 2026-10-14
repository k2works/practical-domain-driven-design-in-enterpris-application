package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
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

    // --- Bolt 32 のロックが頼る枠組みの振る舞い（T-108） ---

    private static DaoAuthenticationProvider daoProvider(UserDetailsService users, List<String> postChecks) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        provider.setPreAuthenticationChecks(user -> {});
        provider.setPostAuthenticationChecks(user -> postChecks.add(user.getUsername()));
        return provider;
    }

    private static UserDetailsService oneUser() {
        UserDetails user = User.withUsername("shipper")
                .password(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                        .encode("correct"))
                .roles("SHIPPER")
                .build();
        return username -> {
            if (!"shipper".equals(username)) {
                throw new UsernameNotFoundException("なし");
            }
            return User.withUserDetails(user).build();
        };
    }

    @Test
    void 照合の後の確認はpasswordが一致したときだけ呼ばれる() {
        // ロックを照合の後に確かめる理由（誤った password とロック中の応答の時間を変えない。AC2）
        List<String> postChecks = new ArrayList<>();
        DaoAuthenticationProvider provider = daoProvider(oneUser(), postChecks);
        Authentication wrong = UsernamePasswordAuthenticationToken.unauthenticated("shipper", "wrong");

        assertThatThrownBy(() -> provider.authenticate(wrong)).isInstanceOf(BadCredentialsException.class);
        assertThat(postChecks).as("誤った password では呼ばない").isEmpty();
        provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("shipper", "correct"));
        assertThat(postChecks).containsExactly("shipper");
    }

    @Test
    void ロックの例外の失敗のイベントはロックのイベントになる() {
        // 監査のリスナーがロック中の試行を数えない試行として写す拠り所（既定の例外とイベントの対応）
        List<Object> events = new ArrayList<>();
        DefaultAuthenticationEventPublisher publisher = new DefaultAuthenticationEventPublisher(events::add);

        publisher.publishAuthenticationFailure(
                new LockedException("ロック中"), UsernamePasswordAuthenticationToken.unauthenticated("shipper", "x"));

        assertThat(events).singleElement().isInstanceOf(AuthenticationFailureLockedEvent.class);
    }

    @Test
    void アカウントの状態の例外ではほかのproviderを試さない() {
        // TOTP の provider がロックで拒んだときに、ほかの provider で通らないこと
        AuthenticationProvider locked = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                throw new LockedException("ロック中");
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return true;
            }
        };
        AuthenticationProvider accepting = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                return UsernamePasswordAuthenticationToken.authenticated("shipper", null, List.of());
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return true;
            }
        };
        ProviderManager manager = new ProviderManager(locked, accepting);
        Authentication input = UsernamePasswordAuthenticationToken.unauthenticated("shipper", "x");

        assertThatThrownBy(() -> manager.authenticate(input)).isInstanceOf(LockedException.class);
    }

    @Test
    void 利用者の読み出しの例外は内部の認証の例外に包まれ失敗へ倒れる() {
        // password の段の読み出しの DB の誤りが 500 でなく /login?error になる理由（確認ポイント 8）
        DaoAuthenticationProvider provider = daoProvider(
                username -> {
                    throw new IllegalStateException("DB の誤り");
                },
                new ArrayList<>());
        Authentication input = UsernamePasswordAuthenticationToken.unauthenticated("shipper", "x");

        assertThatThrownBy(() -> provider.authenticate(input))
                .isInstanceOf(InternalAuthenticationServiceException.class);
    }

    @Test
    void 接頭辞が正しく中身の壊れたbcryptのハッシュは例外でなく一致しないになる() {
        // 確認ポイント 9: 読めない要素としては扱わず、利用者の誤りとして数えられることを受け入れる（壊すには DB への書き込みが要る）
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        assertThat(encoder.matches("1a2b-3c4d", "{bcrypt}not-a-bcrypt-hash")).isFalse();
    }

    @Test
    void 失敗のイベントは例外のクラス名の完全一致で選ばれ内部の認証の例外には既定のイベントがない() {
        // SecurityConfiguration が TOTP・回復コードの拒否と内部の認証の例外を対応表に足す理由（Bolt 31・32）
        List<Object> events = new ArrayList<>();
        DefaultAuthenticationEventPublisher publisher = new DefaultAuthenticationEventPublisher(events::add);

        publisher.publishAuthenticationFailure(
                new InternalAuthenticationServiceException("DB の誤り"),
                UsernamePasswordAuthenticationToken.unauthenticated("shipper", "x"));

        assertThat(events).as("親の型（AuthenticationServiceException）の対応は使われない").isEmpty();
    }

    // --- Bolt 32c の request ごとの確かめが頼る枠組みの振る舞い（T-114、T-108） ---

    private static List<String> authorityNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    /** 主体と ROLE_ の権限だけを替える（SessionRevalidationFilter と同じ替え方）。 */
    private static Authentication replacePrincipalAndRoles(Authentication current, Object principal, String role) {
        return current.toBuilder()
                .principal(principal)
                .authorities(authorities -> {
                    authorities.removeIf(granted -> granted.getAuthority().startsWith("ROLE_"));
                    authorities.add(new SimpleGrantedAuthority(role));
                })
                .build();
    }

    @Test
    void toBuilderで主体とROLEの権限だけを替えても認証の型と認証済みの印と要素の権限が残る() {
        // 2 つの要素がそろった主体（TotpAuthenticationToken）と password の段だけの主体（UsernamePasswordAuthenticationToken）の両方
        GrantedAuthority password = FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY);
        GrantedAuthority totp = FactorGrantedAuthority.fromAuthority(TotpAuthenticationToken.FACTOR_TOTP);
        Authentication twoFactors = TotpAuthenticationToken.authenticated("before", totp).toBuilder()
                .authorities(authorities -> {
                    authorities.add(password);
                    authorities.add(new SimpleGrantedAuthority("ROLE_SHIPPER"));
                })
                .build();
        Authentication passwordOnly = UsernamePasswordAuthenticationToken.authenticated(
                "before", null, List.of(password, new SimpleGrantedAuthority("ROLE_SHIPPER")));

        Authentication replacedTwo = replacePrincipalAndRoles(twoFactors, "after", "ROLE_SALES");
        Authentication replacedPassword = replacePrincipalAndRoles(passwordOnly, "after", "ROLE_SALES");

        assertThat(replacedTwo).isInstanceOf(TotpAuthenticationToken.class);
        assertThat(replacedTwo.isAuthenticated()).isTrue();
        assertThat(replacedTwo.getPrincipal()).isEqualTo("after");
        assertThat(authorityNames(replacedTwo))
                .containsExactlyInAnyOrder(
                        FactorGrantedAuthority.PASSWORD_AUTHORITY, TotpAuthenticationToken.FACTOR_TOTP, "ROLE_SALES");
        assertThat(List.<GrantedAuthority>copyOf(replacedTwo.getAuthorities()))
                .as("要素の権限は同じもの（発行時刻も同じ）")
                .contains(password, totp);
        assertThat(replacedPassword).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(replacedPassword.isAuthenticated()).isTrue();
        assertThat(replacedPassword.getPrincipal()).isEqualTo("after");
        assertThat(authorityNames(replacedPassword))
                .containsExactlyInAnyOrder(FactorGrantedAuthority.PASSWORD_AUTHORITY, "ROLE_SALES");
    }

    @Test
    void 新しい文脈に差し替えた主体は明示的に保存しなければsessionに残らない() throws Exception {
        // SecurityContextHolderFilter は request の終わりに文脈を保存しない（明示的な保存。Spring Security 6 から）。差し替えを request の
        // 中だけにできる理由。対照として、session から読んだ文脈そのものを書き換えると session の値も変わることを示す（新しい文脈を
        // 作る理由）
        Authentication stored = UsernamePasswordAuthenticationToken.authenticated(
                "before", null, List.of(new SimpleGrantedAuthority("ROLE_SHIPPER")));
        Authentication replaced = UsernamePasswordAuthenticationToken.authenticated(
                "after", null, List.of(new SimpleGrantedAuthority("ROLE_SALES")));
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        SecurityContextHolderFilter filter = new SecurityContextHolderFilter(repository);

        MockHttpSession newContextSession = sessionWith(stored);
        filter.doFilter(
                new MockHttpRequestWithSession(newContextSession), new MockHttpServletResponse(), (req, res) -> {
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(replaced);
                    SecurityContextHolder.setContext(context);
                });
        MockHttpSession mutatedSession = sessionWith(stored);
        filter.doFilter(
                new MockHttpRequestWithSession(mutatedSession),
                new MockHttpServletResponse(),
                (req, res) -> SecurityContextHolder.getContext().setAuthentication(replaced));

        assertThat(storedPrincipal(newContextSession))
                .as("新しい文脈は session に残らない")
                .isEqualTo("before");
        assertThat(storedPrincipal(mutatedSession))
                .as("読んだ文脈を書き換えると session の値が変わる")
                .isEqualTo("after");
    }

    private static MockHttpSession sessionWith(Authentication authentication) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private static Object storedPrincipal(MockHttpSession session) {
        SecurityContext context = (SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context.getAuthentication().getPrincipal();
    }

    /** session を持つ GET の request。 */
    private static final class MockHttpRequestWithSession extends MockHttpServletRequest {
        MockHttpRequestWithSession(MockHttpSession session) {
            super("GET", "/customer/home");
            setSession(session);
        }
    }
}

package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.identity.application.internal.session.SessionPaths;
import com.example.cargotracker.shared.acceptance.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 2 つの要素がそろった session の 8 時間（TOTP が初めて通った時刻から）と password の段だけの session の 5 分の上限の判定
 * （US-18 AC5、T-38 の 3 点。Bolt 31）。認証済みなのにログインの時刻がない session も失効として扱う
 * （fail-closed。Bolt 14 レビュー）。
 */
class SessionLifetimeFilterTest {

    private static final Instant LOGIN_AT = Instant.parse("2026-10-06T00:00:00Z");

    private final MutableClock clock = new MutableClock();
    private final SessionLifetimeFilter filter =
            new SessionLifetimeFilter(clock, new ExpiredSessionRequestSaver(request -> false));

    @AfterEach
    void 認証を消す() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse request(MockHttpSession session, Duration elapsed) throws Exception {
        clock.setInstant(LOGIN_AT.plus(elapsed));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer/transport-requests");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest requestOf(MockHttpSession session, Duration elapsed) throws Exception {
        clock.setInstant(LOGIN_AT.plus(elapsed));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer/transport-requests");
        request.setSession(session);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return request;
    }

    private static MockHttpSession authenticatedSession(boolean withLoginTime) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(
                        "user",
                        null,
                        "ROLE_SHIPPER",
                        FactorGrantedAuthority.PASSWORD_AUTHORITY,
                        TotpAuthenticationToken.FACTOR_TOTP));
        MockHttpSession session = new MockHttpSession();
        if (withLoginTime) {
            session.setAttribute(SessionKeys.AUTHENTICATED_AT, LOGIN_AT);
        }
        return session;
    }

    @Test
    void 発行から8時間の1秒前は通す() throws Exception {
        MockHttpSession session = authenticatedSession(true);

        assertThat(request(session, Duration.ofHours(8).minusSeconds(1)).getRedirectedUrl())
                .isNull();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void 発行から8時間ちょうどで失効させる() throws Exception {
        MockHttpSession session = authenticatedSession(true);

        assertThat(request(session, Duration.ofHours(8)).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 発行から8時間の1秒後も失効させる() throws Exception {
        MockHttpSession session = authenticatedSession(true);

        assertThat(request(session, Duration.ofHours(8).plusSeconds(1)).getRedirectedUrl())
                .isEqualTo(SessionPaths.EXPIRED_URL);
    }

    @Test
    void 認証済みなのにログインの時刻がないsessionは失効させる() throws Exception {
        MockHttpSession session = authenticatedSession(false);

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 未認証のsessionはログインの時刻がなくても通す() throws Exception {
        MockHttpSession session = new MockHttpSession();

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isNull();
    }

    @Test
    void 登録の途中のpasswordの段は登録を始めた時刻から15分の1秒前まで通し15分で失効させる() throws Exception {
        MockHttpSession session = passwordStageSession(true);
        session.setAttribute(SessionKeys.ENROLLMENT_STARTED_AT, LOGIN_AT.plus(Duration.ofMinutes(1)));

        assertThat(request(session, Duration.ofMinutes(16).minusSeconds(1)).getRedirectedUrl())
                .isNull();
        assertThat(request(session, Duration.ofMinutes(16)).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
    }

    @Test
    void 二つの要素がそろった後は登録の途中の時刻を使わず8時間で判定する() throws Exception {
        MockHttpSession session = authenticatedSession(true);
        session.setAttribute(SessionKeys.ENROLLMENT_STARTED_AT, LOGIN_AT);

        assertThat(request(session, Duration.ofHours(1)).getRedirectedUrl()).isNull();
    }

    // --- password の段だけの session（Bolt 31。password を確かめた時刻から 5 分） ---

    private static MockHttpSession passwordStageSession(boolean withVerifiedTime) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(
                        "user", null, "ROLE_SHIPPER", FactorGrantedAuthority.PASSWORD_AUTHORITY));
        MockHttpSession session = new MockHttpSession();
        if (withVerifiedTime) {
            session.setAttribute(SessionKeys.PASSWORD_VERIFIED_AT, LOGIN_AT);
        }
        return session;
    }

    @Test
    void passwordの段だけのsessionは5分の1秒前まで通す() throws Exception {
        MockHttpSession session = passwordStageSession(true);

        assertThat(request(session, Duration.ofMinutes(5).minusSeconds(1)).getRedirectedUrl())
                .isNull();
    }

    @Test
    void passwordの段だけのsessionは5分ちょうどで失効させる() throws Exception {
        MockHttpSession session = passwordStageSession(true);

        assertThat(request(session, Duration.ofMinutes(5)).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void passwordを確かめた時刻のないpasswordの段のsessionは失効させる() throws Exception {
        MockHttpSession session = passwordStageSession(false);

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
    }

    @Test
    void 二つの要素がそろったのにログインの時刻がないsessionはpasswordの時刻があっても失効させる() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(
                        "user",
                        null,
                        "ROLE_SHIPPER",
                        FactorGrantedAuthority.PASSWORD_AUTHORITY,
                        TotpAuthenticationToken.FACTOR_TOTP));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.PASSWORD_VERIFIED_AT, LOGIN_AT);

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
    }

    @Test
    void 配備の前の形のsessionはpasswordの段の時刻がないので失効させる() throws Exception {
        // Bolt 31 の前は password の段でログインの時刻を置いた。その session は A-03 に送り、ログインし直してもらう（ADR-012）
        MockHttpSession session = passwordStageSession(false);
        session.setAttribute(SessionKeys.AUTHENTICATED_AT, LOGIN_AT);

        assertThat(request(session, Duration.ofMinutes(1)).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
    }

    @Test
    void passwordの段だけのsessionはTOTPの時刻が残っていても5分で失効させる() throws Exception {
        // 利用者を切り替えた session に前の利用者の TOTP の時刻が残っても、password の段の上限を外さない（Bolt 31 の開発レビュー）
        MockHttpSession session = passwordStageSession(true);
        session.setAttribute(SessionKeys.AUTHENTICATED_AT, LOGIN_AT);

        assertThat(request(session, Duration.ofMinutes(5)).getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
    }

    // --- 8 時間の残りを画面に渡す（Bolt 32b、ACC-06。session の警告） ---

    @Test
    void 二つの要素がそろったsessionは8時間の残りの秒数をrequestの属性に置く() throws Exception {
        MockHttpServletRequest request = requestOf(authenticatedSession(true), Duration.ofHours(2));

        assertThat(request.getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE))
                .isEqualTo(Duration.ofHours(6).toSeconds());
    }

    @Test
    void 上限の1秒前は残りを1秒として置く() throws Exception {
        MockHttpServletRequest request =
                requestOf(authenticatedSession(true), Duration.ofHours(8).minusSeconds(1));

        assertThat(request.getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE))
                .isEqualTo(1L);
    }

    @Test
    void 上限を過ぎたsessionには残りを置かない() throws Exception {
        MockHttpServletRequest request = requestOf(authenticatedSession(true), Duration.ofHours(8));

        assertThat(request.getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE))
                .isNull();
    }

    @Test
    void passwordの段だけのsessionには残りを置かない() throws Exception {
        MockHttpServletRequest request = requestOf(passwordStageSession(true), Duration.ofMinutes(1));

        assertThat(request.getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE))
                .isNull();
    }

    @Test
    void 前の利用者のログインの時刻が残ったpasswordの段のsessionには残りを置かない() throws Exception {
        // 置くかどうかは session の時刻の属性でなく認証の要素で選ぶ（上限の判定と同じ。Bolt 32b の開発レビュー）
        MockHttpSession session = passwordStageSession(true);
        session.setAttribute(SessionKeys.AUTHENTICATED_AT, LOGIN_AT);

        assertThat(requestOf(session, Duration.ofMinutes(1)).getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE))
                .isNull();
    }

    @Test
    void 登録の途中のsessionには残りを置かない() throws Exception {
        MockHttpSession session = passwordStageSession(true);
        session.setAttribute(SessionKeys.ENROLLMENT_STARTED_AT, LOGIN_AT);

        assertThat(requestOf(session, Duration.ofMinutes(1)).getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE))
                .isNull();
    }

    @Test
    void 未認証のsessionには残りを置かない() throws Exception {
        MockHttpServletRequest request = requestOf(new MockHttpSession(), Duration.ZERO);

        assertThat(request.getAttribute(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE))
                .isNull();
    }

    // --- 失効の後に開いていた画面へ戻す（Bolt 32e） ---

    private final SessionLifetimeFilter returningFilter =
            new SessionLifetimeFilter(clock, new ExpiredSessionRequestSaver(request -> true));

    private MockHttpServletRequest returningRequest(MockHttpSession session, Duration elapsed) throws Exception {
        clock.setInstant(LOGIN_AT.plus(elapsed));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer/transport-requests");
        request.setQueryString("page=2");
        request.setSession(session);
        returningFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return request;
    }

    @Test
    void 戻れる画面なら戻り先をqueryごとrequestの属性に置く() throws Exception {
        MockHttpServletRequest request = returningRequest(authenticatedSession(true), Duration.ofHours(1));

        assertThat(request.getAttribute(SessionKeys.RETURN_PATH_ATTRIBUTE))
                .isEqualTo("/customer/transport-requests?page=2");
    }

    @Test
    void 戻れない画面には戻り先を置かない() throws Exception {
        MockHttpServletRequest request = requestOf(authenticatedSession(true), Duration.ofHours(1));

        assertThat(request.getAttribute(SessionKeys.RETURN_PATH_ATTRIBUTE)).isNull();
    }

    @Test
    void 八時間の上限では新しいsessionに開いていた画面を覚える() throws Exception {
        MockHttpServletRequest request = returningRequest(authenticatedSession(true), Duration.ofHours(8));

        assertThat(request.getSession(false)).as("新しい session").isNotNull();
        assertThat(request.getSession(false).getAttribute(SessionKeys.SAVED_REQUEST))
                .isNotNull();
    }

    @Test
    void passwordの段の失効では覚えていた要求を新しいsessionに持ち越す() throws Exception {
        MockHttpSession session = passwordStageSession(true);
        Object saved = new Object();
        session.setAttribute(SessionKeys.SAVED_REQUEST, saved);

        MockHttpServletRequest request = returningRequest(session, Duration.ofMinutes(5));

        assertThat(session.isInvalid()).isTrue();
        assertThat(request.getSession(false).getAttribute(SessionKeys.SAVED_REQUEST))
                .isSameAs(saved);
    }

    @Test
    void passwordの段だけのsessionは5分の1秒後も失効させる() throws Exception {
        MockHttpSession session = passwordStageSession(true);

        assertThat(request(session, Duration.ofMinutes(5).plusSeconds(1)).getRedirectedUrl())
                .isEqualTo(SessionPaths.EXPIRED_URL);
    }
}

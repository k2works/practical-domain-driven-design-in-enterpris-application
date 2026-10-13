package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

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
    private final SessionLifetimeFilter filter = new SessionLifetimeFilter(clock);

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
            session.setAttribute(SessionLifetime.AUTHENTICATED_AT, LOGIN_AT);
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

        assertThat(request(session, Duration.ofHours(8)).getRedirectedUrl()).isEqualTo(SessionLifetime.EXPIRED_URL);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 発行から8時間の1秒後も失効させる() throws Exception {
        MockHttpSession session = authenticatedSession(true);

        assertThat(request(session, Duration.ofHours(8).plusSeconds(1)).getRedirectedUrl())
                .isEqualTo(SessionLifetime.EXPIRED_URL);
    }

    @Test
    void 認証済みなのにログインの時刻がないsessionは失効させる() throws Exception {
        MockHttpSession session = authenticatedSession(false);

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isEqualTo(SessionLifetime.EXPIRED_URL);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 未認証のsessionはログインの時刻がなくても通す() throws Exception {
        MockHttpSession session = new MockHttpSession();

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isNull();
    }

    @Test
    void ログインの時刻の属性の名前はクラスの名前に依らない固定の値() {
        assertThat(SessionLifetime.AUTHENTICATED_AT).isEqualTo("cargotracker.session.authenticatedAt");
        assertThat(SessionLifetime.PASSWORD_VERIFIED_AT).isEqualTo("cargotracker.session.passwordVerifiedAt");
    }

    // --- password の段だけの session（Bolt 31。password を確かめた時刻から 5 分） ---

    private static MockHttpSession passwordStageSession(boolean withVerifiedTime) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(
                        "user", null, "ROLE_SHIPPER", FactorGrantedAuthority.PASSWORD_AUTHORITY));
        MockHttpSession session = new MockHttpSession();
        if (withVerifiedTime) {
            session.setAttribute(SessionLifetime.PASSWORD_VERIFIED_AT, LOGIN_AT);
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

        assertThat(request(session, Duration.ofMinutes(5)).getRedirectedUrl()).isEqualTo(SessionLifetime.EXPIRED_URL);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void passwordを確かめた時刻のないpasswordの段のsessionは失効させる() throws Exception {
        MockHttpSession session = passwordStageSession(false);

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isEqualTo(SessionLifetime.EXPIRED_URL);
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
        session.setAttribute(SessionLifetime.PASSWORD_VERIFIED_AT, LOGIN_AT);

        assertThat(request(session, Duration.ZERO).getRedirectedUrl()).isEqualTo(SessionLifetime.EXPIRED_URL);
    }

    @Test
    void 配備の前の形のsessionはpasswordの段の時刻がないので失効させる() throws Exception {
        // Bolt 31 の前は password の段でログインの時刻を置いた。その session は A-03 に送り、ログインし直してもらう（ADR-012）
        MockHttpSession session = passwordStageSession(false);
        session.setAttribute(SessionLifetime.AUTHENTICATED_AT, LOGIN_AT);

        assertThat(request(session, Duration.ofMinutes(1)).getRedirectedUrl()).isEqualTo(SessionLifetime.EXPIRED_URL);
    }

    @Test
    void passwordの段だけのsessionはTOTPの時刻が残っていても5分で失効させる() throws Exception {
        // 利用者を切り替えた session に前の利用者の TOTP の時刻が残っても、password の段の上限を外さない（Bolt 31 の開発レビュー）
        MockHttpSession session = passwordStageSession(true);
        session.setAttribute(SessionLifetime.AUTHENTICATED_AT, LOGIN_AT);

        assertThat(request(session, Duration.ofMinutes(5)).getRedirectedUrl()).isEqualTo(SessionLifetime.EXPIRED_URL);
    }

    @Test
    void passwordの段だけのsessionは5分の1秒後も失効させる() throws Exception {
        MockHttpSession session = passwordStageSession(true);

        assertThat(request(session, Duration.ofMinutes(5).plusSeconds(1)).getRedirectedUrl())
                .isEqualTo(SessionLifetime.EXPIRED_URL);
    }
}

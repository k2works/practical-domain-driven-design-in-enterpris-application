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
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 発行から 8 時間の上限の判定（US-18 AC5、T-38 の 3 点）。認証済みなのにログインの時刻がない session も失効として扱う
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
                .setAuthentication(new TestingAuthenticationToken("user", null, "ROLE_SHIPPER"));
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
    }
}

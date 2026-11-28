package com.example.cargotracker.identity.application.internal.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * session・request の属性の名前（Bolt 33c。URL は Bolt 33f から SessionPathsTest）。動いている session の属性が読めなくならないように、名前はクラスの名前に依らない固定の値に
 * する（Bolt 31 から {@code SessionLifetimeFilterTest} が守っていたものを、すべての名前に広げて移した）。
 */
class SessionKeysTest {

    @Test
    void sessionの属性の名前はクラスの名前に依らない固定の値() {
        assertThat(SessionKeys.AUTHENTICATED_AT).isEqualTo("cargotracker.session.authenticatedAt");
        assertThat(SessionKeys.PASSWORD_VERIFIED_AT).isEqualTo("cargotracker.session.passwordVerifiedAt");
        assertThat(SessionKeys.ENROLLMENT_STARTED_AT).isEqualTo("cargotracker.session.enrollmentStartedAt");
        assertThat(SessionKeys.ENROLLMENT_PENDING_SECRET).isEqualTo("cargotracker.session.enrollmentPendingSecret");
        assertThat(SessionKeys.RECOVERY_LOGIN_USER).isEqualTo("cargotracker.session.recoveryLoginUser");
        assertThat(SessionKeys.RECOVERY_LOGIN_AT).isEqualTo("cargotracker.session.recoveryLoginAt");
    }

    @Test
    void 覚えた要求の名前はSpringSecurityの既定のcacheが保存する名前と同じ() {
        // 未認証の入口と回復コード・TOTP の成功の扱いは既定の cache で読み書きするので、文字列ではなく既定の振る舞いと照らす
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer/transport-requests");

        new HttpSessionRequestCache().saveRequest(request, new MockHttpServletResponse());

        assertThat(request.getSession().getAttribute(SessionKeys.SAVED_REQUEST)).isNotNull();
    }

    @Test
    void requestの属性の名前は固定の値() {
        assertThat(SessionKeys.LIFETIME_REMAINING_ATTRIBUTE)
                .isEqualTo("cargotracker.request.sessionLifetimeRemainingSeconds");
        assertThat(SessionKeys.RETURN_PATH_ATTRIBUTE).isEqualTo("cargotracker.request.returnPath");
    }
}

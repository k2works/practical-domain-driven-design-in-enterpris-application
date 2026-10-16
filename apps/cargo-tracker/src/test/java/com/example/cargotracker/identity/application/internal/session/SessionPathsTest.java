package com.example.cargotracker.identity.application.internal.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 認証の入口と画面が共有する URL（Bolt 33f で SessionKeysTest から移した）。公開の URL の値を変えない。 */
class SessionPathsTest {

    @Test
    void 認証の入口と画面が共有するURL() {
        assertThat(SessionPaths.RE_ENROLL_URL).isEqualTo("/account/authenticator");
        assertThat(SessionPaths.EXPIRED_URL).isEqualTo("/session-expired");
        assertThat(SessionPaths.PASSWORD_RESET_URL).isEqualTo("/password-reset");
        assertThat(SessionPaths.PASSWORD_RESET_TOKEN_URL_PATTERN).isEqualTo("/password-reset/*");
    }
}

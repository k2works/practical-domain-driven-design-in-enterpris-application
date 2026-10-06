package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

/**
 * ログインの失敗の後に入れたメールアドレスだけを残す（UI 設計「A-01」）。session に置く値の長さをメールアドレスの上限で切る
 * （Bolt 14 レビュー）。
 */
class KeepEmailAuthenticationFailureHandlerTest {

    @Test
    void 入れたメールアドレスを残しpasswordは残さずログインの画面へ移す() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("username", "shipper@example.com");
        request.setParameter("password", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new KeepEmailAuthenticationFailureHandler()
                .onAuthenticationFailure(request, response, new BadCredentialsException("x"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        FlashMap flash = new SessionFlashMapManager()
                .retrieveAndUpdate(
                        new MockHttpServletRequest("GET", "/login") {
                            {
                                setSession(request.getSession());
                            }
                        },
                        new MockHttpServletResponse());
        assertThat(flash.get("username")).isEqualTo("shipper@example.com");
        assertThat(flash.containsKey("password")).isFalse();
    }

    @Test
    void 長すぎる入力はメールアドレスの上限で切って残す() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("username", "a".repeat(10_000));

        new KeepEmailAuthenticationFailureHandler()
                .onAuthenticationFailure(request, new MockHttpServletResponse(), new BadCredentialsException("x"));

        FlashMap flash = new SessionFlashMapManager()
                .retrieveAndUpdate(
                        new MockHttpServletRequest("GET", "/login") {
                            {
                                setSession(request.getSession());
                            }
                        },
                        new MockHttpServletResponse());
        assertThat((String) flash.get("username")).hasSize(EmailAddress.MAX_LENGTH);
    }
}

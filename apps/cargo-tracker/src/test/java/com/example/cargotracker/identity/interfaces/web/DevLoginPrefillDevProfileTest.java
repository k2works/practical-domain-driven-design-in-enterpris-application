package com.example.cargotracker.identity.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 開発環境（dev、H2）では A-01 が開発用の荷主の利用者で入力済みになる（2026-10-06 の人の決定、UI 設計「A-01」）。
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DevLoginPrefillDevProfileTest {

    @Autowired
    MockMvc mvc;

    @Test
    void devではログインの画面にメールアドレスとpasswordが入っている() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("value=\"shipper@dev.cargo-tracker.example\"")))
                .andExpect(content().string(containsString("value=\"dev-password-shipper\"")));
    }

    @Test
    void devではH2のコンソールをログインなしで開ける() throws Exception {
        mvc.perform(get("/h2-console/"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getRedirectedUrl())
                        .as("ログインへ移さない")
                        .isNull());
    }
}

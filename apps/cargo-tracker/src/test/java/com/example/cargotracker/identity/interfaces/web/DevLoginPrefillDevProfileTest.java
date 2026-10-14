package com.example.cargotracker.identity.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 開発環境（dev、H2）のログインの画面（2026-10-06 の人の決定、UI 設計「A-01」）。
 * A-01 は開発用の荷主の利用者で入力済みになり、荷主担当者・営業担当者の開発用の利用者を選んでそのままログインできる。
 * A-02 は開発用の利用者の固定の秘密からその時点のコードで入力済みになる（Bolt 31。TOTP を飛ばさない）。
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DevLoginPrefillDevProfileTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DevTotpCodeSource devTotpCodes;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void devではログインの画面にメールアドレスとpasswordが入っている() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("value=\"shipper@dev.cargo-tracker.example\"")))
                .andExpect(content().string(containsString("value=\"dev-password-shipper\"")));
    }

    @Test
    void devではログインの画面で荷主担当者と営業担当者の開発用の利用者を選べる() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("開発用の利用者でログイン")))
                .andExpect(content().string(containsString("荷主担当者でログイン（shipper@dev.cargo-tracker.example）")))
                .andExpect(content().string(containsString("荷主担当者でログイン（shipper-b@dev.cargo-tracker.example）")))
                .andExpect(content().string(containsString("営業担当者でログイン（sales@dev.cargo-tracker.example）")))
                .andExpect(content().string(containsString("追跡管理者でログイン（tracking-manager@dev.cargo-tracker.example）")));
    }

    @Test
    void 追跡管理者の開発用の利用者を選ぶとA02が入力済みで確認すると追跡一覧へ移る() throws Exception {
        Cookie session = signIn("tracking-manager@dev.cargo-tracker.example");

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/tracking-records"));
    }

    @Test
    void 営業担当者の開発用の利用者を選ぶとA02が入力済みで確認すると社内のホームへ移る() throws Exception {
        Cookie session = signIn("sales@dev.cargo-tracker.example");

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/transport-requests"));
    }

    /**
     * 開発用の利用者のボタンと同じ送信（password の段）の後に、A-02 が入力済みであることを確かめ、そのコードを送る（Bolt 31）。
     * 同じ利用者のテストの間で同じ刻みのコードを使い回さないよう、使った刻みを戻す（テストの準備。確認ポイント 7）。
     */
    private Cookie signIn(String email) throws Exception {
        AuthenticationFactorFixtures.resetTotpStep(jdbc, email);
        MvcResult password = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", "dev-password-staff")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login/totp"))
                .andReturn();
        Cookie passwordStage = password.getResponse().getCookie("SESSION");
        String code = devTotpCodes.codeFor(email).orElseThrow();

        mvc.perform(get("/login/totp").cookie(passwordStage))
                .andExpect(content().string(containsString("value=\"" + code + "\"")));
        MvcResult totp = mvc.perform(post("/login/totp")
                        .cookie(passwordStage)
                        .param("code", code)
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        return totp.getResponse().getCookie("SESSION");
    }
}

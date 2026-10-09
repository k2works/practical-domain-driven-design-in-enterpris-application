package com.example.cargotracker.identity.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 開発環境（dev、H2）のログインの画面（2026-10-06 の人の決定、UI 設計「A-01」）。
 * A-01 は開発用の荷主の利用者で入力済みになり、荷主担当者・営業担当者の開発用の利用者を選んでそのままログインできる。
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
    void devではログインの画面で荷主担当者と営業担当者の開発用の利用者を選べる() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("開発用の利用者でログイン")))
                .andExpect(content().string(containsString("荷主担当者でログイン（shipper@dev.cargo-tracker.example）")))
                .andExpect(content().string(containsString("荷主担当者でログイン（shipper-b@dev.cargo-tracker.example）")))
                .andExpect(content().string(containsString("営業担当者でログイン（sales@dev.cargo-tracker.example）")))
                .andExpect(content().string(containsString("追跡管理者でログイン（tracking-manager@dev.cargo-tracker.example）")));
    }

    @Test
    void 追跡管理者の開発用の利用者を選ぶと追跡一覧へ移る() throws Exception {
        MvcResult login = mvc.perform(post("/login")
                        .param("username", "tracking-manager@dev.cargo-tracker.example")
                        .param("password", "dev-password-staff")
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        Cookie session = login.getResponse().getCookie("SESSION");

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/tracking-records"));
    }

    @Test
    void 営業担当者の開発用の利用者を選ぶと社内のホームへ移る() throws Exception {
        MvcResult login = mvc.perform(post("/login")
                        .param("username", "sales@dev.cargo-tracker.example")
                        .param("password", "dev-password-staff")
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        Cookie session = login.getResponse().getCookie("SESSION");

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/transport-requests"));
    }
}

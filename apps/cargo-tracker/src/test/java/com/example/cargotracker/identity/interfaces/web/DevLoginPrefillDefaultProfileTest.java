package com.example.cargotracker.identity.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.cargotracker.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 既定の設定（ステージング・本番と同じ）では A-01 を入力済みにしない（守りの 1 層目。ADR-011 の決定 3）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DevLoginPrefillDefaultProfileTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Test
    void 既定の設定では入力済みの部品がない() {
        assertThat(context.getBeanNamesForType(DevLoginPrefill.class)).isEmpty();
    }

    @Test
    void 既定の設定ではログインの画面のメールアドレスとpasswordが空() throws Exception {
        String html = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("id=\"username\"")
                .doesNotContain("value=\"shipper@")
                .doesNotContain("dev-password")
                .doesNotContain("開発用の利用者でログイン")
                .containsPattern("id=\"password\"[^>]*>")
                .doesNotContainPattern("id=\"password\"[^>]*value=\"[^\"]+\"");
    }
}

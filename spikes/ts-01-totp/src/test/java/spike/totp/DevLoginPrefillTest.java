package spike.totp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 開発環境だけでログインの画面を入力済みにする（人の決定。2026-10-06。Bolt 13 の確認ポイント 7）。
 * dev プロファイルの設定があるときだけ、A-01 にメールアドレスと password、A-02 にその時点の TOTP のコードが入る。
 */
class DevLoginPrefillTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:15Z");

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestClockConfiguration.class)
    @ActiveProfiles("dev")
    class 開発環境 {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        MutableClock clock;

        @Autowired
        SpikeUsers users;

        @Test
        void ログインの画面にメールアドレスとpasswordが入っている() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(content().string(containsString("value=\"" + SpikeUsers.SHIPPER + "\"")))
                    .andExpect(content().string(containsString("value=\"" + SpikeUsers.PASSWORD + "\"")));
        }

        @Test
        void 認証コードの画面にその時点のコードが入っている() throws Exception {
            clock.set(NOW);
            users.reset();
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(post("/login")
                    .session(session)
                    .with(csrf())
                    .param("username", SpikeUsers.SHIPPER)
                    .param("password", SpikeUsers.PASSWORD));

            mockMvc.perform(get("/login/totp").session(session))
                    .andExpect(content().string(containsString(
                            "value=\"" + users.currentCode(SpikeUsers.SHIPPER, NOW) + "\"")));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestClockConfiguration.class)
    class 既定の設定 {

        @Autowired
        MockMvc mockMvc;

        @Test
        void ログインの画面は空で出る() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(content().string(containsString("name=\"username\"")))
                    .andExpect(content().string(not(containsString(SpikeUsers.SHIPPER))))
                    .andExpect(content().string(not(containsString(SpikeUsers.PASSWORD))));
        }
    }
}

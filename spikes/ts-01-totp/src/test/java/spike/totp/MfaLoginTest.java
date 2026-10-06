package spike.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.authentication.LockedException;

/**
 * Spring Security 7 の多要素認証で、password の後に TOTP を求める 2 段階のログイン（Bolt 13 ステップ 3。仮説 H1・H3）。
 * 業務の画面は仮の /staff。時計はテストで動かす。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfiguration.class)
class MfaLoginTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:15Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    SpikeUsers users;

    @BeforeEach
    void reset() {
        clock.set(NOW);
        users.reset();
    }

    private MockHttpSession password(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login")
                .session(session)
                .with(csrf())
                .param("username", username)
                .param("password", password));
        return session;
    }

    private void totp(MockHttpSession session, String code) throws Exception {
        mockMvc.perform(post("/login/totp").session(session).with(csrf()).param("code", code));
    }

    private String currentCode() {
        return users.currentCode(SpikeUsers.SHIPPER, clock.instant());
    }

    private static Authentication authentication(MockHttpSession session) {
        SecurityContext context = (SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context == null ? null : context.getAuthentication();
    }

    @Test
    void 未認証で業務の画面を開くとログインの画面へ導く() throws Exception {
        mockMvc.perform(get("/staff")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void passwordだけでは業務の画面を開けずTOTPの画面へ導く() throws Exception {
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);

        mockMvc.perform(get("/staff").session(session)).andExpect(redirectedUrl("/login/totp?factor.type=totp&factor.reason=missing"));
        mockMvc.perform(get("/login/totp").session(session)).andExpect(status().isOk());
    }

    @Test
    void passwordと正しいTOTPで両方の要素がそろい業務の画面を開ける() throws Exception {
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);

        totp(session, currentCode());

        mockMvc.perform(get("/staff").session(session)).andExpect(status().isOk());
        assertThat(authentication(session).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("FACTOR_PASSWORD", "FACTOR_TOTP");
    }

    @Test
    void 誤ったTOTPでは業務の画面を開けない() throws Exception {
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);

        mockMvc.perform(post("/login/totp").session(session).with(csrf()).param("code", "000000"))
                .andExpect(redirectedUrl("/login/totp?error"));

        mockMvc.perform(get("/staff").session(session)).andExpect(redirectedUrl("/login/totp?factor.type=totp&factor.reason=missing"));
    }

    @Test
    void 同じ時間の窓のコードは一度しか使えない() throws Exception {
        String code = currentCode();
        MockHttpSession first = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        totp(first, code);
        MockHttpSession second = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);

        mockMvc.perform(post("/login/totp").session(second).with(csrf()).param("code", code))
                .andExpect(redirectedUrl("/login/totp?error"));
        mockMvc.perform(get("/staff").session(second)).andExpect(redirectedUrl("/login/totp?factor.type=totp&factor.reason=missing"));
    }

    @Test
    void passwordを経ずにTOTPだけを送っても認証されない() throws Exception {
        MockHttpSession session = new MockHttpSession();

        totp(session, currentCode());

        mockMvc.perform(get("/staff").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void passwordとTOTPの失敗が5回続くと正しいpasswordでもロックされ15分後に解ける() throws Exception {
        password(SpikeUsers.SHIPPER, "wrong-password-1");
        password(SpikeUsers.SHIPPER, "wrong-password-2");
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        totp(session, "000000");
        totp(session, "111111");
        totp(session, "222222");

        MockHttpSession locked = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        assertThat(authentication(locked)).isNull();

        clock.set(NOW.plus(Duration.ofMinutes(15)));
        MockHttpSession unlocked = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        assertThat(authentication(unlocked)).isNotNull();
    }

    @Test
    void 回復コードはTOTPの代わりに一度だけ使える() throws Exception {
        String recoveryCode = users.recoveryCodes(SpikeUsers.SHIPPER).getFirst();
        MockHttpSession first = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        totp(first, recoveryCode);
        mockMvc.perform(get("/staff").session(first)).andExpect(status().isOk());

        MockHttpSession second = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        totp(second, recoveryCode);
        mockMvc.perform(get("/staff").session(second)).andExpect(redirectedUrl("/login/totp?factor.type=totp&factor.reason=missing"));
    }

    @Test
    void 本人確認から8時間を過ぎたsessionは業務の画面を開けず再認証を求める() throws Exception {
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        totp(session, currentCode());
        mockMvc.perform(get("/staff").session(session)).andExpect(status().isOk());

        clock.set(NOW.plus(Duration.ofHours(8)));

        mockMvc.perform(get("/staff").session(session)).andExpect(redirectedUrl("/login"));
    }

    // Bolt 13 レビューの指摘（境界の 3 点、迂回、session の固定化、利用者の取り違え）

    private MockHttpSession fullyAuthenticated() throws Exception {
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        totp(session, currentCode());
        return session;
    }

    @Test
    void 本人確認から7時間59分59秒では業務の画面を開ける() throws Exception {
        MockHttpSession session = fullyAuthenticated();

        clock.set(NOW.plus(Duration.ofHours(8)).minusSeconds(1));

        mockMvc.perform(get("/staff").session(session)).andExpect(status().isOk());
    }

    @Test
    void 本人確認から8時間を過ぎたsessionではTOTPを送っても延長されずログインの画面へ導く() throws Exception {
        MockHttpSession session = fullyAuthenticated();
        clock.set(NOW.plus(Duration.ofHours(8)).plusSeconds(60));

        mockMvc.perform(post("/login/totp").session(session).with(csrf()).param("code", currentCode()))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/staff").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 本人確認済みのsessionでTOTPを送り直しても本人確認の時刻は変わらない() throws Exception {
        MockHttpSession session = fullyAuthenticated();
        Object first = session.getAttribute(TotpAuthenticationFilter.AUTHENTICATED_AT);
        clock.set(NOW.plusSeconds(60));

        totp(session, currentCode());

        assertThat(session.getAttribute(TotpAuthenticationFilter.AUTHENTICATED_AT)).isEqualTo(first);
    }

    @Test
    void TOTPで本人確認するとsessionのIDが変わる() throws Exception {
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        String before = session.getId();

        MvcResult result = mockMvc.perform(post("/login/totp").session(session).with(csrf()).param("code", currentCode()))
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(before);
    }

    @Test
    void 失敗が4回ではロックされない() throws Exception {
        password(SpikeUsers.SHIPPER, "wrong-password-1");
        password(SpikeUsers.SHIPPER, "wrong-password-2");
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        totp(session, "000000");
        totp(session, "111111");

        totp(session, currentCode());

        mockMvc.perform(get("/staff").session(session)).andExpect(status().isOk());
    }

    @Test
    void 失敗が5回続くと14分59秒まではロックされロックの理由で拒否される() throws Exception {
        failFiveTimes();
        clock.set(NOW.plus(Duration.ofMinutes(15)).minusSeconds(1));

        MockHttpSession locked = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);

        assertThat(authentication(locked)).isNull();
        assertThat(locked.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION)).isInstanceOf(LockedException.class);
    }

    @Test
    void 成功すると失敗の回数は数え直される() throws Exception {
        password(SpikeUsers.SHIPPER, "wrong-password-1");
        password(SpikeUsers.SHIPPER, "wrong-password-2");
        password(SpikeUsers.SHIPPER, "wrong-password-3");
        password(SpikeUsers.SHIPPER, "wrong-password-4");
        fullyAuthenticated();
        clock.set(NOW.plusSeconds(30));

        password(SpikeUsers.SHIPPER, "wrong-password-5");

        assertThat(authentication(password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD))).isNotNull();
    }

    @Test
    void passwordを確かめた後にロックされたら正しいTOTPも拒否される() throws Exception {
        MockHttpSession session = password(SpikeUsers.SHIPPER, SpikeUsers.PASSWORD);
        failFiveTimes();

        totp(session, currentCode());

        mockMvc.perform(get("/staff").session(session))
                .andExpect(redirectedUrl("/login/totp?factor.type=totp&factor.reason=missing"));
    }

    @Test
    void 別の利用者のpasswordを送っても先の利用者のTOTPの要素は引き継がれない() throws Exception {
        MockHttpSession session = fullyAuthenticated();

        mockMvc.perform(post("/login")
                .session(session)
                .with(csrf())
                .param("username", SpikeUsers.STAFF)
                .param("password", SpikeUsers.PASSWORD));

        assertThat(authentication(session).getName()).isEqualTo(SpikeUsers.STAFF);
        assertThat(authentication(session).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("FACTOR_TOTP");
    }

    private void failFiveTimes() throws Exception {
        for (int i = 1; i <= 5; i++) {
            password(SpikeUsers.SHIPPER, "wrong-password-" + i);
        }
    }
}

package com.example.cargotracker.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * identity のセキュリティの統合テストで、password と TOTP の 2 段階でログインする補助（Bolt 34e）。各クラスに写していた login・
 * password の段・いまのコード・利用者 ID の探し方・session の Cookie の取り方をこの 1 か所に置く。spy を持たない（利用者 ID は JDBC で
 * 探し、spy の利用者のリポジトリの呼び出しの数を変えない）。テストの利用者の password はどのクラスも {@link #PASSWORD} にそろえる。
 */
public final class IdentityLogins {

    /** テストの利用者の password。 */
    public static final String PASSWORD = "correct horse battery staple";

    /** session の Cookie の名前。 */
    public static final String SESSION_COOKIE = "SESSION";

    /** A-02（TOTP の段）の URL。 */
    public static final String TOTP_URL = "/login/totp";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final TotpCredentialRepository totpCredentials;
    private final TotpCodeGenerator totpCodes;
    private final Clock clock;

    public IdentityLogins(
            MockMvc mvc,
            JdbcTemplate jdbc,
            TotpCredentialRepository totpCredentials,
            TotpCodeGenerator totpCodes,
            Clock clock) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.totpCredentials = totpCredentials;
        this.totpCodes = totpCodes;
        this.clock = clock;
    }

    /**
     * password と TOTP の 2 段階でログインする。同じ利用者が同じ刻みの間に入り直しても再利用にならないよう、テストの準備として TOTP を
     * 送る前に使った刻みを戻す（本体の再利用の拒否は変えない。Bolt 31 の確認ポイント 7）。TOTP の後は誤りにも期限切れにも移らない。
     */
    public Cookie login(String email) throws Exception {
        Cookie passwordStage = loginWithPassword(email);
        AuthenticationFactorFixtures.resetTotpStep(jdbc, userIdOf(email));
        MvcResult result = mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("code", currentCode(email))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl())
                .doesNotContain("error")
                .doesNotContain("expired");
        return sessionOf(result);
    }

    /** password の段だけを通す（A-02 へ移る）。 */
    public Cookie loginWithPassword(String email) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        return sessionOf(result);
    }

    /** いまの時計の刻みの TOTP のコード（テストの利用者の登録した秘密から作る）。 */
    public String currentCode(String email) {
        TotpCredential credential =
                totpCredentials.findByUserId(userIdOf(email)).orElseThrow();
        return totpCodes.code(
                credential.userId(), credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    /** メールアドレスの利用者の ID（JDBC で探す。spy の利用者のリポジトリに触れない）。 */
    public UserId userIdOf(String email) {
        return jdbc.queryForObject(
                "SELECT id FROM identity.app_user WHERE email = ?",
                (rs, n) -> new UserId(rs.getObject("id", UUID.class)),
                email);
    }

    /** 応答の session の Cookie（置いていなければ失敗にする）。 */
    public static Cookie sessionOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("session の Cookie").isNotNull();
        return cookie;
    }
}

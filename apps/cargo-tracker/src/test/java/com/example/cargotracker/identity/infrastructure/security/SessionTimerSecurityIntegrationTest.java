package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.SpringSessionRows;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.identity.interfaces.web.SessionController;
import com.example.cargotracker.identity.interfaces.web.SessionTimerAdvice.SessionTimer;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * session の失効の警告と延長のセキュリティの統合テスト（Bolt 32b、ACC-06、横断の受入条件、ADR-012）。本物のフィルターの連なりと
 * PostgreSQL の上で、ログインした画面に無操作の秒数と 8 時間の残りの秒数が渡ること、延長の URL が session の最終アクセスを
 * 新しくし、CSRF・認証・上限の判定の今のフィルターを通り、8 時間の上限を延ばさないことを確かめる。CSRF のトークンは、実際の
 * ブラウザと同じく画面の HTML から取って送る（無操作で失効した session の延長が、CSRF のトークンのない拒否の経路で A-03 に
 * なることを確かめるため）。Spring Session は最終アクセスと無操作の期限を実時間で数える（{@link SpringSessionRows#idle}）。
 */
@IdentityIntegrationTest
class SessionTimerSecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-14T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String HOME_URL = "/customer/transport-requests";
    private static final String TOTP_URL = "/login/totp";
    private static final String EXTEND_URL = SessionController.EXTEND_URL;
    private static final String EXPIRED_URL = "/session-expired";
    private static final Pattern CSRF_TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    TotpCodeGenerator totpCodes;

    @Autowired
    AesGcmSecretCipher cipher;

    private String email;

    @BeforeEach
    void 時計を置き荷主担当者を登録する() {
        clock.setInstant(LOGIN_AT);
        email = user(CompanyKind.SHIPPER, Role.SHIPPER);
    }

    private String user(CompanyKind kind, Role role) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "A 社", kind, true);
        companies.add(company);
        String newEmail = "timer-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of(newEmail),
                "利用者",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(role)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return newEmail;
    }

    // --- 残り時間を画面に渡す ---

    @Test
    void ログインした画面に無操作の秒数と8時間の残りの秒数が渡る() throws Exception {
        Cookie session = login();
        atElapsed(Duration.ofHours(2));

        String html = mvc.perform(get(HOME_URL).cookie(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute(
                                "sessionTimer",
                                new SessionTimer(1800, Duration.ofHours(6).toSeconds())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .as("共通のレイアウトの警告の部分に残り時間を書く")
                .contains("data-idle-seconds=\"1800\"")
                .contains("data-lifetime-remaining-seconds=\"21600\"")
                .contains("/js/session-timeout.js");
    }

    @Test
    void passwordの段の画面には残り時間を渡さない() throws Exception {
        Cookie passwordStage = loginWithPassword();

        String html = mvc.perform(get(TOTP_URL).cookie(passwordStage))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sessionTimer", (Object) null))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).doesNotContain("session-warning").doesNotContain("/js/session-timeout.js");
    }

    @Test
    void 社内業務Webのレイアウトにも残り時間と警告が出る() throws Exception {
        email = user(CompanyKind.OPERATOR, Role.SALES);
        Cookie session = login();

        String html = mvc.perform(get("/staff/transport-requests").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("data-idle-seconds=\"1800\"")
                .contains("data-lifetime-remaining-seconds=\"28800\"")
                .contains("/js/session-timeout.js");
    }

    // --- 延長 ---

    @Test
    void 利用を止められた利用者の延長はsessionを捨ててA03へ移す() throws Exception {
        // 延長も request ごとの利用者の確かめを通る（Bolt 32c。止められた利用者に 204 を返して延長できたと示さない）
        Cookie session = login();
        String token = csrfTokenOf(session);
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", email);

        mvc.perform(post(EXTEND_URL).cookie(session).param("_csrf", token)).andExpect(redirectedUrl(EXPIRED_URL));
        assertThat(sessions().exists(session)).as("session を捨てた").isFalse();
    }

    @Test
    void 延長は204を返しsessionの最終アクセスを新しくする() throws Exception {
        Cookie session = login();
        String token = csrfTokenOf(session);
        sessions().idle(session, Duration.ofMinutes(20));
        Instant before = Instant.now();

        mvc.perform(post(EXTEND_URL).cookie(session).param("_csrf", token)).andExpect(status().isNoContent());

        assertThat(sessions().lastAccessTime(session))
                .as("延長で無操作の期限が延びる（20 分前に戻した最終アクセスが今になる）")
                .isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    void CSRFのトークンのない延長は403で拒む() throws Exception {
        Cookie session = login();
        ensureCsrfTokenInSession(session);

        mvc.perform(post(EXTEND_URL).cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 誤ったCSRFのトークンの延長は403で拒む() throws Exception {
        Cookie session = login();
        ensureCsrfTokenInSession(session);

        mvc.perform(post(EXTEND_URL).cookie(session).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 無操作で失効したsessionの延長はA03へ移す() throws Exception {
        // session とともに CSRF のトークンも消えているので、トークンのない拒否として invalidSessionUrl の A-03 に移る（ADR-012）
        Cookie session = login();
        String token = csrfTokenOf(session);
        sessions().idle(session, Duration.ofMinutes(31));

        mvc.perform(post(EXTEND_URL).cookie(session).param("_csrf", token)).andExpect(redirectedUrl(EXPIRED_URL));
    }

    @Test
    void 八時間を過ぎたsessionの延長はA03へ移す() throws Exception {
        Cookie session = login();
        String token = csrfTokenOf(session);
        atElapsed(Duration.ofHours(8));

        mvc.perform(post(EXTEND_URL).cookie(session).param("_csrf", token)).andExpect(redirectedUrl(EXPIRED_URL));
    }

    @Test
    void 延長は八時間の上限を延ばさない() throws Exception {
        Cookie session = login();
        String token = csrfTokenOf(session);
        atElapsed(Duration.ofHours(8).minusMinutes(1));
        mvc.perform(post(EXTEND_URL).cookie(session).param("_csrf", token)).andExpect(status().isNoContent());

        atElapsed(Duration.ofHours(8));

        mvc.perform(get(HOME_URL).cookie(session)).andExpect(redirectedUrl(EXPIRED_URL));
    }

    @Test
    void passwordの段だけのsessionの延長はA02へ導く() throws Exception {
        Cookie passwordStage = loginWithPassword();
        String token = tokenIn(mvc.perform(get(TOTP_URL).cookie(passwordStage))
                .andExpect(status().isOk())
                .andReturn());

        MvcResult result = mvc.perform(post(EXTEND_URL).cookie(passwordStage).param("_csrf", token))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).startsWith(TOTP_URL);
    }

    @Test
    void ログインしていない延長はログインへ移す() throws Exception {
        // MockMvc の csrf() で CSRF の確かめを通した前提。実際のブラウザでは session がなくトークンのない拒否になり、A-03 に移る
        mvc.perform(post(EXTEND_URL).with(csrf())).andExpect(redirectedUrl("/login"));
    }

    // --- 補助 ---

    private Cookie login() throws Exception {
        Cookie passwordStage = loginWithPassword();
        AuthenticationFactorFixtures.resetTotpStep(jdbc, email);
        TotpCredential credential = totpCredentials
                .findByUserId(
                        users.findByEmail(EmailAddress.of(email)).orElseThrow().id())
                .orElseThrow();
        String code = totpCodes.code(
                credential.userId(), credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
        MvcResult result = mvc.perform(
                        post(TOTP_URL).cookie(passwordStage).param("code", code).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl())
                .doesNotContain("error")
                .doesNotContain("expired");
        return sessionOf(result);
    }

    private Cookie loginWithPassword() throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        return sessionOf(result);
    }

    /**
     * ログインした画面を開いて session に CSRF のトークンを作る（トークンのない session では、トークンのない送信が 403 でなく
     * invalidSessionUrl の A-03 になるため、403 を確かめる前に作る）。
     */
    private void ensureCsrfTokenInSession(Cookie session) throws Exception {
        csrfTokenOf(session);
    }

    /** ログインした画面を開いて、session の CSRF のトークンを画面の HTML から取る（ヘッダーのログアウトのフォーム）。 */
    private String csrfTokenOf(Cookie session) throws Exception {
        return tokenIn(mvc.perform(get(HOME_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static String tokenIn(MvcResult result) throws Exception {
        Matcher matcher = CSRF_TOKEN.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as("画面の CSRF のトークン").isTrue();
        return matcher.group(1);
    }

    private static Cookie sessionOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("session の Cookie").isNotNull();
        return cookie;
    }

    /** 時計をログインの時刻からその時間だけ後に置く（進める量でなく、ログインからの位置）。 */
    private void atElapsed(Duration duration) {
        clock.setInstant(LOGIN_AT.plus(duration));
    }

    private SpringSessionRows sessions() {
        return new SpringSessionRows(jdbc);
    }
}

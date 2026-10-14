package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 失効の後に開いていた画面へ戻すセキュリティの統合テスト（Bolt 32e、ACC-06、横断の受入条件の「再認証後に再開できる」、ADR-012）。
 * 本物のフィルターの連なりと PostgreSQL の上で、無操作 30 分と 8 時間の上限で失効したとき、開いていた業務の画面の GET を新しい
 * session に覚え、A-03 からログインし直すとその画面へ戻ることを確かめる。覚えない場合（送信、許す一覧の外、取消し）は、覚える
 * 場合と同じ流れで確かめる（T-119）。認証コードの誤りと password の段の失効では、覚えた要求を持ち越す。Spring Session は
 * 無操作の期限を実時間で数える（{@link SpringSessionRows#idle}）。
 */
@IdentityIntegrationTest
class ReturnAfterExpirySecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-14T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String PAGE_URL = "/customer/transport-requests/new";
    private static final String RETURN_URL = "http://localhost" + PAGE_URL + "?continue";
    private static final String HOME_URL = "/";
    private static final String TOTP_URL = "/login/totp";
    private static final String EXPIRED_URL = "/session-expired";
    private static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";
    private static final String STAFF_URL = "/staff/transport-requests";

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

    private Company company;
    private String email;

    @BeforeEach
    void 時計を置き荷主担当者を登録する() {
        clock.setInstant(LOGIN_AT);
        company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
        email = shipper();
    }

    // --- 覚えて戻る ---

    @Test
    void 無操作で失効した後に開いた業務の画面へログインし直すと戻る() throws Exception {
        Cookie session = login(email, null);
        sessions().idle(session, Duration.ofMinutes(31));

        Cookie expired = expiredTo(get(PAGE_URL).cookie(session));

        assertThat(loginAgain(email, expired)).isEqualTo(RETURN_URL);
    }

    @Test
    void queryの付いた画面に戻りその画面が開くと覚えた要求は消える() throws Exception {
        Cookie session = login(email, null);
        sessions().idle(session, Duration.ofMinutes(31));

        Cookie expired = expiredTo(get(PAGE_URL).queryParam("copyFrom", "TR-1").cookie(session));
        MvcResult back = loginAgainResult(email, expired);

        String target = back.getResponse().getRedirectedUrl();
        assertThat(target).isEqualTo("http://localhost" + PAGE_URL + "?copyFrom=TR-1&continue");
        Cookie loggedIn = sessionOr(back, expired);
        mvc.perform(get(target.substring("http://localhost".length())).cookie(loggedIn))
                .andExpect(status().isOk());
        assertThat(sessions().attribute(loggedIn, SAVED_REQUEST)).as("開いた後は消える").isNull();
    }

    @Test
    void 社内の画面も覚えて戻る() throws Exception {
        String sales = user(staffCompany(), Role.SALES);
        Cookie session = login(sales, null);
        sessions().idle(session, Duration.ofMinutes(31));

        Cookie expired = expiredTo(get(STAFF_URL).cookie(session));

        assertThat(loginAgain(sales, expired)).isEqualTo("http://localhost" + STAFF_URL + "?continue");
    }

    @Test
    void 八時間を過ぎた後に開いた業務の画面へログインし直すと戻る() throws Exception {
        Cookie session = login(email, null);
        atElapsed(Duration.ofHours(8));

        Cookie expired = expiredTo(get(PAGE_URL).cookie(session));

        assertThat(loginAgain(email, expired)).isEqualTo(RETURN_URL);
    }

    @Test
    void 古いCookieのままA03を開くと新しいsessionで1回だけ移り302を繰り返さない() throws Exception {
        // 失効の扱いが新しい session を作らないと、古い Cookie のまま A-03 も失効と判定され続ける
        Cookie session = login(email, null);
        sessions().idle(session, Duration.ofMinutes(31));

        Cookie fresh = expiredTo(get(EXPIRED_URL).cookie(session));

        mvc.perform(get(EXPIRED_URL).cookie(fresh)).andExpect(status().isOk());
        assertThat(sessions().attribute(fresh, SAVED_REQUEST))
                .as("公開の URL は覚えない")
                .isNull();
    }

    // --- 覚えない（覚える場合と同じ流れで対にする。T-119） ---

    @ParameterizedTest(name = "失効した session の送信 {0} は覚えずログインし直すとホームへ移る")
    @ValueSource(strings = {"/customer/transport-requests", "/logout", "/session/extend"})
    void 失効したsessionの送信は覚えずログインし直すとホームへ移る(String url) throws Exception {
        // 業務の送信・ログアウト・延長。同じサイトの Referer があっても覚えない（Referer を使わない）
        Cookie session = login(email, null);
        String token = csrfTokenOf(session);
        sessions().idle(session, Duration.ofMinutes(31));

        Cookie expired = expiredTo(
                post(url).cookie(session).param("_csrf", token).header("Referer", "http://localhost" + PAGE_URL));

        assertThat(loginAgain(email, expired)).isEqualTo(HOME_URL);
    }

    @Test
    void 書類のダウンロードのGETは覚えない() throws Exception {
        Cookie session = login(email, null);
        sessions().idle(session, Duration.ofMinutes(31));

        Cookie expired = expiredTo(get("/customer/transport-requests/TR-2026-0001/versions/1/documents/1")
                .cookie(session));

        assertThat(sessions().attribute(expired, SAVED_REQUEST)).isNull();
        assertThat(loginAgain(email, expired)).isEqualTo(HOME_URL);
    }

    @Test
    void 八時間を過ぎたsessionの業務の送信は覚えずログインし直すとホームへ移る() throws Exception {
        // 8 時間の上限は session が生きているので CSRF を通り、SessionLifetimeFilter が送信を受ける唯一の経路
        Cookie session = login(email, null);
        String token = csrfTokenOf(session);
        atElapsed(Duration.ofHours(8));

        Cookie expired =
                expiredTo(post("/customer/transport-requests").cookie(session).param("_csrf", token));

        assertThat(sessions().attribute(expired, SAVED_REQUEST)).isNull();
        assertThat(loginAgain(email, expired)).isEqualTo(HOME_URL);
    }

    @Test
    void 許す一覧の外のGETは覚えない() throws Exception {
        Cookie session = login(email, null);
        sessions().idle(session, Duration.ofMinutes(31));

        Cookie expired = expiredTo(get(HOME_URL).cookie(session));

        assertThat(sessions().attribute(expired, SAVED_REQUEST)).isNull();
        assertThat(loginAgain(email, expired)).isEqualTo(HOME_URL);
    }

    @Test
    void 利用を止められた後は覚えず利用を戻してもログインし直すとホームへ移る() throws Exception {
        // 取消しでは覚えない（開始準備の決定）。業務の画面を開いていた session で確かめる（T-115）
        Cookie session = login(email, null);
        mvc.perform(get(PAGE_URL).cookie(session)).andExpect(status().isOk());
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", email);

        MvcResult revoked = mvc.perform(get(PAGE_URL).cookie(session))
                .andExpect(redirectedUrl(EXPIRED_URL))
                .andReturn();
        // ブラウザと同じく、応答が新しい session の Cookie を置けばそれを、置かなければ捨てられた Cookie のまま A-03 を開く
        Cookie next = sessionOr(revoked, session);
        Cookie afterRevoke = next == session ? expiredTo(get(EXPIRED_URL).cookie(session)) : next;
        assertThat(sessions().attribute(afterRevoke, SAVED_REQUEST))
                .as("取消しでは覚えない")
                .isNull();
        jdbc.update("UPDATE identity.app_user SET status = 'ACTIVE' WHERE email = ?", email);

        assertThat(loginAgain(email, afterRevoke)).isEqualTo(HOME_URL);
    }

    // --- 持ち越し ---

    @Test
    void 認証コードを誤ってからログインし直しても開いていた画面へ戻る() throws Exception {
        Cookie session = login(email, null);
        sessions().idle(session, Duration.ofMinutes(31));
        Cookie expired = expiredTo(get(PAGE_URL).cookie(session));

        Cookie passwordStage = loginWithPassword(email, expired);
        MvcResult wrong = mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("code", codeAt(email, 10))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(wrong.getResponse().getRedirectedUrl()).isEqualTo(TotpAuthenticationFilter.FAILURE_URL);

        assertThat(loginAgain(email, sessionOr(wrong, passwordStage))).isEqualTo(RETURN_URL);
    }

    @Test
    void passwordの段で5分を過ぎてからログインし直しても開いていた画面へ戻る() throws Exception {
        Cookie session = login(email, null);
        sessions().idle(session, Duration.ofMinutes(31));
        Cookie expired = expiredTo(get(PAGE_URL).cookie(session));
        Cookie passwordStage = loginWithPassword(email, expired);
        atElapsed(Duration.ofMinutes(5));

        Cookie stageExpired = expiredTo(get(TOTP_URL).cookie(passwordStage));

        assertThat(loginAgain(email, stageExpired)).isEqualTo(RETURN_URL);
    }

    // --- 受け入れた振る舞い（確認ポイント 4） ---

    @Test
    void 覚えた要求は利用者に結び付けず別の利用者がログインすると前の画面へ移るが権限で拒まれる() throws Exception {
        // 受け入れた振る舞い（確認ポイント 4）。社内の画面を開いていた session で、荷主がログインし直す
        String sales = user(staffCompany(), Role.SALES);
        Cookie session = login(sales, null);
        sessions().idle(session, Duration.ofMinutes(31));
        Cookie expired = expiredTo(get(STAFF_URL).cookie(session));

        MvcResult back = loginAgainResult(email, expired);

        assertThat(back.getResponse().getRedirectedUrl()).isEqualTo("http://localhost" + STAFF_URL + "?continue");
        mvc.perform(get(STAFF_URL + "?continue").cookie(sessionOr(back, expired)))
                .andExpect(status().isForbidden());
    }

    // --- 戻り先を画面に渡す ---

    @Test
    void GETで開いた業務の画面には戻り先を渡し送信の後の画面には渡さない() throws Exception {
        Cookie session = login(email, null);

        String page = mvc.perform(get(PAGE_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String afterPost = mvc.perform(
                        post("/customer/transport-requests").cookie(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("data-return-path=\"" + PAGE_URL + "\"");
        assertThat(afterPost)
                .as("入力の誤りの画面（送信の URL のまま描き直す）。警告の部分は描かれている（T-119）")
                .contains("data-idle-seconds=")
                .doesNotContain("data-return-path");
    }

    // --- 補助 ---

    /** 失効した request を送り、A-03 へ移ることと、失効の扱いが作った新しい session の Cookie を返す。 */
    private Cookie expiredTo(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result =
                mvc.perform(request).andExpect(redirectedUrl(EXPIRED_URL)).andReturn();
        Cookie fresh = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(fresh).as("失効の扱いが作った新しい session の Cookie").isNotNull();
        return fresh;
    }

    /** その session（Cookie）のまま A-01・A-02 でログインし直し、TOTP の後に移る先を返す。 */
    private String loginAgain(String who, Cookie current) throws Exception {
        return loginAgainResult(who, current).getResponse().getRedirectedUrl();
    }

    /** その session（Cookie）のまま A-01・A-02 でログインし直した、TOTP の送信の応答。 */
    private MvcResult loginAgainResult(String who, Cookie current) throws Exception {
        Cookie passwordStage = loginWithPassword(who, current);
        AuthenticationFactorFixtures.resetTotpStep(jdbc, who);
        return mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("code", currentCode(who))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private Cookie login(String who, Cookie current) throws Exception {
        MvcResult result = loginAgainResult(who, current);
        return sessionOr(result, current);
    }

    private Cookie loginWithPassword(String who, Cookie current) throws Exception {
        var request = post("/login")
                .param("username", who)
                .param("password", PASSWORD)
                .with(csrf());
        MvcResult result = mvc.perform(current == null ? request : request.cookie(current))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        return sessionOr(result, current);
    }

    /** いまの刻みから steps だけ先の刻みのコード（照合の窓（前後 1 つ）に入らない誤ったコード）。 */
    private String codeAt(String who, long steps) {
        TotpCredential credential = totpCredentials
                .findByUserId(
                        users.findByEmail(EmailAddress.of(who)).orElseThrow().id())
                .orElseThrow();
        return totpCodes.code(
                credential.userId(),
                credential.secret(),
                TotpCredential.stepAt(new UtcInstant(clock.instant())) + steps);
    }

    private String currentCode(String who) {
        TotpCredential credential = totpCredentials
                .findByUserId(
                        users.findByEmail(EmailAddress.of(who)).orElseThrow().id())
                .orElseThrow();
        return totpCodes.code(
                credential.userId(), credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private String shipper() {
        return user(company, Role.SHIPPER);
    }

    private Company staffCompany() {
        Company operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        return operator;
    }

    private String user(Company of, Role role) {
        String newEmail = "return-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                of.id(),
                EmailAddress.of(newEmail),
                "利用者",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(role)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return newEmail;
    }

    /** ログインした画面を開いて、session の CSRF のトークンを画面の HTML から取る（ヘッダーのログアウトのフォーム）。 */
    private String csrfTokenOf(Cookie session) throws Exception {
        String html = mvc.perform(get(PAGE_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
                .matcher(html);
        assertThat(matcher.find()).as("画面の CSRF のトークン").isTrue();
        return matcher.group(1);
    }

    /** 応答が新しい session の Cookie を置いていればそれを、なければ今の Cookie を返す。 */
    private static Cookie sessionOr(MvcResult result, Cookie current) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        if (cookie != null) {
            return cookie;
        }
        assertThat(current).as("session の Cookie").isNotNull();
        return current;
    }

    /** 時計をログインの時刻からその時間だけ後に置く（進める量でなく、ログインからの位置）。 */
    private void atElapsed(Duration duration) {
        clock.setInstant(LOGIN_AT.plus(duration));
    }

    private SpringSessionRows sessions() {
        return new SpringSessionRows(jdbc);
    }
}

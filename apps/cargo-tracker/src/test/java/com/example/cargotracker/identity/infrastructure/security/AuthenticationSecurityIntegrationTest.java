package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * セキュリティの統合テスト（US-18 の password の段、ADR-012、テスト戦略「セキュリティの統合テスト」）。
 * 本物のフィルターの連なりと Spring Session JDBC（PostgreSQL 18）の上で、ログイン・拒否・失効・CSRF・役割・監査を確かめる。
 * session は Cookie（{@code SESSION}）で受け渡し、ログインの前後で値が変わることを見る（session の固定化の防止。T-40）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthenticationSecurityIntegrationTest.FixedClock.class})
class AuthenticationSecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-06T00:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

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
    Clock clock;

    private Company shipperCompany;

    @BeforeEach
    void 時計を戻し荷主の企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        shipperCompany = company(true);
    }

    // --- 未認証 ---

    @Test
    void 未認証で荷主の画面を開くとログインへ移り業務データを返さない() throws Exception {
        mvc.perform(get("/customer/transport-requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 未認証で社内の画面を開くとログインへ移る() throws Exception {
        mvc.perform(get("/staff/transport-requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // --- ログインの成功（AC1） ---

    @Test
    void 荷主担当者はログインすると荷主のホームへ移りsessionIDが変わる() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie before = sessionOf(mvc.perform(get("/login")).andReturn());

        MvcResult login = mvc.perform(post("/login")
                        .cookie(before)
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/transport-requests"))
                .andReturn();

        Cookie after = sessionOf(login);
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        mvc.perform(get("/customer/transport-requests").cookie(after)).andExpect(status().isOk());
    }

    @Test
    void 営業担当者はログインすると社内のホームへ移る() throws Exception {
        String email = user(staffCompany(), Role.SALES, UserStatus.ACTIVE);

        mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/staff/transport-requests"));
    }

    @Test
    void ログインの前に開いた画面があればログインの後にそこへ戻る() throws Exception {
        String email = user(staffCompany(), Role.SALES, UserStatus.ACTIVE);
        MvcResult first = mvc.perform(get("/staff/kpi-observations")).andReturn();

        mvc.perform(post("/login")
                        .cookie(sessionOf(first))
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("http://localhost/staff/kpi-observations"));
    }

    @Test
    void メールアドレスは大文字でもログインできる() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        mvc.perform(post("/login")
                        .param("username", "  " + email.toUpperCase(java.util.Locale.ROOT) + " ")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/customer/transport-requests"));
    }

    @Test
    void ログインの成功を監査記録に残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        login(email);

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_SUCCEEDED", "result", "SUCCESS", "reason", "-"));
    }

    // --- ログインの失敗（AC2・AC4） ---

    @Test
    void 誤ったpasswordではログインできず失敗の理由は監査記録にだけ残る() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        assertLoginRejected(email, "wrong password");

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "BAD_CREDENTIALS"));
    }

    @Test
    void 利用停止の利用者は正しいpasswordでもログインできない() throws Exception {
        String email = shipper(UserStatus.SUSPENDED);

        assertLoginRejected(email, PASSWORD);

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "SUSPENDED"));
    }

    @Test
    void 無効な企業の利用者は正しいpasswordでもログインできない() throws Exception {
        String email = user(company(false), Role.SHIPPER, UserStatus.ACTIVE);

        assertLoginRejected(email, PASSWORD);

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "COMPANY_INACTIVE"));
    }

    @Test
    void 利用停止の利用者でもpasswordが誤っていればpasswordの誤りとして扱う() throws Exception {
        String email = shipper(UserStatus.SUSPENDED);

        assertLoginRejected(email, "wrong password");

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "BAD_CREDENTIALS"));
    }

    @Test
    void 存在しないメールアドレスではログインできず監査記録に操作者もメールアドレスも残さない() throws Exception {
        Integer before = unknownUserFailures();

        assertLoginRejected("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD);

        assertThat(unknownUserFailures()).isEqualTo(before + 1);
    }

    @Test
    void 形式の誤ったメールアドレスでも同じ応答にする() throws Exception {
        assertLoginRejected("not-an-email", PASSWORD);
    }

    // --- ログアウト ---

    @Test
    void ログアウトするとログインへ移りsessionが使えなくなり監査記録に残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = login(email);

        mvc.perform(post("/logout").cookie(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().is3xxRedirection());
        assertThat(auditOf(email)).extracting(row -> row.get("action")).containsExactly("LOGIN_SUCCEEDED", "LOGOUT");
    }

    // --- CSRF ---

    @Test
    void CSRFのトークンのないログインは拒否する() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        mvc.perform(post("/login").param("username", email).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFのトークンのない業務のPOSTは拒否する() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        mvc.perform(post("/customer/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    // --- 役割の分離 ---

    @Test
    void 荷主担当者は社内の画面を開けない() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 営業担当者は荷主の画面を開けない() throws Exception {
        Cookie session = login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    // --- 発行から 8 時間（AC5。T-38 の 3 点） ---

    @Test
    void 発行から8時間の1秒前は使える() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        advance(Duration.ofHours(8).minusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void 発行から8時間ちょうどで再認証の案内へ移り業務データを返さない() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        advance(Duration.ofHours(8));

        mvc.perform(get("/customer/transport-requests").cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 発行から8時間の1秒後も再認証の案内へ移り同じsessionは使えない() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        advance(Duration.ofHours(8).plusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
        ((MutableClock) clock).setInstant(LOGIN_AT);
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void 発行から8時間を過ぎたsessionでもログインし直せて上限は新しいログインから数える() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = login(email);
        advance(Duration.ofHours(8));

        MvcResult relogin = mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/customer/transport-requests"))
                .andReturn();

        advance(Duration.ofHours(1));
        mvc.perform(get("/customer/transport-requests").cookie(sessionOf(relogin)))
                .andExpect(status().isOk());
    }

    // --- 無操作 30 分（AC5。T-38 の 3 点。Spring Session の最終アクセスの時刻を戻して確かめる） ---

    @Test
    void 無操作30分の1秒前は使える() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        idle(session, Duration.ofMinutes(30).minusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void 無操作30分ちょうどで再認証の案内へ移る() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        idle(session, Duration.ofMinutes(30));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 無操作30分の1秒後も再認証の案内へ移る() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        idle(session, Duration.ofMinutes(30).plusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 再認証の案内とログインの画面は未認証で開ける() throws Exception {
        mvc.perform(get("/session-expired")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    // --- 応答のヘッダーと Cookie（SEC-17） ---

    @Test
    void 応答はクリックジャッキングと型の推測とスクリプトの読み込みを防ぐ() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'self'"));
    }

    @Test
    void sessionのCookieはスクリプトから読めず別のサイトのPOSTで送られない() throws Exception {
        MvcResult result = mvc.perform(get("/login")).andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith(SESSION_COOKIE + "=")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"));
    }

    // --- 準備 ---

    private Company company(boolean active) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, active);
        companies.add(company);
        return company;
    }

    private Company staffCompany() {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(company);
        return company;
    }

    private String shipper(UserStatus status) {
        return user(shipperCompany, Role.SHIPPER, status);
    }

    private String user(Company company, Role role, UserStatus status) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        users.add(User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of(email),
                "利用者",
                passwordEncoder.encode(PASSWORD),
                status,
                Set.of(role)));
        return email;
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("error");
        return sessionOf(result);
    }

    private void assertLoginRejected(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andReturn();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        if (session != null) {
            mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/login"));
        }
    }

    private static Cookie sessionOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("session の Cookie").isNotNull();
        return cookie;
    }

    private void advance(Duration duration) {
        ((MutableClock) clock).setInstant(LOGIN_AT.plus(duration));
    }

    /** Spring Session の最終アクセスの時刻を、いまから指定の時間だけ前にする（無操作の時間を作る）。 */
    private void idle(Cookie session, Duration duration) {
        String sessionId = new String(java.util.Base64.getDecoder().decode(session.getValue()));
        long lastAccess = Instant.now().minus(duration).toEpochMilli();
        int updated = jdbc.update(
                "UPDATE platform.spring_session SET last_access_time = ?,"
                        + " expiry_time = ? + max_inactive_interval * 1000 WHERE session_id = ?",
                lastAccess,
                lastAccess,
                sessionId);
        assertThat(updated).as("session の行").isEqualTo(1);
    }

    private List<Map<String, String>> auditOf(String email) {
        UUID userId =
                users.findByEmail(EmailAddress.of(email)).orElseThrow().id().value();
        return jdbc.query(
                "SELECT action, result, reason FROM identity.audit_record WHERE actor_user_id = ?"
                        + " ORDER BY occurred_at, action",
                (rs, n) -> Map.of(
                        "action", rs.getString("action"),
                        "result", rs.getString("result"),
                        "reason", rs.getString("reason") == null ? "-" : rs.getString("reason")),
                userId);
    }

    private Integer unknownUserFailures() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'LOGIN_FAILED'"
                        + " AND reason = 'UNKNOWN_USER' AND actor_user_id IS NULL AND actor_company_id IS NULL",
                Integer.class);
    }
}

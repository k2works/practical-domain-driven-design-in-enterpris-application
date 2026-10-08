package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie after = sessionOf(login);
        mvc.perform(get("/").cookie(after)).andExpect(redirectedUrl("/customer/transport-requests"));
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        mvc.perform(get("/customer/transport-requests").cookie(after)).andExpect(status().isOk());
    }

    @Test
    void 営業担当者はログインすると社内のホームへ移る() throws Exception {
        Cookie session = login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/transport-requests"));
    }

    @Test
    void 画面のまだない役割の利用者はログインできてもホームは権限なしになり行き先が循環しない() throws Exception {
        // 経路設計者は Bolt 17 でホームを持った。画面のまだない役割の例を追跡管理者にした
        Cookie session = login(user(staffCompany(), Role.TRACKING_MANAGER, UserStatus.ACTIVE));

        mvc.perform(get("/").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/routing-cases").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 経路設計者はログインすると案件一覧へ移り営業の画面は開けない() throws Exception {
        Cookie session = login(user(staffCompany(), Role.ROUTE_DESIGNER, UserStatus.ACTIVE));

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/routing-cases"));
        mvc.perform(get("/staff/routing-cases").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/kpi-observations").cookie(session)).andExpect(status().isForbidden());
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
                // Spring Security は保存した request へ戻すとき、目印の continue を付ける
                .andExpect(redirectedUrl("http://localhost/staff/kpi-observations?continue"));
    }

    @Test
    void メールアドレスは大文字でもログインできる() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        mvc.perform(post("/login")
                        .param("username", "  " + email.toUpperCase(java.util.Locale.ROOT) + " ")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 長いメールアドレスの利用者もログインできる() throws Exception {
        String local = "long-" + "x".repeat(150);
        String email = user(shipperCompany, Role.SHIPPER, UserStatus.ACTIVE, local + "@example.com");

        login(email);
    }

    @Test
    void ログインの成功を監査記録に残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        login(email);

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_SUCCEEDED", "result", "SUCCESS", "reason", "-"));
    }

    @Test
    void ログインの成功の監査記録に利用者と企業と認証時刻を残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        UUID userId =
                users.findByEmail(EmailAddress.of(email)).orElseThrow().id().value();

        login(email);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT actor_company_id, occurred_at FROM identity.audit_record WHERE actor_user_id = ?", userId);
        assertThat(row).containsEntry("actor_company_id", shipperCompany.id().value());
        assertThat(((java.sql.Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(LOGIN_AT);
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
    void 形式の誤ったメールアドレスでも同じ応答にし存在しないメールアドレスとして記録する() throws Exception {
        Integer before = unknownUserFailures();

        assertLoginRejected("not-an-email", PASSWORD);

        assertThat(unknownUserFailures()).isEqualTo(before + 1);
    }

    // --- ログアウト ---

    @Test
    void ログアウトするとログインへ移りsessionが使えなくなり監査記録に残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = login(email);
        advance(Duration.ofMinutes(1));

        mvc.perform(post("/logout").cookie(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().is3xxRedirection());
        assertThat(auditOf(email)).extracting(row -> row.get("action")).containsExactly("LOGIN_SUCCEEDED", "LOGOUT");
    }

    @Test
    void GETのログアウトではログアウトしない() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        mvc.perform(get("/logout").cookie(session));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    // --- CSRF ---

    @Test
    void CSRFのトークンのないログインは拒否する() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie page = sessionOf(mvc.perform(get("/login")).andReturn());

        mvc.perform(post("/login").cookie(page).param("username", email).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFのトークンが誤った業務のPOSTは拒否する() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        mvc.perform(post("/customer/transport-requests").cookie(session).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionにトークンのない古いフォームの送信は処理せず再認証の案内へ移る() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        mvc.perform(post("/customer/transport-requests").cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/session-expired"));
    }

    // --- 役割の分離 ---

    @Test
    void 荷主担当者は社内の画面を開けない() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 営業担当者と荷主担当者は経路設計の画面を開けず候補の算出と経路の確定を送れない() throws Exception {
        for (Cookie session : List.of(
                login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE)), login(shipper(UserStatus.ACTIVE)))) {
            mvc.perform(get("/staff/routing-cases").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get("/staff/routing-cases/RC-2026-0001").cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/staff/routing-cases/RC-2026-0001/candidates")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
            // 経路の確定（S-07。Bolt 19）も経路設計者だけ
            mvc.perform(get("/staff/routing-cases/RC-2026-0001/confirmation?candidate=1")
                            .cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/staff/routing-cases/RC-2026-0001/confirmation")
                            .param("candidate", "1")
                            .param("rationale", "根拠")
                            .param("expectedVersion", "0")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 営業担当者は荷主の画面を開けない() throws Exception {
        Cookie session = login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 荷主担当者は見積りの提示のPOSTを送れない() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        mvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations/1/presentation")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 営業担当者は荷主の回答のPOSTを送れない() throws Exception {
        Cookie session = login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(post("/customer/transport-requests/TR-2026-0001/quotations/1/response")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 営業担当者と経路設計者は見積りと経路の承認の画面を開けずPOSTも送れない() throws Exception {
        for (Role role : List.of(Role.SALES, Role.ROUTE_DESIGNER)) {
            Cookie session = login(user(staffCompany(), role, UserStatus.ACTIVE));

            mvc.perform(get("/customer/transport-requests/TR-2026-0001/quotations/1/approval")
                            .cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/customer/transport-requests/TR-2026-0001/quotations/1/approval")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 荷主担当者と経路設計者は本予約の確定と予約の詳細を開けずPOSTも送れない() throws Exception {
        for (Role role : List.of(Role.SHIPPER, Role.ROUTE_DESIGNER)) {
            Cookie session =
                    login(user(role == Role.SHIPPER ? shipperCompany : staffCompany(), role, UserStatus.ACTIVE));

            mvc.perform(get("/staff/bookings/new?transportRequest=TR-2026-0001&quotation=1")
                            .cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/staff/bookings/CTABCDEFGH2345").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(post("/staff/bookings")
                            .param("transportRequest", "TR-2026-0001")
                            .param("quotation", "1")
                            .param("staffConfirmed", "true")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 営業担当者はナビの予約を開くと準備中の画面になる() throws Exception {
        Cookie session = login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(get("/staff/bookings").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("この画面は準備中です")));
    }

    @Test
    void CSRFのトークンが誤った本予約の確定は拒否する() throws Exception {
        Cookie session = login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(post("/staff/bookings")
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true")
                        .cookie(session)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFのトークンが誤った見積りの提示は拒否する() throws Exception {
        Cookie session = login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations/1/presentation")
                        .cookie(session)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
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
    void 発行から8時間を過ぎたsessionではログインの送信も受け付けずログインし直すと上限は新しいログインから数える() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = login(email);
        advance(Duration.ofHours(8));

        // 上限の確認は認証の処理より前にある（T-40）。古い session のままのログインの送信も A-03 へ移す
        mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/session-expired"));

        Cookie renewed = login(email);
        advance(Duration.ofHours(8 + 7));
        mvc.perform(get("/customer/transport-requests").cookie(renewed)).andExpect(status().isOk());
    }

    // --- 無操作 30 分（AC5。T-38 の 3 点。Spring Session の最終アクセスの時刻を戻して確かめる） ---

    @Test
    void 無操作30分の1秒前は使える() throws Exception {
        Cookie session = login(shipper(UserStatus.ACTIVE));

        idle(session, Duration.ofMinutes(30).minusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    /**
     * 最終アクセスの時刻を 30 分前に書いてから request を送るまでに数ミリ秒かかるため、判定の時点では 30 分を数ミリ秒過ぎている。
     * ちょうど 30 分の扱い（以上か超えか）は Spring Session の実装に任せ、このテストでは確かめない（T-38）。
     */
    @Test
    void 無操作30分で再認証の案内へ移る() throws Exception {
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
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'; object-src 'none'"));
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
        return user(company, role, status, "user-" + UUID.randomUUID() + "@example.com");
    }

    private String user(Company company, Role role, UserStatus status, String email) {
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
                        + " ORDER BY occurred_at",
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

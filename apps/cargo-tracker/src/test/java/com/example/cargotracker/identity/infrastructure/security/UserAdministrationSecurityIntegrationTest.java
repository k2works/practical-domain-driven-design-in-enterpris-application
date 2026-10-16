package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
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
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S-20 利用者の管理のセキュリティの統合テスト（Bolt 34。US-16 の一部）。本物のフィルターの連なりと PostgreSQL の上で、
 * `/staff/users/**` はシステム管理者だけが開けること、システム管理者のホームと社内のナビ、役割の変更の確定の POST を画面を経ずに
 * 送っても同じ判定で拒むこと、変えた役割が相手の次の request から効くことを確かめる。
 */
class UserAdministrationSecurityIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-16T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String TOTP_URL = "/login/totp";
    private static final String USERS_URL = "/staff/users";

    @Autowired
    MockMvc mvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    AesGcmSecretCipher cipher;

    private Company operator;
    private Company shipper;

    @BeforeEach
    void 時計を戻し企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        shipper = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(shipper);
    }

    // --- 認可とホーム ---

    @Test
    void システム管理者はホームから利用者の一覧へ移り一覧と詳細を開ける() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl(USERS_URL));
        String list = page(session, USERS_URL);
        assertThat(list).contains(target).contains("営業担当者");
        String detail = page(session, USERS_URL + "/" + userIdOf(target).value());
        assertThat(detail).contains(target).contains("A 社");
    }

    @Test
    void システム管理者はほかの社内と荷主の画面を開けない() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));

        for (String url : new String[] {
            "/staff/transport-requests",
            "/staff/bookings",
            "/staff/kpi-observations",
            "/staff/routing-cases",
            "/staff/tracking-records",
            "/customer/transport-requests"
        }) {
            mvc.perform(get(url).cookie(session)).andExpect(status().isForbidden());
        }
    }

    @Test
    void システム管理者のほかの役割は利用者の管理を開けない() throws Exception {
        String target = user(operator, Role.SALES);
        String detailUrl = USERS_URL + "/" + userIdOf(target).value();
        for (Role role : EnumSet.complementOf(EnumSet.of(Role.SYSTEM_ADMIN))) {
            Cookie session = login(user(role == Role.SHIPPER ? shipper : operator, role));

            mvc.perform(get(USERS_URL).cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get(detailUrl).cookie(session)).andExpect(status().isForbidden());
            mvc.perform(post(detailUrl + "/role-changes/confirmation")
                            .cookie(session)
                            .param("roles", Role.ROUTE_DESIGNER.name())
                            .param("version", "0")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(rolesOf(target)).containsExactly("SALES");
    }

    @Test
    void いない利用者の詳細は404() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));

        mvc.perform(get(USERS_URL + "/" + UUID.randomUUID()).cookie(session)).andExpect(status().isNotFound());
    }

    @Test
    void 社内のナビの利用者はシステム管理者にだけ出す() throws Exception {
        String link = "href=\"" + USERS_URL + "\"[^>]*>利用者</a>";
        Cookie admin = login(user(operator, Role.SYSTEM_ADMIN));
        Cookie sales = login(user(operator, Role.SALES));

        assertThat(page(admin, USERS_URL)).containsPattern(link);
        assertThat(page(sales, "/staff/transport-requests")).doesNotContainPattern(link);
    }

    // --- 役割の変更の確定（H1・H3） ---

    @Test
    void 確定のPOSTを画面を経ずに送っても同じ判定で拒み何も書かない() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        String url = USERS_URL + "/" + userIdOf(target).value() + "/role-changes/confirmation";

        String page = mvc.perform(post(url)
                        .cookie(session)
                        .param("roles", Role.SALES.name(), Role.SYSTEM_ADMIN.name())
                        .param("version", "0")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 監督の役割は画面で付け外ししない（Bolt 34 の開発レビューの決定）
        assertThat(page).contains("システム管理者・監査担当者の役割は、この画面では付けたり外したりできません。");
        assertThat(rolesOf(target)).containsExactly("SALES");
        assertThat(rolesChangedOf(target)).isZero();
    }

    @Test
    void 確定のPOSTにもCSRFのトークンが要る() throws Exception {
        // 業務の画面の CSRF の拒否は 403 ではなく案内へ移す（Bolt 32f）。役割も監査も書かないことを確かめる
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        String detailUrl = USERS_URL + "/" + userIdOf(target).value();

        MvcResult rejected = mvc.perform(post(detailUrl + "/role-changes/confirmation")
                        .cookie(session)
                        .param("roles", Role.ROUTE_DESIGNER.name())
                        .param("version", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(rejected.getResponse().getRedirectedUrl()).isNotEqualTo(detailUrl);
        assertThat(rolesOf(target)).containsExactly("SALES");
        assertThat(rolesChangedOf(target)).isZero();
    }

    @Test
    void 変えた役割は相手の次のrequestから効く() throws Exception {
        Cookie admin = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        Cookie targetSession = login(target);
        mvc.perform(get("/staff/transport-requests").cookie(targetSession)).andExpect(status().isOk());

        mvc.perform(post(USERS_URL + "/" + userIdOf(target).value() + "/role-changes/confirmation")
                        .cookie(admin)
                        .param("roles", Role.ROUTE_DESIGNER.name())
                        .param("version", "0")
                        .with(csrf()))
                .andExpect(redirectedUrl(USERS_URL + "/" + userIdOf(target).value()));

        mvc.perform(get("/staff/transport-requests").cookie(targetSession)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/routing-cases").cookie(targetSession)).andExpect(status().isOk());
        assertThat(rolesChangedOf(target)).isEqualTo(1);
    }

    @Test
    void 監査担当者に業務の役割を足すPOSTは職務分離で拒む() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.AUDITOR);

        String page = postChange(session, target, "0", Role.AUDITOR.name(), Role.SALES.name())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("業務データを変更できる役割（営業担当者）は併せて付けられません。");
        assertThat(rolesOf(target)).containsExactly("AUDITOR");
    }

    @Test
    void 自分の詳細には役割のフォームを出さずPOSTでも自分の役割は変えられない() throws Exception {
        String admin = user(operator, Role.SYSTEM_ADMIN);
        Cookie session = login(admin);

        String detail = page(session, USERS_URL + "/" + userIdOf(admin).value());
        assertThat(detail).contains("自分の役割は変更できません。").doesNotContain("変更内容を確認する");
        String page = postChange(session, admin, "0", Role.SYSTEM_ADMIN.name(), Role.SALES.name())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("自分の役割は変更できません。ほかのシステム管理者に依頼してください。");
        assertThat(rolesOf(admin)).containsExactly("SYSTEM_ADMIN");
    }

    @Test
    void 役割を選ばない送信は役割なしで拒む() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        String page = postChange(session, target, "0")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("役割を 1 つ以上選んでください。");
        assertThat(rolesOf(target)).containsExactly("SALES");
    }

    @Test
    void 知らない役割の名前と数でない版と版のない送信は400() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        String url = USERS_URL + "/" + userIdOf(target).value() + "/role-changes/confirmation";

        postChange(session, target, "0", "ROOT").andExpect(status().isBadRequest());
        postChange(session, target, "x", Role.ROUTE_DESIGNER.name()).andExpect(status().isBadRequest());
        mvc.perform(post(url)
                        .cookie(session)
                        .param("roles", Role.ROUTE_DESIGNER.name())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url).cookie(session).param("roles", "ROOT").param("version", "0"))
                .andExpect(status().isBadRequest());
        assertThat(rolesOf(target)).containsExactly("SALES");
    }

    @Test
    void 形の誤った利用者IDは詳細も確認も確定も404() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String url = USERS_URL + "/not-a-uuid";

        mvc.perform(get(url).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(get(url + "/role-changes/confirmation").cookie(session).param("version", "0"))
                .andExpect(status().isNotFound());
        mvc.perform(post(url + "/role-changes/confirmation")
                        .cookie(session)
                        .param("roles", Role.SALES.name())
                        .param("version", "0")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 荷受人の企業の利用者の詳細には役割のフォームを出さない() throws Exception {
        Company consignee = Company.of(new CompanyId(UUID.randomUUID()), "荷受人 A", CompanyKind.CONSIGNEE, true);
        companies.add(consignee);
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(consignee, Role.SHIPPER);

        String detail = page(session, USERS_URL + "/" + userIdOf(target).value());

        assertThat(detail).contains("荷受人の企業の利用者には役割を付けません。").doesNotContain("変更内容を確認する");
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions postChange(
            Cookie session, String target, String version, String... roles) throws Exception {
        return mvc.perform(post(USERS_URL + "/" + userIdOf(target).value() + "/role-changes/confirmation")
                .cookie(session)
                .param("roles", roles)
                .param("version", version)
                .with(csrf()));
    }

    private String page(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String user(Company company, Role role) {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of(email),
                "利用者 " + role.name(),
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(role)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return email;
    }

    private Cookie login(String email) throws Exception {
        MvcResult password = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        MvcResult totp = mvc.perform(post(TOTP_URL)
                        .cookie(sessionOf(password))
                        .param("code", currentCode(email))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(totp.getResponse().getRedirectedUrl()).doesNotContain("error");
        return sessionOf(totp);
    }

    private String currentCode(String email) {
        TotpCredential credential =
                totpCredentials.findByUserId(userIdOf(email)).orElseThrow();
        return totpCodes.code(
                credential.userId(), credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private static Cookie sessionOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("session の Cookie").isNotNull();
        return cookie;
    }

    UserId userIdOf(String email) {
        return jdbc.queryForObject(
                "SELECT id FROM identity.app_user WHERE email = ?",
                (rs, n) -> new UserId(rs.getObject("id", UUID.class)),
                email);
    }

    int rolesChangedOf(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ROLES_CHANGED' AND target_id = ?",
                Integer.class,
                userIdOf(email).value().toString());
    }

    java.util.List<String> rolesOf(String email) {
        return jdbc.queryForList(
                "SELECT role FROM identity.user_role WHERE user_id = ? ORDER BY role",
                String.class,
                userIdOf(email).value());
    }
}

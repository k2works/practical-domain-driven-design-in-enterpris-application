package com.example.cargotracker.identity.infrastructure.security;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.application.internal.session.SessionPaths;
import com.example.cargotracker.identity.domain.events.UserRegistered;
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
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * S-20 の利用者の登録の画面のセキュリティの統合テスト（Bolt 38b3b。US-16 AC1・AC2）。本物のフィルターの連なりと PostgreSQL の上で、
 * 登録の 5 つの URL はシステム管理者だけが開けること、確認・戻る・確定の POST に CSRF のトークンが要ること、確認は何も書かないこと、
 * 範囲外の企業への確定だけを試行として記録すること、重複の文言が管理範囲の内外で同じこと、画面の確定から監査と DE-25 と招待の
 * トークンまで通ること（外側のトランザクションのない POST。Bolt 38b2・38b2c の rollback-only の推論の確かめ）を確かめる。
 */
class UserRegistrationSecurityIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final Instant LOGIN_AT = Instant.parse("2026-10-26T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String NEW_URL = "/staff/users/new";
    private static final String DUPLICATE = "既に登録されています。ほかのメールアドレスを入力してください。";

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
    private Company outside;
    private Company shipper;

    @BeforeEach
    void 時計を戻し企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        shipper = Company.of(new CompanyId(UUID.randomUUID()), "荷主 B", CompanyKind.SHIPPER, true);
        companies.add(shipper);
    }

    private static String newEmail() {
        return "registered-" + UUID.randomUUID() + "@example.com";
    }

    private static MockHttpServletRequestBuilder filled(
            MockHttpServletRequestBuilder request, Company company, String email) {
        return request.param("companyId", company.id().value().toString())
                .param("roles", Role.SALES.name())
                .param("email", email)
                .param("displayName", "新人 花子");
    }

    @Test
    void ほかの役割は登録の5つのURLに入れない() throws Exception {
        // 行 12
        for (Role role : EnumSet.complementOf(EnumSet.of(Role.SYSTEM_ADMIN))) {
            Cookie session = logins().login(user(role == Role.SHIPPER ? shipper : operator, role));
            String email = newEmail();

            mvc.perform(get(NEW_URL).cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get(NEW_URL)
                            .cookie(session)
                            .param("step", "company")
                            .param("companyId", operator.id().value().toString()))
                    .andExpect(status().isForbidden());
            for (String url : List.of(NEW_URL + "/confirmation", NEW_URL + "/form", "/staff/users")) {
                mvc.perform(filled(post(url), operator, email).cookie(session).with(csrf()))
                        .andExpect(status().isForbidden());
            }
            assertThat(usersWith(email)).isZero();
        }
    }

    @Test
    void システム管理者の登録の入口は詳細でなく企業を選ぶ画面() throws Exception {
        // 行 12（/staff/users/new は /staff/users/{id} に落ちない）
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));

        String page = mvc.perform(get(NEW_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("<h1>利用者の登録</h1>").contains("A 社（運用する会社）");
        // 企業を選ぶ GET のフォームに CSRF のトークンを載せない（URL・Referer・アクセスのログに残さない。レイアウトの POST のフォームは別）
        int start = page.indexOf("method=\"get\"");
        assertThat(start).isPositive();
        assertThat(page.substring(start, page.indexOf("</form>", start))).doesNotContain("_csrf");
    }

    @Test
    void 確認と戻ると確定のPOSTにCSRFのトークンが要り何も書かない() throws Exception {
        // 行 12（業務の画面の CSRF の拒否は案内へ移し、その session を終える。Bolt 32f。URL ごとにログインし直す）
        String admin = user(operator, Role.SYSTEM_ADMIN);
        String email = newEmail();
        int audits = registeredBy(admin);

        for (String url : List.of(NEW_URL + "/confirmation", NEW_URL + "/form", "/staff/users")) {
            Cookie session = logins().login(admin);
            MvcResult rejected = mvc.perform(filled(post(url), operator, email).cookie(session))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat(rejected.getResponse().getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
        }
        assertThat(usersWith(email)).isZero();
        assertThat(registeredBy(admin)).isEqualTo(audits);
    }

    @Test
    void 確認は何も書かず範囲外も記録しない() throws Exception {
        // 行 6・行 8
        String admin = user(operator, Role.SYSTEM_ADMIN);
        Cookie session = logins().login(admin);
        String email = newEmail();

        String page = mvc.perform(filled(post(NEW_URL + "/confirmation"), operator, email)
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(filled(post(NEW_URL + "/confirmation"), outside, email)
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(page).contains("<h1>利用者を登録しますか</h1>");
        assertThat(usersWith(email)).isZero();
        assertThat(registeredBy(admin)).isZero();
        assertThat(scopeDenialsFor(outside)).isZero();
    }

    @Test
    void 入力の画面のGETと戻るの範囲外は404で記録しない() throws Exception {
        // 行 5・行 10（GET と戻るは範囲外を記録しない。記録するのは確定だけ）
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));

        mvc.perform(get(NEW_URL)
                        .cookie(session)
                        .param("step", "company")
                        .param("companyId", outside.id().value().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(filled(post(NEW_URL + "/form"), outside, newEmail())
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(scopeDenialsFor(outside)).isZero();
    }

    @Test
    void 範囲外の企業への確定は404で試行を1行記録する() throws Exception {
        // 行 11
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String email = newEmail();

        mvc.perform(filled(post("/staff/users"), outside, email).cookie(session).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(usersWith(email)).isZero();
        assertThat(scopeDenialsFor(outside)).isEqualTo(1);
    }

    @Test
    void 範囲外の企業への確定は入力の誤りや知らない役割があっても404で試行を1行記録する() throws Exception {
        // 行 11（Bolt 38b3b の開発レビューの高-1。値は管理範囲の判定の後に解釈する。前例 ui_design の利用状態の変更）
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));

        mvc.perform(post("/staff/users")
                        .cookie(session)
                        .with(csrf())
                        .param("companyId", outside.id().value().toString())
                        .param("roles", Role.SALES.name())
                        .param("email", "")
                        .param("displayName", "新人 花子"))
                .andExpect(status().isNotFound());
        assertThat(scopeDenialsFor(outside)).isEqualTo(1);

        mvc.perform(post("/staff/users")
                        .cookie(session)
                        .with(csrf())
                        .param("companyId", outside.id().value().toString())
                        .param("roles", "NOT_A_ROLE")
                        .param("email", newEmail())
                        .param("displayName", "新人 花子"))
                .andExpect(status().isNotFound());
        assertThat(scopeDenialsFor(outside)).isEqualTo(2);
    }

    @Test
    void 重複のメールアドレスは既にいる利用者の企業が管理範囲の内でも外でも同じ文で拒む() throws Exception {
        // 行 9（Bolt 38b の決定 (2)。範囲外の利用者の存在が分かることは受け入れた）
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String inside = user(operator, Role.SALES);
        String stranger = user(outside, Role.SALES);

        for (String taken : List.of(inside, stranger)) {
            String page = mvc.perform(filled(post("/staff/users"), operator, taken)
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(page).contains("メールアドレス: " + DUPLICATE);
        }
    }

    @Test
    void 画面の確定で登録し監査とDE25と招待のトークンまで通る() throws Exception {
        // 行 13（外側のトランザクションのない POST。発行記録の完了を待ってからトークンを読む。T-209）
        String admin = user(operator, Role.SYSTEM_ADMIN);
        Cookie session = logins().login(admin);
        String email = newEmail();

        String location = mvc.perform(filled(post("/staff/users"), operator, email)
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        UserId registered =
                users.findByEmail(EmailAddress.of(email)).orElseThrow().id();
        assertThat(location).isEqualTo("/staff/users/" + registered.value());
        assertThat(registeredBy(admin)).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM platform.event_publication WHERE event_type = ?"
                                        + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                                Integer.class,
                                UserRegistered.class.getName(),
                                "%" + registered.value() + "%")
                        == 1);
        assertThat(jdbc.queryForList(
                        "SELECT purpose FROM identity.password_reset_token WHERE user_id = ?",
                        String.class,
                        registered.value()))
                .containsExactly("USER_INVITATION");
    }

    // --- helpers ---

    private int usersWith(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM identity.app_user WHERE email = ?", Integer.class, email);
    }

    private int registeredBy(String admin) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'USER_REGISTERED' AND actor_user_id = ?",
                Integer.class,
                logins().userIdOf(admin).value());
    }

    private int scopeDenialsFor(Company company) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ADMIN_SCOPE_DENIED' AND target_id = ?",
                Integer.class,
                company.id().value().toString());
    }

    private String user(Company company, Role role) {
        String email = "registration-test-" + UUID.randomUUID() + "@example.com";
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
        if (role == Role.SYSTEM_ADMIN) {
            // 管理範囲は A 社だけ（範囲外の会社は割り当てない）
            AdministrationFixtures.assignCompany(jdbc, userId, operator.id());
        }
        return email;
    }

    private IdentityLogins logins() {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock);
    }
}

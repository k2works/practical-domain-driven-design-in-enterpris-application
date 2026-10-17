package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AdministrationFixtures;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * S-20 の利用停止と再開のセキュリティの統合テスト（Bolt 34d。US-16 AC5、UC-16 の 5a、IA-INV-01・04・14）。本物のフィルターの連なりと
 * PostgreSQL の上で、確定の POST を画面を経ずに送っても同じ判定で拒むこと、範囲外の試行の記録、止めた利用者の既存の session が次の
 * request で捨てられること、確認の GET が何も書かないことを確かめる。文言は画面の層の受入シナリオで確かめる（T-145）。
 */
class UserStatusChangeSecurityIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-17T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String TOTP_URL = "/login/totp";
    private static final String USERS_URL = "/staff/users";
    private static final String EXPIRED_URL = "/session-expired";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String ACTIVE = "ACTIVE";
    private static final String TRANSFERRED = "TRANSFERRED";

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

    // --- 停止と再開（H1） ---

    @Test
    void 停止の確定の後に相手の既存のsessionの次のrequestはA03に移りSESSION_REVOKEDが残る() throws Exception {
        Cookie admin = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(shipper, Role.SHIPPER);
        Cookie targetSession = login(target);
        mvc.perform(get("/customer/transport-requests").cookie(targetSession)).andExpect(status().isOk());

        postStatus(admin, target, SUSPENDED, TRANSFERRED, "0").andExpect(redirectedUrl(detailUrl(target)));

        assertThat(statusOf(target)).containsEntry("status", SUSPENDED).containsEntry("version", 1L);
        assertThat(audits(target, "USER_SUSPENDED")).isEqualTo(1);
        mvc.perform(get("/customer/transport-requests").cookie(targetSession)).andExpect(redirectedUrl(EXPIRED_URL));
        assertThat(revocations(target)).containsExactly(Map.of("result", "FAILURE", "reason", SUSPENDED));
    }

    @Test
    void 停止した利用者はログインできず再開するとログインでき過去の監査の行は残る() throws Exception {
        Cookie admin = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        login(target);
        int before = auditsByActor(target);

        postStatus(admin, target, SUSPENDED, "USAGE_ENDED", "0").andExpect(status().is3xxRedirection());
        mvc.perform(post("/login")
                        .param("username", target)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error"));
        postStatus(admin, target, ACTIVE, null, "1").andExpect(redirectedUrl(detailUrl(target)));

        assertThat(statusOf(target)).containsEntry("status", ACTIVE).containsEntry("version", 2L);
        assertThat(audits(target, "USER_REACTIVATED")).isEqualTo(1);
        login(target);
        assertThat(auditsByActor(target)).as("相手の過去の操作の記録は残る（AC5）").isGreaterThan(before);
    }

    // --- 確定の POST を画面を経ずに送る（H3） ---

    @Test
    void 自分の利用状態の確定は拒み何も書かない() throws Exception {
        String admin = user(operator, Role.SYSTEM_ADMIN);
        Cookie session = login(admin);

        postStatus(session, admin, SUSPENDED, TRANSFERRED, "0").andExpect(status().isOk());

        assertThat(statusOf(admin)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
        assertThat(statusAudits(admin)).isZero();
    }

    @Test
    void 監督の役割を持つ利用者の停止と再開の確定は拒み何も書かない() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String auditor = user(operator, Role.AUDITOR);
        String otherAdmin = user(operator, Role.SYSTEM_ADMIN);
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", otherAdmin);

        postStatus(session, auditor, SUSPENDED, TRANSFERRED, "0").andExpect(status().isOk());
        postStatus(session, otherAdmin, ACTIVE, null, "0").andExpect(status().isOk());

        assertThat(statusOf(auditor)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
        assertThat(statusOf(otherAdmin)).containsEntry("status", SUSPENDED).containsEntry("version", 0L);
        assertThat(statusAudits(auditor) + statusAudits(otherAdmin)).isZero();
    }

    @Test
    void 同じ状態と理由のない停止と古い版の確定は拒み何も書かない() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        postStatus(session, target, ACTIVE, null, "0").andExpect(status().isOk());
        postStatus(session, target, SUSPENDED, null, "0").andExpect(status().isOk());
        postStatus(session, target, SUSPENDED, TRANSFERRED, "5").andExpect(status().isOk());

        assertThat(statusOf(target)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
        assertThat(statusAudits(target)).isZero();
    }

    @Test
    void 知らない状態と理由の名前と再開に理由を付けた送信は400() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        String url = detailUrl(target) + "/status-changes/confirmation";

        postStatus(session, target, "DELETED", null, "0").andExpect(status().isBadRequest());
        postStatus(session, target, SUSPENDED, "BORED", "0").andExpect(status().isBadRequest());
        postStatus(session, target, ACTIVE, TRANSFERRED, "0").andExpect(status().isBadRequest());
        mvc.perform(get(url).cookie(session).param("status", "DELETED").param("version", "0"))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(target)).containsEntry("status", ACTIVE);
        assertThat(statusAudits(target)).isZero();
    }

    @Test
    void システム管理者のほかの役割は確定できない() throws Exception {
        String target = user(operator, Role.SALES);
        Cookie session = login(user(operator, Role.CUSTOMER_SUPPORT));

        postStatus(session, target, SUSPENDED, TRANSFERRED, "0").andExpect(status().isForbidden());

        assertThat(statusOf(target)).containsEntry("status", ACTIVE);
    }

    @Test
    void 確定のPOSTにもCSRFのトークンが要る() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        MvcResult rejected = mvc.perform(post(detailUrl(target) + "/status-changes/confirmation")
                        .cookie(session)
                        .param("status", SUSPENDED)
                        .param("reason", TRANSFERRED)
                        .param("version", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(rejected.getResponse().getRedirectedUrl()).isNotEqualTo(detailUrl(target));
        assertThat(statusOf(target)).containsEntry("status", ACTIVE);
        assertThat(statusAudits(target)).isZero();
    }

    // --- 管理範囲（IA-INV-14）と確認の GET（T-144・T-115） ---

    @Test
    void 範囲外の利用者の確認も確定も404で確定の試行だけが1行残り利用状態は変わらない() throws Exception {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String stranger = user(outside, Role.SALES);

        mvc.perform(get(detailUrl(stranger) + "/status-changes/confirmation")
                        .cookie(session)
                        .param("status", SUSPENDED)
                        .param("reason", TRANSFERRED)
                        .param("version", "0"))
                .andExpect(status().isNotFound());
        assertThat(scopeDenialsOf(stranger)).as("確認の GET は記録しない").isZero();
        postStatus(session, stranger, SUSPENDED, TRANSFERRED, "0").andExpect(status().isNotFound());

        assertThat(scopeDenialsOf(stranger)).isEqualTo(1);
        assertThat(statusOf(stranger)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
    }

    @Test
    void 受け付けられる停止の確認のGETは何も書かない() throws Exception {
        Cookie session = login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        mvc.perform(get(detailUrl(target) + "/status-changes/confirmation")
                        .cookie(session)
                        .param("status", SUSPENDED)
                        .param("reason", TRANSFERRED)
                        .param("version", "0"))
                .andExpect(status().isOk());

        assertThat(statusOf(target)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
        assertThat(statusAudits(target)).isZero();
    }

    @Test
    void 荷受人の企業の利用者も止められる() throws Exception {
        Company consignee = Company.of(new CompanyId(UUID.randomUUID()), "荷受人 A", CompanyKind.CONSIGNEE, true);
        companies.add(consignee);
        String admin = user(operator, Role.SYSTEM_ADMIN);
        AdministrationFixtures.assignCompany(jdbc, userIdOf(admin), consignee.id());
        Cookie session = login(admin);
        String target = user(consignee, Role.SHIPPER);

        postStatus(session, target, SUSPENDED, "EMERGENCY", "0").andExpect(redirectedUrl(detailUrl(target)));

        assertThat(statusOf(target)).containsEntry("status", SUSPENDED);
    }

    // --- helpers ---

    private ResultActions postStatus(Cookie session, String target, String status, String reason, String version)
            throws Exception {
        var request = post(detailUrl(target) + "/status-changes/confirmation")
                .cookie(session)
                .param("status", status)
                .param("version", version)
                .with(csrf());
        if (reason != null) {
            request.param("reason", reason);
        }
        return mvc.perform(request);
    }

    private String detailUrl(String email) {
        return USERS_URL + "/" + userIdOf(email).value();
    }

    private Map<String, Object> statusOf(String email) {
        return jdbc.queryForMap("SELECT status, version FROM identity.app_user WHERE email = ?", email);
    }

    private int audits(String email, String action) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = ? AND target_id = ?",
                Integer.class,
                action,
                userIdOf(email).value().toString());
    }

    private int statusAudits(String email) {
        return audits(email, "USER_SUSPENDED") + audits(email, "USER_REACTIVATED");
    }

    private int scopeDenialsOf(String email) {
        return audits(email, "ADMIN_SCOPE_DENIED");
    }

    private int auditsByActor(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE actor_user_id = ?",
                Integer.class,
                userIdOf(email).value());
    }

    private java.util.List<Map<String, Object>> revocations(String email) {
        return jdbc.queryForList(
                "SELECT result, reason FROM identity.audit_record WHERE action = 'SESSION_REVOKED' AND actor_user_id = ?",
                userIdOf(email).value());
    }

    private String user(Company company, Role role) {
        String email = "status-test-" + UUID.randomUUID() + "@example.com";
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
            AdministrationFixtures.assignCompany(jdbc, userId, operator.id());
            AdministrationFixtures.assignCompany(jdbc, userId, shipper.id());
        }
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
        // 同じ TOTP の刻みのコードを 2 度使わない（IA-INV-10）。次のログインのために時計を 1 刻み進める
        ((MutableClock) clock).setInstant(clock.instant().plusSeconds(30));
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

    private UserId userIdOf(String email) {
        return jdbc.queryForObject(
                "SELECT id FROM identity.app_user WHERE email = ?",
                (rs, n) -> new UserId(rs.getObject("id", UUID.class)),
                email);
    }
}

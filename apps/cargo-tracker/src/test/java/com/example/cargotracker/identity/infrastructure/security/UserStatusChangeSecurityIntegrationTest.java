package com.example.cargotracker.identity.infrastructure.security;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.SpringSessionRows;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
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

    private static final Instant LOGIN_AT = Instant.parse("2026-10-17T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
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
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(shipper, Role.SHIPPER);
        Cookie targetSession = logins().login(target);
        mvc.perform(get("/customer/transport-requests").cookie(targetSession)).andExpect(status().isOk());

        postStatus(admin, target, SUSPENDED, TRANSFERRED, "0").andExpect(redirectedUrl(detailUrl(target)));

        assertThat(statusOf(target)).containsEntry("status", SUSPENDED).containsEntry("version", 1L);
        assertThat(audits(target, "USER_SUSPENDED")).isEqualTo(1);
        mvc.perform(get("/customer/transport-requests").cookie(targetSession)).andExpect(redirectedUrl(EXPIRED_URL));
        assertThat(revocations(target)).containsExactly(Map.of("result", "FAILURE", "reason", SUSPENDED));
    }

    @Test
    void 停止した利用者はログインできず再開するとログインでき過去の監査の行は残る() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        logins().login(target);
        java.util.List<UUID> before = auditIdsByActor(target);
        assertThat(before).as("相手のログインの記録がある").isNotEmpty();

        postStatus(admin, target, SUSPENDED, "USAGE_ENDED", "0").andExpect(status().is3xxRedirection());
        mvc.perform(post("/login")
                        .param("username", target)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error"));
        postStatus(admin, target, ACTIVE, null, "1").andExpect(redirectedUrl(detailUrl(target)));

        assertThat(statusOf(target)).containsEntry("status", ACTIVE).containsEntry("version", 2L);
        assertThat(audits(target, "USER_REACTIVATED")).isEqualTo(1);
        logins().login(target);
        assertThat(auditIdsByActor(target)).as("相手の過去の操作の記録は残る（AC5）").containsAll(before);
    }

    // --- 確定の POST を画面を経ずに送る（H3） ---

    @Test
    void 自分の利用状態の確定は拒み何も書かない() throws Exception {
        String admin = user(operator, Role.SYSTEM_ADMIN);
        Cookie session = logins().login(admin);

        postStatus(session, admin, SUSPENDED, TRANSFERRED, "0").andExpect(status().isOk());

        assertThat(statusOf(admin)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
        assertThat(statusAudits(admin)).isZero();
    }

    @Test
    void 監督の役割を持つ利用者の停止と再開の確定は拒み何も書かない() throws Exception {
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
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
    void 同じ状態への確定は拒み何も書かない() throws Exception {
        assertRefusedWithoutWriting(ACTIVE, null, "0");
    }

    @Test
    void 理由のない停止の確定は拒み何も書かない() throws Exception {
        assertRefusedWithoutWriting(SUSPENDED, null, "0");
    }

    @Test
    void 古い版からの確定は拒み何も書かない() throws Exception {
        assertRefusedWithoutWriting(SUSPENDED, TRANSFERRED, "5");
    }

    private void assertRefusedWithoutWriting(String to, String reason, String version) throws Exception {
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        postStatus(session, target, to, reason, version).andExpect(status().isOk());

        assertThat(statusOf(target)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
        assertThat(statusAudits(target)).isZero();
    }

    // --- 開発レビューの後の決定（2026-10-17、human:kakimomokuri） ---

    @Test
    void 相手が操作しないうちに停止して再開すると前のsessionは使えない() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        Cookie targetSession = logins().login(target);
        SpringSessionRows sessions = new SpringSessionRows(jdbc);

        postStatus(admin, target, SUSPENDED, "EMERGENCY", "0").andExpect(status().is3xxRedirection());
        assertThat(sessions.exists(targetSession)).as("停止ではすぐに消さない（決定 2）").isTrue();
        postStatus(admin, target, ACTIVE, null, "1").andExpect(status().is3xxRedirection());

        assertThat(sessions.exists(targetSession)).as("再開の確定の後に前の session を消した").isFalse();
        MvcResult next = mvc.perform(get("/staff/transport-requests").cookie(targetSession))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(next.getResponse().getRedirectedUrl()).isNotNull();
    }

    @Test
    void 停止では相手の未使用の再設定のトークンを無効にする() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        UUID token = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO identity.password_reset_token"
                        + " (id, request_id, user_id, token_hash, issued_at, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                token,
                UUID.randomUUID(),
                logins().userIdOf(target).value(),
                "hash-" + token,
                java.sql.Timestamp.from(clock.instant()),
                java.sql.Timestamp.from(clock.instant().plusSeconds(1800)));

        postStatus(admin, target, SUSPENDED, TRANSFERRED, "0").andExpect(status().is3xxRedirection());

        assertThat(jdbc.queryForObject(
                        "SELECT invalidated_at IS NOT NULL FROM identity.password_reset_token WHERE id = ?",
                        Boolean.class,
                        token))
                .as("再開の後に、停止の前のリンクで設定し直させない")
                .isTrue();
    }

    @Test
    void 知らない状態と理由の名前と再開に理由を付けた送信は400() throws Exception {
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
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
        Cookie session = logins().login(user(operator, Role.CUSTOMER_SUPPORT));

        postStatus(session, target, SUSPENDED, TRANSFERRED, "0").andExpect(status().isForbidden());

        assertThat(statusOf(target)).containsEntry("status", ACTIVE);
    }

    @Test
    void 確定のPOSTにもCSRFのトークンが要る() throws Exception {
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
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
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
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
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
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
        AdministrationFixtures.assignCompany(jdbc, logins().userIdOf(admin), consignee.id());
        Cookie session = logins().login(admin);
        String target = user(consignee, Role.SHIPPER);

        postStatus(session, target, SUSPENDED, "EMERGENCY", "0").andExpect(redirectedUrl(detailUrl(target)));

        assertThat(statusOf(target)).containsEntry("status", SUSPENDED);
    }

    @Test
    void 詳細を読んだ後に管理範囲から外れた確認は404で何も書かず確定は404で試行を1行書く() throws Exception {
        String admin = user(operator, Role.SYSTEM_ADMIN);
        Cookie session = logins().login(admin);
        String target = user(operator, Role.SALES);
        // 1 回目（controller の詳細の読み出し）は範囲内、2 回目（利用状態の変更の判定）は範囲外
        doReturn(new AdministrationScope(Set.of(operator.id())))
                .doReturn(new AdministrationScope(Set.of()))
                .doReturn(new AdministrationScope(Set.of(operator.id())))
                .doReturn(new AdministrationScope(Set.of()))
                .when(assignments)
                .scopeOf(logins().userIdOf(admin));

        mvc.perform(get(detailUrl(target) + "/status-changes/confirmation")
                        .cookie(session)
                        .param("status", SUSPENDED)
                        .param("reason", TRANSFERRED)
                        .param("version", "0"))
                .andExpect(status().isNotFound());
        assertThat(scopeDenialsOf(target)).as("確認の GET は記録しない").isZero();
        postStatus(session, target, SUSPENDED, TRANSFERRED, "0").andExpect(status().isNotFound());

        assertThat(scopeDenialsOf(target)).isEqualTo(1);
        assertThat(statusOf(target)).containsEntry("status", ACTIVE).containsEntry("version", 0L);
    }

    @Test
    void 範囲外の利用者への版の誤った確定も試行を記録してから404にする() throws Exception {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String stranger = user(outside, Role.SALES);

        postStatus(session, stranger, SUSPENDED, TRANSFERRED, "x").andExpect(status().isNotFound());

        assertThat(scopeDenialsOf(stranger)).isEqualTo(1);
    }

    @Test
    void 範囲内の利用者への版の誤った確定と詳細の知らない理由は400() throws Exception {
        Cookie session = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        postStatus(session, target, SUSPENDED, TRANSFERRED, "x").andExpect(status().isBadRequest());
        mvc.perform(get(detailUrl(target)).cookie(session).param("reason", "BORED"))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(target)).containsEntry("status", ACTIVE);
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
        return USERS_URL + "/" + logins().userIdOf(email).value();
    }

    private Map<String, Object> statusOf(String email) {
        return jdbc.queryForMap("SELECT status, version FROM identity.app_user WHERE email = ?", email);
    }

    private int audits(String email, String action) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = ? AND target_id = ?",
                Integer.class,
                action,
                logins().userIdOf(email).value().toString());
    }

    private int statusAudits(String email) {
        return audits(email, "USER_SUSPENDED") + audits(email, "USER_REACTIVATED");
    }

    private int scopeDenialsOf(String email) {
        return audits(email, "ADMIN_SCOPE_DENIED");
    }

    private java.util.List<UUID> auditIdsByActor(String email) {
        return jdbc.queryForList(
                "SELECT id FROM identity.audit_record WHERE actor_user_id = ?",
                UUID.class,
                logins().userIdOf(email).value());
    }

    private java.util.List<Map<String, Object>> revocations(String email) {
        return jdbc.queryForList(
                "SELECT result, reason FROM identity.audit_record WHERE action = 'SESSION_REVOKED' AND actor_user_id = ?",
                logins().userIdOf(email).value());
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

    private IdentityLogins logins() {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock);
    }
}

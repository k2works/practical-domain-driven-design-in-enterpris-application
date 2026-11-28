package com.example.cargotracker.identity.infrastructure.security;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.SpringSessionRows;
import com.example.cargotracker.identity.application.internal.session.SessionPaths;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * S-20 のログインの資格（ロックの解除と管理者による TOTP の解除）のセキュリティの統合テスト（Bolt 38c。US-18 AC3・AC9、IA-INV-01・03・04・
 * 10・11・14。行 2・3・5・6・9・11・12）。本物のフィルターの連なりと PostgreSQL の上で、確定の POST を画面を経ずに送っても同じ判定で拒む
 * こと、範囲外の試行の記録、解除した利用者の既存の session の扱い、確認の GET が何も書かないことを確かめる。文言は画面の層で確かめる
 * （T-145）。
 */
class UserLoginCredentialSecurityIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final Instant LOGIN_AT = Instant.parse("2026-11-09T01:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String USERS_URL = "/staff/users";
    private static final String UNLOCKS = "/unlocks/confirmation";
    private static final String TOTP_RESETS = "/totp-resets/confirmation";
    private static final String VERIFIED_AT = "2026-11-09 09:30";
    private static final String RECOVERY_CODE = "1a2b-3c4d";
    private static final String TOTP_ERROR = "/login?totp-error";

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

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    RecoveryCodeHasher hasher;

    private Company operator;

    @BeforeEach
    void 時計を戻し企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
    }

    // --- ロックの解除（行 2・3） ---

    @Test
    void ロックの解除の確認のGETは何も書かず確定で解除しすぐにログインでき相手のsessionは残る() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        Cookie targetSession = logins().login(target);
        lock(target);

        mvc.perform(get(detailUrl(target) + UNLOCKS).cookie(admin)).andExpect(status().isOk());
        assertThat(lockedUntilOf(target)).as("確認の GET は書かない").isNotNull();
        assertThat(audits(target, "ACCOUNT_UNLOCKED")).isZero();

        postUnlock(admin, target).andExpect(redirectedUrl(detailUrl(target)));

        assertThat(lockedUntilOf(target)).isNull();
        assertThat(audits(target, "ACCOUNT_UNLOCKED")).isEqualTo(1);
        assertThat(new SpringSessionRows(jdbc).exists(targetSession))
                .as("ロックの解除では session を消さない")
                .isTrue();
        logins().login(target);
    }

    @Test
    void ロック中でない利用者の解除の確定は詳細に戻し何も書かない() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        postUnlock(admin, target).andExpect(status().isOk());

        assertThat(audits(target, "ACCOUNT_UNLOCKED")).isZero();
    }

    // --- TOTP の解除（行 5・6） ---

    @Test
    void TOTPの解除の確認のGETは何も書かず確定で資格と回復コードを消し相手のsessionはA03に移る() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        Cookie targetSession = logins().login(target);
        lock(target);
        recoveryCode(target);
        mvc.perform(get("/staff/transport-requests").cookie(targetSession)).andExpect(status().isOk());

        mvc.perform(get(detailUrl(target) + TOTP_RESETS).cookie(admin)).andExpect(status().isOk());
        assertThat(countOf("identity.totp_credential", target))
                .as("確認の GET は書かない")
                .isOne();

        postTotpReset(admin, target, enrolledAtOf(target), "PHONE_CALLBACK", VERIFIED_AT)
                .andExpect(redirectedUrl(detailUrl(target)));

        assertThat(countOf("identity.totp_credential", target)).isZero();
        assertThat(countOf("identity.recovery_code", target)).isZero();
        assertThat(lockedUntilOf(target)).as("ロックは触らない").isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT after_state FROM identity.audit_record WHERE action = 'TOTP_RESET_BY_ADMIN' AND target_id = ?",
                        String.class,
                        logins().userIdOf(target).value().toString()))
                .isEqualTo("method=PHONE_CALLBACK;verifiedAt=2026-11-09T00:30:00Z");
        mvc.perform(get("/staff/transport-requests").cookie(targetSession))
                .andExpect(redirectedUrl(SessionPaths.EXPIRED_URL));
    }

    @Test
    void TOTPを解除した利用者は次のログインで認証アプリの登録に移る() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        postTotpReset(admin, target, enrolledAtOf(target), "IN_PERSON", VERIFIED_AT)
                .andExpect(status().is3xxRedirection());

        Cookie password = logins().loginWithPassword(target);
        mvc.perform(get(IdentityLogins.TOTP_URL).cookie(password)).andExpect(redirectedUrl("/login/totp/enroll"));
    }

    @Test
    void 本人確認の手段と日時の誤りは確認の画面に戻し何も書かない() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        String enrolledAt = enrolledAtOf(target);

        postTotpReset(admin, target, enrolledAt, null, null).andExpect(status().isOk());
        postTotpReset(admin, target, enrolledAt, "IN_PERSON", "2026/11/09 9:30").andExpect(status().isOk());
        postTotpReset(admin, target, enrolledAt, "IN_PERSON", "2026-11-09 10:01")
                .andExpect(status().isOk());
        postTotpReset(admin, target, enrolledAt, "IN_PERSON", "2026-11-01 09:59")
                .andExpect(status().isOk());

        assertThat(countOf("identity.totp_credential", target)).isOne();
        assertThat(audits(target, "TOTP_RESET_BY_ADMIN")).isZero();
    }

    @Test
    void 知らない手段の名前と形の誤った登録の時刻は400() throws Exception {
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);

        postTotpReset(admin, target, enrolledAtOf(target), "TELEPATHY", VERIFIED_AT)
                .andExpect(status().isBadRequest());
        postTotpReset(admin, target, "yesterday", "IN_PERSON", VERIFIED_AT).andExpect(status().isBadRequest());

        assertThat(countOf("identity.totp_credential", target)).isOne();
    }

    // --- 自分と監督の役割（行 9） ---

    @Test
    void 自分と監督の役割の利用者の確定は拒み何も書かない() throws Exception {
        String self = user(operator, Role.SYSTEM_ADMIN);
        Cookie admin = logins().login(self);
        String auditor = user(operator, Role.AUDITOR);
        lock(self);
        lock(auditor);

        postUnlock(admin, self).andExpect(status().isOk());
        postUnlock(admin, auditor).andExpect(status().isOk());
        postTotpReset(admin, auditor, enrolledAtOf(auditor), "IN_PERSON", VERIFIED_AT)
                .andExpect(status().isOk());

        assertThat(lockedUntilOf(self)).isNotNull();
        assertThat(lockedUntilOf(auditor)).isNotNull();
        assertThat(countOf("identity.totp_credential", auditor)).isOne();
        assertThat(audits(self, "ACCOUNT_UNLOCKED")
                        + audits(auditor, "ACCOUNT_UNLOCKED")
                        + audits(auditor, "TOTP_RESET_BY_ADMIN"))
                .isZero();
    }

    // --- 管理範囲 × 誤った値（行 11。T-211） ---

    @Test
    void 範囲外の利用者の確認は404で記録せず確定は値の誤りによらず404で試行を1行ずつ書く() throws Exception {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String stranger = user(outside, Role.SALES);
        lock(stranger);

        mvc.perform(get(detailUrl(stranger) + UNLOCKS).cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get(detailUrl(stranger) + TOTP_RESETS).cookie(admin)).andExpect(status().isNotFound());
        assertThat(audits(stranger, "ADMIN_SCOPE_DENIED")).as("確認の GET は記録しない").isZero();

        postUnlock(admin, stranger).andExpect(status().isNotFound());
        postTotpReset(admin, stranger, "yesterday", "TELEPATHY", "x").andExpect(status().isNotFound());
        postTotpReset(admin, stranger, enrolledAtOf(stranger), null, null).andExpect(status().isNotFound());

        assertThat(audits(stranger, "ADMIN_SCOPE_DENIED")).isEqualTo(3);
        assertThat(lockedUntilOf(stranger)).isNotNull();
        assertThat(countOf("identity.totp_credential", stranger)).isOne();
    }

    // --- 認可と CSRF（行 12） ---

    @Test
    void TOTPの解除の後は解除の前に発行した回復コードでA02を通れない() throws Exception {
        // 行 6（回復コードの認証は TOTP の資格を見ないので、回復コードも消す。開始準備の決定 (3)）。本物のハッシュで置く
        Cookie admin = logins().login(user(operator, Role.SYSTEM_ADMIN));
        String target = user(operator, Role.SALES);
        UserId targetId = logins().userIdOf(target);
        UtcInstant issuedAt = new UtcInstant(clock.instant());
        recoveryCodes.replaceAll(
                targetId,
                IntStream.range(0, RecoveryCode.CODES_PER_USER)
                        .mapToObj(i -> RecoveryCode.issue(
                                targetId, i == 0 ? RECOVERY_CODE : String.format("0000-%04x", i), hasher, issuedAt))
                        .toList());

        postTotpReset(admin, target, enrolledAtOf(target), "IN_PERSON", VERIFIED_AT)
                .andExpect(redirectedUrl(detailUrl(target)));

        MvcResult passwordStage = mvc.perform(post("/login")
                        .param("username", target)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MvcResult recovered = mvc.perform(post(IdentityLogins.TOTP_URL)
                        .cookie(IdentityLogins.sessionOf(passwordStage))
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andReturn();

        assertThat(passwordStage.getResponse().getRedirectedUrl())
                .as("password の段は A-02 へ移る（2 つ目の要素の段）")
                .isEqualTo(IdentityLogins.TOTP_URL);
        assertThat(recovered.getResponse().getRedirectedUrl())
                .as("回復コードでは A-02 を通れず、誤りとして A-01 に戻る")
                .isEqualTo(TOTP_ERROR);
        assertThat(audits(target, "LOGIN_SUCCEEDED")).isZero();
    }

    @Test
    void システム管理者のほかの役割は2つのURLのGETとPOSTで403() throws Exception {
        String target = user(operator, Role.SALES);
        lock(target);
        Cookie support = logins().login(user(operator, Role.CUSTOMER_SUPPORT));

        mvc.perform(get(detailUrl(target) + UNLOCKS).cookie(support)).andExpect(status().isForbidden());
        mvc.perform(get(detailUrl(target) + TOTP_RESETS).cookie(support)).andExpect(status().isForbidden());
        postUnlock(support, target).andExpect(status().isForbidden());
        postTotpReset(support, target, enrolledAtOf(target), "IN_PERSON", VERIFIED_AT)
                .andExpect(status().isForbidden());

        assertThat(lockedUntilOf(target)).isNotNull();
        assertThat(countOf("identity.totp_credential", target)).isOne();
    }

    @Test
    void 確定のPOSTにもCSRFのトークンが要りA03へ移し何も書かない() throws Exception {
        // 行 12（業務の画面の CSRF の拒否は案内へ移し、その session を終える。Bolt 32f。URL ごとにログインし直す）
        String admin = user(operator, Role.SYSTEM_ADMIN);
        String target = user(operator, Role.SALES);
        lock(target);

        for (String url : List.of(UNLOCKS, TOTP_RESETS)) {
            Cookie session = logins().login(admin);
            MvcResult rejected = mvc.perform(post(detailUrl(target) + url)
                            .cookie(session)
                            .param("enrolledAt", enrolledAtOf(target))
                            .param("method", "IN_PERSON")
                            .param("verifiedAt", VERIFIED_AT))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat(rejected.getResponse().getRedirectedUrl()).as(url).isEqualTo(SessionPaths.EXPIRED_URL);
        }
        assertThat(lockedUntilOf(target)).isNotNull();
        assertThat(audits(target, "ACCOUNT_UNLOCKED")).isZero();
        assertThat(countOf("identity.totp_credential", target)).isOne();
        assertThat(audits(target, "TOTP_RESET_BY_ADMIN")).isZero();
    }

    // --- helpers ---

    private ResultActions postUnlock(Cookie session, String target) throws Exception {
        return mvc.perform(post(detailUrl(target) + UNLOCKS).cookie(session).with(csrf()));
    }

    private ResultActions postTotpReset(
            Cookie session, String target, String enrolledAt, String method, String verifiedAt) throws Exception {
        var request = post(detailUrl(target) + TOTP_RESETS)
                .cookie(session)
                .param("enrolledAt", enrolledAt)
                .with(csrf());
        if (method != null) {
            request.param("method", method);
        }
        if (verifiedAt != null) {
            request.param("verifiedAt", verifiedAt);
        }
        return mvc.perform(request);
    }

    /** 今から 10 分のロックを置く（5 回の失敗の後）。 */
    private void lock(String email) {
        UserId userId = logins().userIdOf(email);
        jdbc.update("DELETE FROM identity.login_failure WHERE user_id = ?", userId.value());
        jdbc.update(
                "INSERT INTO identity.login_failure (user_id, consecutive_failures, locked_until, updated_at)"
                        + " VALUES (?, 5, ?, ?)",
                userId.value(),
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(10))),
                Timestamp.from(clock.instant()));
    }

    private void recoveryCode(String email) {
        jdbc.update(
                "INSERT INTO identity.recovery_code (id, user_id, code_hash, used_at, issued_at) VALUES (?, ?, 'h', NULL, ?)",
                UUID.randomUUID(),
                logins().userIdOf(email).value(),
                Timestamp.from(clock.instant()));
    }

    private Object lockedUntilOf(String email) {
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT locked_until FROM identity.login_failure WHERE user_id = ?",
                logins().userIdOf(email).value());
        return rows.isEmpty() ? null : rows.getFirst().get("locked_until");
    }

    /** 確認の画面が hidden で持つ登録の時刻（ISO-8601 の UTC）。 */
    private String enrolledAtOf(String email) {
        return totpCredentials
                .findByUserId(logins().userIdOf(email))
                .orElseThrow()
                .enrolledAt()
                .instant()
                .toString();
    }

    private int countOf(String table, String email) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?",
                Integer.class,
                logins().userIdOf(email).value());
        return count == null ? 0 : count;
    }

    private String detailUrl(String email) {
        return USERS_URL + "/" + logins().userIdOf(email).value();
    }

    private int audits(String email, String action) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = ? AND target_id = ?",
                Integer.class,
                action,
                logins().userIdOf(email).value().toString());
    }

    private String user(Company company, Role role) {
        String email = "credential-test-" + UUID.randomUUID() + "@example.com";
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
        }
        return email;
    }

    private IdentityLogins logins() {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock);
    }
}

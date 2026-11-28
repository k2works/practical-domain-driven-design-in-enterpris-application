package com.example.cargotracker.identity.infrastructure.security;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static com.example.cargotracker.identity.IdentityLogins.SESSION_COOKIE;
import static com.example.cargotracker.identity.IdentityLogins.TOTP_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.LoginFailureRepository;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
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

/**
 * ログインのロックのセキュリティの統合テスト（Bolt 32、US-18 AC3・AC2、IA-INV-03・IA-INV-08）。本物のフィルターの連なりと
 * PostgreSQL の上で、password・TOTP・回復コードの失敗を合わせて 5 回でロックし、15 分の間は正しい認証情報でも誤りと同じ応答で
 * 拒み、ロック中の試行は数えず、2 つの要素がそろったログインで 0 に戻ることを確かめる。スパイクのロックの 5 本（Bolt 31d の
 * 対応表）を受ける。
 */
@IdentityIntegrationTest
class LoginLockSecurityIntegrationTest {

    private static final String WRONG_PASSWORD = "wrong password";
    private static final Instant T0 = Instant.parse("2026-10-13T00:00:00Z");
    private static final String LOGIN_ERROR = "/login?error";
    private static final String TOTP_ERROR = "/login?totp-error";
    private static final String RECOVERY_CODE = "1a2b-3c4d";
    private static final String WARNING = "続けて 5 回誤ると、15 分ログインできなくなります。";
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

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

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    RecoveryCodeHasher hasher;

    @Autowired
    TotpCodeGenerator totpCodes;

    @Autowired
    AesGcmSecretCipher cipher;

    @Autowired
    LoginFailureRepository loginFailures;

    private Company company;

    @BeforeEach
    void 時計を戻し企業を登録する() {
        at(Duration.ZERO);
        company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
    }

    // --- AC3 の境界（スパイクの「失敗が4回ではロックされない」「失敗が5回続くと14分59秒まではロック…」） ---

    @Test
    void 四回の失敗ではロックされず五回目でロックされ正しいpasswordでも誤りと同じ応答になる() throws Exception {
        String email = user();
        failPassword(email, 4);
        assertThat(failures(email)).isEqualTo(4);
        logins().loginWithPassword(email);

        String other = user();
        failPassword(other, 5);

        String lockedPage = responseOf(post("/login")
                .param("username", other)
                .param("password", PASSWORD)
                .with(csrf()));
        assertThat(lockedPage).as("ロック中の正しい password").contains("メールアドレスまたは password が正しくありません。", WARNING);
        assertThat(auditActions(other)).containsOnlyOnce("ACCOUNT_LOCKED");
    }

    @Test
    void ロックは14分59秒まで続き15分で解け解けた後の失敗は1回目から数える() throws Exception {
        String email = user();
        failPassword(email, 5);

        at(Duration.ofMinutes(15).minusSeconds(1));
        postPassword(email, PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));
        at(Duration.ofMinutes(15));
        logins().loginWithPassword(email);
        failPassword(email, 1);

        assertThat(failures(email)).as("解けた後は 1 回目から").isEqualTo(1);
    }

    @Test
    void passwordとTOTPと回復コードの失敗を合わせて数える() throws Exception {
        String email = userWithRecoveryCodes();
        failPassword(email, 2);
        mvc.perform(post(TOTP_URL)
                        .cookie(logins().loginWithPassword(email))
                        .param("code", wrongCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        mvc.perform(post(TOTP_URL)
                        .cookie(logins().loginWithPassword(email))
                        .param("code", wrongCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        mvc.perform(post(TOTP_URL)
                        .cookie(logins().loginWithPassword(email))
                        .param("recoveryCode", "ffff-ffff")
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        assertThat(failures(email)).isEqualTo(5);
        assertThat(failureReasons(email))
                .as("要素ごとの理由で記録する")
                .containsExactlyInAnyOrder(
                        "BAD_CREDENTIALS", "BAD_CREDENTIALS", "INVALID_TOTP", "INVALID_TOTP", "INVALID_RECOVERY_CODE");
        assertThat(auditActions(email)).as("回復コードの段でロックした").containsOnlyOnce("ACCOUNT_LOCKED");
        postPassword(email, PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));
    }

    @Test
    void 利用停止の利用者の正しいpasswordは何度送っても数えずロックしない() throws Exception {
        // 数えない拒否（業務の規則）と、数える誤りを並べる（T-106）
        String email = user();
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", email);
        for (int i = 0; i < 6; i++) {
            postPassword(email, PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));
        }

        assertThat(failures(email)).isZero();
        assertThat(failureReasons(email)).hasSize(6).containsOnly("SUSPENDED");
        failPassword(email, 1);
        assertThat(failures(email)).as("誤った password は数える").isEqualTo(1);
    }

    // --- AC2 ---

    @Test
    void ロック中の応答は存在しないメールアドレスの応答と同じで存在しない利用者の記録は作らない() throws Exception {
        String email = user();
        failPassword(email, 5);
        String unknown = "unknown-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 6; i++) {
            postPassword(unknown, WRONG_PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));
        }

        String locked = responseOf(post("/login")
                .param("username", email)
                .param("password", PASSWORD)
                .with(csrf()));
        String notFound = responseOf(post("/login")
                .param("username", unknown)
                .param("password", PASSWORD)
                .with(csrf()));

        assertThat(locked.replace(email, "EMAIL")).isEqualTo(notFound.replace(unknown, "EMAIL"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.login_failure f JOIN identity.app_user u"
                                + " ON u.id = f.user_id WHERE u.email = ?",
                        Integer.class,
                        unknown))
                .isZero();
    }

    // --- 数えない・延ばさない（T-106: 数えた場面の記録と並べる） ---

    @Test
    void ロック中の試行は数えず期限も延ばさず理由LOCKEDで記録する() throws Exception {
        String email = user();
        failPassword(email, 5);
        var lockedUntil = loginFailures.findByUserId(userIdOf(email)).lockedUntil();

        at(Duration.ofMinutes(10));
        postPassword(email, WRONG_PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));
        postPassword(email, PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));

        assertThat(failures(email)).isEqualTo(5);
        assertThat(loginFailures.findByUserId(userIdOf(email)).lockedUntil()).isEqualTo(lockedUntil);
        assertThat(failureReasons(email))
                .as("数えた 5 回と、数えないロック中の 2 回")
                .containsExactlyInAnyOrder(
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "LOCKED",
                        "LOCKED");
    }

    // --- 0 に戻す（スパイクの「成功すると失敗の回数は数え直される」） ---

    @Test
    void TOTPで終えたログインでも回復コードで終えたログインでも0に戻る() throws Exception {
        String byTotp = user();
        failPassword(byTotp, 4);
        mvc.perform(post(TOTP_URL)
                        .cookie(logins().loginWithPassword(byTotp))
                        .param("code", currentCode(byTotp))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
        assertThat(failures(byTotp)).isZero();

        String byRecovery = userWithRecoveryCodes();
        failPassword(byRecovery, 4);
        mvc.perform(post(TOTP_URL)
                        .cookie(logins().loginWithPassword(byRecovery))
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                // 回復コードで終えたログインは認証アプリの再登録（A-08）へ移る（Bolt 33b）
                .andExpect(redirectedUrl("/account/authenticator"));
        assertThat(failures(byRecovery)).isZero();
    }

    @Test
    void passwordの段の成功では0に戻さずTOTPの誤りを続けると5回目でロックする() throws Exception {
        String email = user();
        for (int i = 0; i < 4; i++) {
            mvc.perform(post(TOTP_URL)
                            .cookie(logins().loginWithPassword(email))
                            .param("code", wrongCode(email))
                            .with(csrf()))
                    .andExpect(redirectedUrl(TOTP_ERROR));
        }
        Cookie passwordStage = logins().loginWithPassword(email);
        assertThat(failures(email)).as("password の段の成功では戻さない").isEqualTo(4);

        mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("code", wrongCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        assertThat(auditActions(email)).as("TOTP の段でロックした").containsOnlyOnce("ACCOUNT_LOCKED");
        postPassword(email, PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));
    }

    // --- password の段の後のロック（スパイクの「passwordを確かめた後にロックされたら正しいTOTPも拒否される」） ---

    @Test
    void passwordの段の後に別のsessionの失敗でロックされたら正しいTOTPも回復コードも拒む() throws Exception {
        String email = userWithRecoveryCodes();
        Cookie first = logins().loginWithPassword(email);
        Cookie second = logins().loginWithPassword(email);
        Cookie third = logins().loginWithPassword(email);
        failPassword(email, 5);

        mvc.perform(post(TOTP_URL)
                        .cookie(first)
                        .param("code", currentCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        mvc.perform(post(TOTP_URL)
                        .cookie(second)
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        mvc.perform(post(TOTP_URL).cookie(third).param("code", wrongCode(email)).with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email)))
                .as("ロック中に回復コードを使わせない")
                .hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(failures(email)).as("ロック中の 3 回は数えない").isEqualTo(5);
        assertThat(failureReasons(email))
                .as("数えた 5 回と、確かめずに拒んだ 3 回（誤った TOTP を含む）")
                .containsExactlyInAnyOrder(
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "BAD_CREDENTIALS",
                        "LOCKED",
                        "LOCKED",
                        "LOCKED");
        mvc.perform(get("/login?totp-error"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).contains(WARNING));
    }

    // --- 利用者の切り替え（T-102） ---

    @Test
    void 別の利用者の失敗は数えない() throws Exception {
        String email = user();
        String other = user();
        failPassword(other, 4);

        assertThat(failures(email)).isZero();
        logins().loginWithPassword(email);
    }

    // --- 補助 ---

    private void at(Duration offset) {
        ((MutableClock) clock).setInstant(T0.plus(offset));
    }

    private String user() {
        String email = "lock-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of(email),
                "荷主 太郎",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(Role.SHIPPER)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return email;
    }

    private String userWithRecoveryCodes() {
        String email = user();
        UserId userId = userIdOf(email);
        UtcInstant now = new UtcInstant(clock.instant());
        recoveryCodes.replaceAll(
                userId,
                IntStream.range(0, RecoveryCode.CODES_PER_USER)
                        .mapToObj(i -> RecoveryCode.issue(
                                userId, i == 0 ? RECOVERY_CODE : String.format("0000-%04x", i), hasher, now))
                        .toList());
        return email;
    }

    private org.springframework.test.web.servlet.ResultActions postPassword(String email, String password)
            throws Exception {
        return mvc.perform(post("/login")
                .param("username", email)
                .param("password", password)
                .with(csrf()));
    }

    private void failPassword(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            postPassword(email, WRONG_PASSWORD).andExpect(redirectedUrl(LOGIN_ERROR));
        }
    }

    /** 送信の後に移った先の画面（A-01 の誤りの文言）を開いた本文。 */
    private String responseOf(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String target = result.getResponse().getRedirectedUrl();
        assertThat(target).isEqualTo(LOGIN_ERROR);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        MvcResult page = mvc.perform(session == null ? get(target) : get(target).cookie(session))
                .andReturn();
        return page.getResponse().getContentAsString().replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "");
    }

    private String currentCode(String email) {
        UserId owner = userIdOf(email);
        TotpCredential credential = totpCredentials.findByUserId(owner).orElseThrow();
        return totpCodes.code(owner, credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private String wrongCode(String email) {
        UserId owner = userIdOf(email);
        TotpCredential credential = totpCredentials.findByUserId(owner).orElseThrow();
        long step = TotpCredential.stepAt(new UtcInstant(clock.instant()));
        List<String> window = List.of(
                totpCodes.code(owner, credential.secret(), step - 1),
                totpCodes.code(owner, credential.secret(), step),
                totpCodes.code(owner, credential.secret(), step + 1));
        return IntStream.range(0, 10)
                .mapToObj(i -> String.format("%06d", i))
                .filter(code -> !window.contains(code))
                .findFirst()
                .orElseThrow();
    }

    private UserId userIdOf(String email) {
        return users.findByEmail(EmailAddress.of(email)).orElseThrow().id();
    }

    private int failures(String email) {
        return loginFailures.findByUserId(userIdOf(email)).consecutiveFailures();
    }

    private List<String> auditActions(String email) {
        return jdbc.queryForList(
                "SELECT action FROM identity.audit_record WHERE actor_user_id = ?",
                String.class,
                userIdOf(email).value());
    }

    private List<String> failureReasons(String email) {
        return jdbc.queryForList(
                "SELECT reason FROM identity.audit_record WHERE actor_user_id = ? AND action = 'LOGIN_FAILED'",
                String.class,
                userIdOf(email).value());
    }

    private IdentityLogins logins() {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock);
    }
}

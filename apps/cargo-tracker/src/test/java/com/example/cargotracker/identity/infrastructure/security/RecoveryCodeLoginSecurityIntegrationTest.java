package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
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
import com.example.cargotracker.identity.infrastructure.crypto.Base32;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
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
 * A-02 での回復コードによるログインのセキュリティの統合テスト（Bolt 31c、US-18 AC9 のログインの分、SEC-09、ADR-011）。本物の
 * フィルターの連なりと Spring Session JDBC（PostgreSQL）の上で、回復コードが TOTP と同じ 2 つ目の要素として通ること、一度だけ
 * 使えること、誤りは A-01 に戻り監査記録に理由だけが残ること、登録の直後の案内を確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecoveryCodeLoginSecurityIntegrationTest.FixedClock.class})
class RecoveryCodeLoginSecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-13T00:00:00Z");
    private static final String TOTP_URL = "/login/totp";
    private static final String ENROLL_URL = "/login/totp/enroll";
    private static final String TOTP_ERROR = "/login?totp-error";
    private static final String BUSINESS_URL = "/customer/transport-requests";
    private static final String RECOVERY_CODE = "1a2b-3c4d";
    private static final String SECOND_RECOVERY_CODE = "5e6f-7a8b";
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern MANUAL_KEY = Pattern.compile("data-manual-key=\"([A-Z2-7 ]+)\"");
    private static final String JUST_ENROLLED = "認証アプリを登録しました。";

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

    private Company company;

    @BeforeEach
    void 時計を戻し企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
    }

    @Test
    void 回復コードでA02を通ると業務の画面を開けsessionIDが変わり監査記録はログインの成功になる() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie passwordStage = loginWithPassword(email);

        MvcResult result = mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("recoveryCode", " 1A2B3C4D ")
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie full = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(full.getValue()).as("権限が上がる段で session ID を変える（T-40）").isNotEqualTo(passwordStage.getValue());
        mvc.perform(get(BUSINESS_URL).cookie(full)).andExpect(status().isOk());
        assertThat(sessionAttributeNames(full)).as("8 時間の起点").contains(SessionLifetime.AUTHENTICATED_AT);
        assertThat(auditActions(email))
                .as("時計を止めているので順は問わない")
                .containsExactlyInAnyOrder("PASSWORD_VERIFIED", "RECOVERY_CODE_USED", "LOGIN_SUCCEEDED");
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(RecoveryCode.CODES_PER_USER - 1);
    }

    @Test
    void 使った回復コードはもう一度使えずA01に戻り理由だけが監査記録に残り平文はどこにも残らない() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie full = mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
        Cookie again = loginWithPassword(email);

        mvc.perform(post(TOTP_URL)
                        .cookie(again)
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        assertThat(failureReasons(email)).containsExactly("INVALID_RECOVERY_CODE");
        // 「ないこと」と並べて「あるべきもの」を確かめる（T-106）
        assertThat(usedAtCount(email)).as("使った回復コードの時刻は残る").isEqualTo(1);
        assertThat(auditText(email)).doesNotContain(RECOVERY_CODE).doesNotContain("1a2b3c4d");
        assertThat(sessionAttributeText(full))
                .as("ログインを終えた session には主体があり、回復コードの平文はない")
                .contains(email)
                .doesNotContain(RECOVERY_CODE);
    }

    @Test
    void 保存したハッシュの形が違う利用者は500にせずA01に戻しログに平文を出さない() throws Exception {
        // 読めない認証の要素（Bolt 31d、確認ポイント 5）。ログの平文の見張り: エラーの行があること（T-107）と、回復コード・password がないこと
        String email = enrolledUserWithRecoveryCodes();
        jdbc.update(
                "UPDATE identity.recovery_code SET code_hash = 'no-prefix-hash' WHERE user_id = ?",
                userIdOf(email).value());

        Cookie session = loginWithPassword(email);
        String logs;
        try (LogCapture capture = LogCapture.start()) {
            mvc.perform(post(TOTP_URL)
                            .cookie(session)
                            .param("recoveryCode", RECOVERY_CODE)
                            .with(csrf()))
                    .andExpect(redirectedUrl(TOTP_ERROR));
            logs = capture.text();
        }

        assertThat(failureReasons(email)).containsExactly("INVALID_RECOVERY_CODE");
        assertThat(logs)
                .contains("回復コードを照合できず", userIdOf(email).value().toString())
                .doesNotContain(RECOVERY_CODE)
                .doesNotContain("1a2b3c4d")
                .doesNotContain(PASSWORD);
    }

    @Test
    void 誤った回復コードと形の違う回復コードはA01に戻り何も使わない() throws Exception {
        String email = enrolledUserWithRecoveryCodes();

        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("recoveryCode", "ffff-ffff")
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("recoveryCode", "not-a-code")
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        assertThat(failureReasons(email)).containsExactly("INVALID_RECOVERY_CODE", "INVALID_RECOVERY_CODE");
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(RecoveryCode.CODES_PER_USER);
    }

    @Test
    void 回復コードと認証コードの両方を送ると照合せずに拒み監査記録に残す() throws Exception {
        String email = enrolledUserWithRecoveryCodes();

        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("code", currentCode(email))
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        assertThat(failureReasons(email)).containsExactly("INVALID_RECOVERY_CODE");
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(RecoveryCode.CODES_PER_USER);
    }

    @Test
    void 回復コードの欄を開いたsessionで別の利用者のpasswordを送ると前の利用者の回復コードでは通らない() throws Exception {
        // T-102: 回復コードは主体の利用者 ID で探すので、利用者を切り替えた session では前の利用者のコードは使えない
        String first = enrolledUserWithRecoveryCodes();
        String second = enrolledUserWithRecoveryCodes(SECOND_RECOVERY_CODE);
        Cookie session = loginWithPassword(first);
        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(status().isOk());
        Cookie switched = mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", second)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);

        mvc.perform(post(TOTP_URL)
                        .cookie(switched)
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(first))).hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(failureReasons(second)).as("拒否は切り替えた後の利用者に記録する").containsExactly("INVALID_RECOVERY_CODE");
        // 主体が本当に 2 人目に切り替わっていること（あるべきもの。T-106）
        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(second))
                        .param("recoveryCode", SECOND_RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void どちらの欄もない送信と空白だけの回復コードは認証コードの誤りと同じに扱う() throws Exception {
        String email = enrolledUserWithRecoveryCodes();

        mvc.perform(post(TOTP_URL).cookie(loginWithPassword(email)).with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("recoveryCode", "   ")
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));

        assertThat(failureReasons(email)).containsExactly("INVALID_TOTP", "INVALID_TOTP");
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(RecoveryCode.CODES_PER_USER);
    }

    @Test
    void 回復コードの送信にもCSRFのトークンが要りpasswordの段を経ない送信は誤りの文言を出さない() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie session = loginWithPassword(email);
        // A-02 の画面を開くと session に CSRF のトークンができる（開く前の送信は既存の扱いどおり A-03 へ送る。Bolt 31）
        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(status().isOk());

        mvc.perform(post(TOTP_URL).cookie(session).param("recoveryCode", RECOVERY_CODE))
                .andExpect(status().isForbidden());
        mvc.perform(post(TOTP_URL).param("recoveryCode", RECOVERY_CODE).with(csrf()))
                .andExpect(redirectedUrl("/login"));
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(RecoveryCode.CODES_PER_USER);
    }

    @Test
    void A02は回復コードの欄を出す() throws Exception {
        String email = enrolledUserWithRecoveryCodes();

        mvc.perform(get(TOTP_URL).cookie(loginWithPassword(email)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<details")))
                .andExpect(content().string(Matchers.containsString("name=\"recoveryCode\"")))
                .andExpect(content().string(Matchers.containsString("回復コードもない場合は")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(JUST_ENROLLED))));
    }

    @Test
    void 登録の直後のA02には案内を出しログインを終えた後に開き直すと出さない() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);
        String key = manualKey(mvc.perform(get(ENROLL_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String code = codeFromKey(email, key);
        mvc.perform(post(ENROLL_URL).cookie(session).param("code", code).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get(TOTP_URL).cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(JUST_ENROLLED)));
        Cookie full = mvc.perform(
                        post(TOTP_URL).cookie(session).param("code", code).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
        mvc.perform(get(TOTP_URL).cookie(full))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString(JUST_ENROLLED))));
        assertThat(sessionAttributeNames(full)).doesNotContain(SessionLifetime.ENROLLMENT_STARTED_AT);
    }

    // --- 補助 ---

    private String enrolledUser() {
        String email = unenrolledUser();
        UserId userId = userIdOf(email);
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return email;
    }

    private String enrolledUserWithRecoveryCodes() {
        return enrolledUserWithRecoveryCodes(RECOVERY_CODE);
    }

    private String enrolledUserWithRecoveryCodes(String plain) {
        String email = enrolledUser();
        UserId userId = userIdOf(email);
        UtcInstant now = new UtcInstant(clock.instant());
        recoveryCodes.replaceAll(
                userId,
                IntStream.range(0, RecoveryCode.CODES_PER_USER)
                        .mapToObj(i ->
                                RecoveryCode.issue(userId, i == 0 ? plain : String.format("0000-%04x", i), hasher, now))
                        .toList());
        return email;
    }

    private String unenrolledUser() {
        String email = "recovery-" + UUID.randomUUID() + "@example.com";
        users.add(User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of(email),
                "荷主 太郎",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(Role.SHIPPER)));
        return email;
    }

    private Cookie loginWithPassword(String email) throws Exception {
        return mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
    }

    private String currentCode(String email) {
        UserId owner = userIdOf(email);
        TotpCredential credential = totpCredentials.findByUserId(owner).orElseThrow();
        return totpCodes.code(owner, credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private static String manualKey(String html) {
        Matcher matcher = MANUAL_KEY.matcher(html);
        assertThat(matcher.find()).as("手入力のキー").isTrue();
        return matcher.group(1).replace(" ", "");
    }

    private String codeFromKey(String email, String key) {
        UserId owner = userIdOf(email);
        return totpCodes.code(
                owner,
                cipher.encrypt(Base32.decode(key), owner),
                TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private UserId userIdOf(String email) {
        return users.findByEmail(EmailAddress.of(email)).orElseThrow().id();
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

    private String auditText(String email) {
        return String.join(
                "|",
                jdbc.queryForList(
                        "SELECT concat_ws(',', action, result, reason) FROM identity.audit_record WHERE actor_user_id = ?",
                        String.class,
                        userIdOf(email).value()));
    }

    private Integer usedAtCount(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.recovery_code WHERE user_id = ? AND used_at IS NOT NULL",
                Integer.class,
                userIdOf(email).value());
    }

    private String sessionAttributeText(Cookie session) {
        String sessionId = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.US_ASCII);
        return String.join(
                "|",
                jdbc.query(
                        "SELECT a.attribute_bytes FROM platform.spring_session_attributes a"
                                + " JOIN platform.spring_session s ON s.primary_id = a.session_primary_id"
                                + " WHERE s.session_id = ?",
                        (rs, n) -> new String(rs.getBytes(1), StandardCharsets.ISO_8859_1),
                        sessionId));
    }

    private List<String> sessionAttributeNames(Cookie session) {
        String sessionId = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.US_ASCII);
        return jdbc.queryForList(
                "SELECT a.attribute_name FROM platform.spring_session_attributes a"
                        + " JOIN platform.spring_session s ON s.primary_id = a.session_primary_id WHERE s.session_id = ?",
                String.class,
                sessionId);
    }
}

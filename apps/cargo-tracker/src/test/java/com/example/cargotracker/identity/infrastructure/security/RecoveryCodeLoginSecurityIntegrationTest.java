package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AuditRecordRows;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.SpringSessionRows;
import com.example.cargotracker.identity.application.internal.session.SessionKeys;
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
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A-02 での回復コードによるログインのセキュリティの統合テスト（Bolt 31c、US-18 AC9 のログインの分、SEC-09、ADR-011）。本物の
 * フィルターの連なりと Spring Session JDBC（PostgreSQL）の上で、回復コードが TOTP と同じ 2 つ目の要素として通ること、一度だけ
 * 使えること、誤りは A-01 に戻り監査記録に理由だけが残ること、登録の直後の案内を確かめる。
 */
@IdentityIntegrationTest
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
    private static final String RE_ENROLL_URL = "/account/authenticator";
    private static final String RE_ENROLLMENT_STARTED_AT = "cargotracker.session.reEnrollmentStartedAt";
    private static final String NOTICE = "回復コードでログインしました。認証アプリを登録し直してください。";
    private static final String EXPIRED_NOTICE = "回復コードでログインしてから 15 分を過ぎたため、この画面からは認証アプリを登録し直せません。";
    private static final Pattern NEW_RECOVERY_CODE = Pattern.compile("\\b([0-9a-f]{4}-[0-9a-f]{4})\\b");

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
        Instant recoveredAt = LOGIN_AT.plus(Duration.ofMinutes(2));
        ((MutableClock) clock).setInstant(recoveredAt);

        MvcResult result = mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("recoveryCode", " 1A2B3C4D ")
                        .with(csrf()))
                .andExpect(redirectedUrl(RE_ENROLL_URL))
                .andReturn();

        Cookie full = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(full.getValue()).as("権限が上がる段で session ID を変える（T-40）").isNotEqualTo(passwordStage.getValue());
        mvc.perform(get(BUSINESS_URL).cookie(full)).andExpect(status().isOk());
        assertThat(sessions().attribute(full, SessionKeys.AUTHENTICATED_AT))
                .as("8 時間の起点は回復コードが通った時刻（password の段の時刻でない。Bolt 31c）")
                .isEqualTo(recoveredAt);
        assertThat(new AuditRecordRows(jdbc).actionsAt(userIdOf(email), LOGIN_AT))
                .as("password の段が先")
                .containsExactly("PASSWORD_VERIFIED");
        assertThat(new AuditRecordRows(jdbc).actionsAt(userIdOf(email), recoveredAt))
                .as("回復コードの使用とログインの成功は同じ時刻に書く（順は問わない）")
                .containsExactlyInAnyOrder("RECOVERY_CODE_USED", "LOGIN_SUCCEEDED");
        assertThat(auditActions(email)).hasSize(3);
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(RecoveryCode.CODES_PER_USER - 1);
    }

    @Test
    void 使った回復コードはもう一度使えずA01に戻り理由だけが監査記録に残り平文はどこにも残らない() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie full = mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl(RE_ENROLL_URL))
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
                .andExpect(redirectedUrl(RE_ENROLL_URL));
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
        assertThat(sessions().attributeNames(full)).doesNotContain(SessionKeys.ENROLLMENT_STARTED_AT);
    }

    // --- 認証アプリの再登録（A-08。US-18 AC9、SEC-10。Bolt 33b） ---

    private Cookie loginWithRecoveryCode(String email, String code) throws Exception {
        return mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("recoveryCode", code)
                        .with(csrf()))
                .andExpect(redirectedUrl(RE_ENROLL_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
    }

    private Cookie loginWithTotp(String email) throws Exception {
        return mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("code", currentCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
    }

    /** 時計を次の刻みに進める（同じ刻みのコードを二度使わない）。 */
    private void nextStep() {
        ((MutableClock) clock).setInstant(clock.instant().plus(TotpCredential.STEP_DURATION));
    }

    @Test
    void 回復コードでログインするとA08に移り認証コードでログインした利用者はA08を開けない() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie recovered = loginWithRecoveryCode(email, RECOVERY_CODE);

        String html = mvc.perform(get(RE_ENROLL_URL).cookie(recovered))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        // 本文は知らせと同じ文を言うので、知らせの部品がないことを見る
        assertThat(html)
                .contains("<title>認証アプリの再登録 - cargo-tracker</title>", "後で登録する")
                // 欄の桁数は TotpCredential.DIGITS から描く。表示は Bolt 33d の前と同じ（見出しの文言は残す）
                .contains("pattern=\"[0-9]{6}\"", "maxlength=\"6\"", "2. 表示された 6 桁のコードを入力する")
                .as("A-08 には印の知らせを出さない")
                .doesNotContain("app-recovery-notice");

        nextStep();
        Cookie byTotp = loginWithTotp(email);
        mvc.perform(get(RE_ENROLL_URL).cookie(byTotp)).andExpect(redirectedUrl("/"));
        mvc.perform(get(BUSINESS_URL).cookie(byTotp))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(NOTICE))));
    }

    @Test
    void A08で登録し直すと新しい回復コードを示し古いコードと古い回復コードは通らずほかの端末はA03で今のsessionは残る() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie otherDevice = loginWithTotp(email);
        mvc.perform(get(BUSINESS_URL).cookie(otherDevice)).andExpect(status().isOk());
        nextStep();
        Cookie recovered = loginWithRecoveryCode(email, RECOVERY_CODE);
        String form = mvc.perform(get(RE_ENROLL_URL).cookie(recovered))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String newKey = manualKey(form);

        String done = mvc.perform(post(RE_ENROLL_URL)
                        .cookie(recovered)
                        .param("code", codeFromKey(email, newKey))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(done).contains("認証アプリを登録し直しました。前の回復コードは使えなくなりました。");
        Matcher codes = NEW_RECOVERY_CODE.matcher(done);
        List<String> shown = new java.util.ArrayList<>();
        while (codes.find()) {
            shown.add(codes.group(1));
        }
        assertThat(shown).hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(auditActions(email)).contains("TOTP_RE_ENROLLED");
        mvc.perform(get(BUSINESS_URL).cookie(otherDevice)).andExpect(redirectedUrl("/session-expired"));
        mvc.perform(get(BUSINESS_URL).cookie(recovered))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(NOTICE))));
        mvc.perform(get(RE_ENROLL_URL).cookie(recovered)).andExpect(redirectedUrl("/"));
        // 完了の後の再送は置き換えずにホームへ
        mvc.perform(post(RE_ENROLL_URL)
                        .cookie(recovered)
                        .param("code", codeFromKey(email, newKey))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
        assertThat(auditActions(email)).filteredOn("TOTP_RE_ENROLLED"::equals).hasSize(1);

        nextStep();
        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("recoveryCode", "0000-0001")
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("code", oldSecretCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_ERROR));
        mvc.perform(post(TOTP_URL)
                        .cookie(loginWithPassword(email))
                        .param("code", codeFromKey(email, newKey))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
        nextStep();
        loginWithRecoveryCode(email, shown.getFirst());
    }

    @Test
    void 後で登録すると業務の画面に印の知らせが出てリンクからA08を開ける() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie recovered = loginWithRecoveryCode(email, RECOVERY_CODE);

        mvc.perform(post(RE_ENROLL_URL + "/later").cookie(recovered).with(csrf()))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get(BUSINESS_URL).cookie(recovered))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(NOTICE)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("認証アプリを登録し直す")));
        mvc.perform(get(RE_ENROLL_URL).cookie(recovered)).andExpect(status().isOk());
        assertThat(auditActions(email)).doesNotContain("TOTP_RE_ENROLLED");
    }

    @Test
    void 回復コードでログインしたsessionで別の利用者に切り替えると印は消える() throws Exception {
        String first = enrolledUserWithRecoveryCodes();
        String second = enrolledUserWithRecoveryCodes(SECOND_RECOVERY_CODE);
        Cookie recovered = loginWithRecoveryCode(first, RECOVERY_CODE);
        mvc.perform(get(RE_ENROLL_URL).cookie(recovered)).andExpect(status().isOk());

        Cookie switched = mvc.perform(post("/login")
                        .cookie(recovered)
                        .param("username", second)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
        assertThat(sessions().attribute(switched, SessionKeys.RECOVERY_LOGIN_USER))
                .as("password の段の成功で前の利用者の印を消す")
                .isNull();
        Cookie secondFull = mvc.perform(post(TOTP_URL)
                        .cookie(switched)
                        .param("code", currentCode(second))
                        .with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);

        mvc.perform(get(RE_ENROLL_URL).cookie(secondFull)).andExpect(redirectedUrl("/"));
        mvc.perform(get(BUSINESS_URL).cookie(secondFull))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(NOTICE))));
    }

    @Test
    void A08の登録と後で登録するはCSRFのトークンがなければ処理しない() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie recovered = loginWithRecoveryCode(email, RECOVERY_CODE);

        mvc.perform(post(RE_ENROLL_URL)
                        .cookie(recovered)
                        .param("code", "000000")
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post(RE_ENROLL_URL + "/later").cookie(recovered).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(auditActions(email)).doesNotContain("TOTP_RE_ENROLLED");
    }

    @Test
    void A08で誤ったコードを送ると置き換えず同じキーで示し直す() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie recovered = loginWithRecoveryCode(email, RECOVERY_CODE);
        String key = manualKey(mvc.perform(get(RE_ENROLL_URL).cookie(recovered))
                .andReturn()
                .getResponse()
                .getContentAsString());

        String again = mvc.perform(post(RE_ENROLL_URL)
                        .cookie(recovered)
                        .param("code", "000000")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(again).contains("認証コードが正しくありません。認証アプリに表示されている最新のコードを入力してください。");
        assertThat(manualKey(again)).isEqualTo(key);
        assertThat(auditActions(email)).doesNotContain("TOTP_RE_ENROLLED");
    }

    @Test
    void A08を開いてから15分ちょうどで途中の秘密を使わず新しいキーで始め直す() throws Exception {
        // 印（ログインから 15 分）は残したまま、A-08 を開いた時刻だけを前にずらす（画面の期限の分岐。Bolt 33e の決定 5）
        String email = enrolledUserWithRecoveryCodes();
        Cookie recovered = loginWithRecoveryCode(email, RECOVERY_CODE);
        String key = manualKey(mvc.perform(get(RE_ENROLL_URL).cookie(recovered))
                .andReturn()
                .getResponse()
                .getContentAsString());

        sessions()
                .putAttribute(
                        recovered.getValue(),
                        RE_ENROLLMENT_STARTED_AT,
                        clock.instant().minus(Duration.ofMinutes(15)).plusNanos(1));
        String before = manualKey(mvc.perform(get(RE_ENROLL_URL).cookie(recovered))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(before).as("15 分の 1 ナノ秒前は同じキー").isEqualTo(key);

        sessions()
                .putAttribute(
                        recovered.getValue(),
                        RE_ENROLLMENT_STARTED_AT,
                        clock.instant().minus(Duration.ofMinutes(15)));
        String expired = manualKey(mvc.perform(get(RE_ENROLL_URL).cookie(recovered))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(expired).as("15 分ちょうどは期限切れで新しいキー").isNotEqualTo(key);
    }

    @Test
    void 回復コードでログインしてから15分を過ぎるとA08を開けず知らせはログインし直す案内になる() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        Cookie recovered = loginWithRecoveryCode(email, RECOVERY_CODE);
        Instant loggedIn = clock.instant();
        ((MutableClock) clock).setInstant(loggedIn.plus(Duration.ofMinutes(15)).minusSeconds(1));
        mvc.perform(get(RE_ENROLL_URL).cookie(recovered)).andExpect(status().isOk());

        ((MutableClock) clock).setInstant(loggedIn.plus(Duration.ofMinutes(15)));

        mvc.perform(get(RE_ENROLL_URL).cookie(recovered)).andExpect(redirectedUrl("/"));
        // 完了の後の再送は置き換えずにホームへ
        mvc.perform(post(RE_ENROLL_URL)
                        .cookie(recovered)
                        .param("code", "000000")
                        .with(csrf()))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get(BUSINESS_URL).cookie(recovered))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(EXPIRED_NOTICE)))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("href=\"/account/authenticator\""))));
        assertThat(auditActions(email)).doesNotContain("TOTP_RE_ENROLLED");
    }

    @Test
    void ログインの前に開いたqueryの付いた画面に後で登録するでも続けるでも戻る() throws Exception {
        String email = enrolledUserWithRecoveryCodes();
        String target = BUSINESS_URL + "?copyFrom=TR-1";

        Cookie later = recoveredAfterOpening(email, target, RECOVERY_CODE);
        String laterTo = mvc.perform(
                        post(RE_ENROLL_URL + "/later").cookie(later).with(csrf()))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        assertThat(laterTo)
                .as("覚えた要求の query を保つ（Spring Security が continue の引数を足す）")
                .contains(target);

        Cookie recovered = recoveredAfterOpening(email, target, "0000-0001");
        String key = manualKey(mvc.perform(get(RE_ENROLL_URL).cookie(recovered))
                .andReturn()
                .getResponse()
                .getContentAsString());
        mvc.perform(post(RE_ENROLL_URL)
                        .cookie(recovered)
                        .param("code", codeFromKey(email, key))
                        .with(csrf()))
                .andExpect(status().isOk());
        String continued = mvc.perform(
                        post(RE_ENROLL_URL + "/continue").cookie(recovered).with(csrf()))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        assertThat(continued).contains(target);
    }

    /** ログインしないで業務の画面を開き（覚えた要求）、password と回復コードでログインした session。 */
    private Cookie recoveredAfterOpening(String email, String url, String recoveryCode) throws Exception {
        Cookie anonymous = mvc.perform(get(url))
                .andExpect(redirectedUrl("/login"))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
        Cookie passwordStage = mvc.perform(post("/login")
                        .cookie(anonymous)
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
        nextStep();
        return mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("recoveryCode", recoveryCode)
                        .with(csrf()))
                .andExpect(redirectedUrl(RE_ENROLL_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
    }

    /** 古い秘密（テストの固定の秘密）の今の刻みのコード。 */
    private String oldSecretCode(String email) {
        UserId owner = userIdOf(email);
        return totpCodes.code(
                owner,
                cipher.encrypt(TOTP_SECRET.clone(), owner),
                TotpCredential.stepAt(new UtcInstant(clock.instant())));
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
        return String.join(
                "|",
                sessions().attributeBytes(session).stream()
                        .map(bytes -> new String(bytes, StandardCharsets.ISO_8859_1))
                        .toList());
    }

    private SpringSessionRows sessions() {
        return new SpringSessionRows(jdbc);
    }
}

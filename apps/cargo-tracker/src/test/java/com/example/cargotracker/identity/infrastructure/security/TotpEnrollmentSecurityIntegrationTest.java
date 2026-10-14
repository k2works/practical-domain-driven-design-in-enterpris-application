package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.IdentityTestClock;
import com.example.cargotracker.identity.SpringSessionRows;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
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
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 初回の TOTP の登録（A-07）のセキュリティの統合テスト（Bolt 31b、US-18 AC7、SEC-09・SEC-16）。本物のフィルターの連なりと
 * Spring Session JDBC（PostgreSQL）の上で、登録の流れ、session に平文を置かないこと、登録の途中の上限（15 分）、CSRF を確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IdentityTestClock.class})
class TotpEnrollmentSecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-13T00:00:00Z");
    private static final String TOTP_URL = "/login/totp";
    private static final String ENROLL_URL = "/login/totp/enroll";
    private static final Pattern MANUAL_KEY = Pattern.compile("data-manual-key=\"([A-Z2-7 ]+)\"");
    private static final Pattern RECOVERY_CODE = Pattern.compile("[0-9a-f]{4}-[0-9a-f]{4}");
    /** LOGIN_AT（日本時間 9:00）に登録を始めたときの期限の表示。 */
    private static final String DEADLINE_AT_LOGIN = "2026-10-13 09:15 Asia/Tokyo（UTC+09:00）";

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
    void 未登録の利用者はpasswordの後にA02を経てA07に来てQRとキーが出て応答は保存させない() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);

        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(redirectedUrl(ENROLL_URL));
        MvcResult page = mvc.perform(get(ENROLL_URL).cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        String html = page.getResponse().getContentAsString();
        assertThat(html)
                .contains("<svg")
                .contains("認証アプリで読み取る QR コード")
                .contains(DEADLINE_AT_LOGIN + "</span> までに、登録とログインを終えてください")
                .doesNotContain("TOTP")
                .contains("autocomplete=\"one-time-code\"");
        assertThat(manualKey(html)).hasSize(32);
    }

    @Test
    void 再読み込みしても秘密は変わらずsessionには平文の秘密も表示の値も置かない() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);

        String first = manualKey(enrollPage(session));
        String second = manualKey(enrollPage(session));

        assertThat(second).isEqualTo(first);
        assertNoPlaintextInSession(session, first);
        mvc.perform(post(ENROLL_URL)
                        .cookie(session)
                        .param("code", wrongCode(email, first))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertNoPlaintextInSession(session, first);
    }

    @Test
    void 再読み込みしても登録を始めた時刻は変わらず15分の上限を延ばさない() throws Exception {
        Cookie session = loginWithPassword(unenrolledUser());
        enrollPage(session);

        advance(Duration.ofMinutes(10));
        assertThat(enrollPage(session)).as("期限の表示も最初の時刻のまま").contains(DEADLINE_AT_LOGIN);

        advance(Duration.ofMinutes(15));
        mvc.perform(get(ENROLL_URL).cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 認証していない利用者はA07を開けず送信もできずログインの画面へ送られる() throws Exception {
        mvc.perform(get(ENROLL_URL)).andExpect(redirectedUrl("/login"));
        mvc.perform(post(ENROLL_URL).param("code", "123456").with(csrf())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 誤ったコードでは保存せず同じ秘密で示し直す() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);
        String key = manualKey(enrollPage(session));

        MvcResult result = mvc.perform(post(ENROLL_URL)
                        .cookie(session)
                        .param("code", wrongCode(email, key))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("認証コードが正しくありません");
        assertThat(manualKey(result.getResponse().getContentAsString())).isEqualTo(key);
        assertThat(totpCredentials.findByUserId(userIdOf(email))).isEmpty();
    }

    @Test
    void 正しいコードで回復コード10個を一度だけ示しA02で登録のコードを使ってログインを終えられる() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);
        String key = manualKey(enrollPage(session));
        String code = codeFromKey(email, key);

        MvcResult enrolled = mvc.perform(
                        post(ENROLL_URL).cookie(session).param("code", code).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        String html = enrolled.getResponse().getContentAsString();
        assertThat(RECOVERY_CODE.matcher(html).results().map(MatchResult::group).distinct())
                .hasSize(10);
        assertThat(html)
                .contains("回復コードを安全な場所に保存した")
                .contains("required")
                .as("登録を始めた時刻からの期限を続けて示す")
                .contains(DEADLINE_AT_LOGIN + "</span> までに、続けてログインを終えてください");
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(10);
        assertThat(auditActions(email)).contains("TOTP_ENROLLED");

        Cookie afterEnroll = sessionOrSame(enrolled, session);
        mvc.perform(get(TOTP_URL).cookie(afterEnroll)).andExpect(status().isOk());
        mvc.perform(post(TOTP_URL).cookie(afterEnroll).param("code", code).with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 登録の送信を再送しても二度登録せずA02へ送る() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);
        String code = codeFromKey(email, manualKey(enrollPage(session)));
        mvc.perform(post(ENROLL_URL).cookie(session).param("code", code).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post(ENROLL_URL).cookie(session).param("code", code).with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL));
        assertThat(recoveryCodes.findUnusedByUserId(userIdOf(email))).hasSize(10);
    }

    @Test
    void 登録済みの利用者と2つの要素がそろった主体はA07を開けずA02へ送られる() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);
        String code = codeFromKey(email, manualKey(enrollPage(session)));
        mvc.perform(post(ENROLL_URL).cookie(session).param("code", code).with(csrf()));

        mvc.perform(get(ENROLL_URL).cookie(session)).andExpect(redirectedUrl(TOTP_URL));
        MvcResult full = mvc.perform(
                        post(TOTP_URL).cookie(session).param("code", code).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        mvc.perform(get(ENROLL_URL).cookie(full.getResponse().getCookie(SESSION_COOKIE)))
                .andExpect(redirectedUrl(TOTP_URL));
    }

    @Test
    void A07のPOSTにはCSRFのトークンが要る() throws Exception {
        String email = unenrolledUser();
        Cookie session = loginWithPassword(email);
        String code = codeFromKey(email, manualKey(enrollPage(session)));

        mvc.perform(post(ENROLL_URL).cookie(session).param("code", code)).andExpect(status().isForbidden());
        assertThat(totpCredentials.findByUserId(userIdOf(email))).isEmpty();
    }

    @Test
    void 登録の途中の暗号文がないPOSTはA07を開き直す() throws Exception {
        Cookie session = loginWithPassword(unenrolledUser());
        mvc.perform(get(TOTP_URL).cookie(session));

        mvc.perform(post(ENROLL_URL).cookie(session).param("code", "123456").with(csrf()))
                .andExpect(redirectedUrl(ENROLL_URL));
    }

    @Test
    void 登録の途中はA07を開いてから15分の1秒前まで登録でき15分で再認証の案内へ移る() throws Exception {
        String email = unenrolledUser();
        Cookie early = loginWithPassword(email);
        advance(Duration.ofMinutes(1));
        String key = manualKey(enrollPage(early));
        Cookie late = loginWithPassword(otherUnenrolledUser());
        enrollPage(late);

        advance(Duration.ofMinutes(1).plus(Duration.ofMinutes(15)).minusSeconds(1));
        mvc.perform(post(ENROLL_URL)
                        .cookie(early)
                        .param("code", codeFromKey(email, key))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(totpCredentials.findByUserId(userIdOf(email)))
                .as("上限の 1 秒前は登録できる")
                .isPresent();

        advance(Duration.ofMinutes(1).plus(Duration.ofMinutes(15)));
        mvc.perform(get(ENROLL_URL).cookie(late)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void A07を開いていないpasswordの段は5分のまま() throws Exception {
        Cookie session = loginWithPassword(unenrolledUser());

        advance(Duration.ofMinutes(5));
        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 登録の途中のsessionで別の利用者がpasswordを送ると前の利用者の秘密は使えず新しい秘密になる() throws Exception {
        // T-102: 同じ session での利用者の切り替え。前の利用者の登録の途中の暗号文と時刻を残さない。見張り（T-88）: password の段で
        // 値を消すことと、暗号文の AAD（前の利用者の暗号文は後の利用者として使えない）の 2 つで守るので、片方を外しても通る
        String first = unenrolledUser();
        String second = unenrolledUser();
        Cookie session = loginWithPassword(first);
        String firstKey = manualKey(enrollPage(session));

        Cookie switched = mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", second)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);
        String secondKey = manualKey(enrollPage(switched));

        assertThat(secondKey).isNotEqualTo(firstKey);
        mvc.perform(post(ENROLL_URL)
                        .cookie(switched)
                        .param("code", codeFromKey(first, firstKey))
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(totpCredentials.findByUserId(userIdOf(second)))
                .as("前の利用者の秘密のコードでは登録しない")
                .isEmpty();
    }

    @Test
    void 登録の途中のsessionで別の利用者がpasswordを送ると登録を始めた時刻を持ち越さずpasswordの段の5分になる() throws Exception {
        // T-102: 前の利用者の登録を始めた時刻が残ると、次の利用者の password の段が 15 分に延びる
        Cookie session = loginWithPassword(unenrolledUser());
        enrollPage(session);

        advance(Duration.ofMinutes(1));
        Cookie switched = mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", unenrolledUser())
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn()
                .getResponse()
                .getCookie(SESSION_COOKIE);

        advance(Duration.ofMinutes(6));
        mvc.perform(get(TOTP_URL).cookie(switched)).andExpect(redirectedUrl("/session-expired"));
    }

    // --- 補助 ---

    /**
     * session の属性に、登録の途中の暗号文（{@code v1:}）はあり、平文の秘密（手入力のキー、4 文字ずつの区切り、復号した値）も
     * 表示の値（otpauth の URI、QR の SVG）もないこと。
     */
    private void assertNoPlaintextInSession(Cookie session, String key) {
        List<String> attributes = sessions().attributeBytes(session).stream()
                .map(bytes -> new String(bytes, StandardCharsets.ISO_8859_1))
                .toList();
        String grouped = String.join(" ", key.split("(?<=\\G.{4})"));
        String raw = new String(Base32.decode(key), StandardCharsets.ISO_8859_1);
        assertThat(attributes).as("登録の途中の暗号文").anyMatch(value -> value.contains("v1:"));
        assertThat(attributes)
                .noneMatch(value -> value.contains(key))
                .noneMatch(value -> value.contains(grouped))
                .noneMatch(value -> value.contains(raw))
                .noneMatch(value -> value.contains("otpauth://"))
                .noneMatch(value -> value.contains("<svg"));
    }

    private String unenrolledUser() {
        return user("enroll-" + UUID.randomUUID() + "@example.com");
    }

    private String otherUnenrolledUser() {
        return unenrolledUser();
    }

    private String user(String email) {
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
        MvcResult result = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        return result.getResponse().getCookie(SESSION_COOKIE);
    }

    private String enrollPage(Cookie session) throws Exception {
        return mvc.perform(get(ENROLL_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static Cookie sessionOrSame(MvcResult result, Cookie session) {
        Cookie renewed = result.getResponse().getCookie(SESSION_COOKIE);
        return renewed == null ? session : renewed;
    }

    private static String manualKey(String html) {
        Matcher matcher = MANUAL_KEY.matcher(html);
        assertThat(matcher.find()).as("手入力のキー").isTrue();
        return matcher.group(1).replace(" ", "");
    }

    /** 画面の手入力のキーから、利用者の認証アプリと同じようにその時点のコードを作る。 */
    private String codeFromKey(String email, String key) {
        UserId owner = userIdOf(email);
        return totpCodes.code(
                owner,
                cipher.encrypt(Base32.decode(key), owner),
                TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private String wrongCode(String email, String key) {
        UserId owner = userIdOf(email);
        long step = TotpCredential.stepAt(new UtcInstant(clock.instant()));
        List<String> window = List.of(
                totpCodes.code(owner, cipher.encrypt(Base32.decode(key), owner), step - 1),
                totpCodes.code(owner, cipher.encrypt(Base32.decode(key), owner), step),
                totpCodes.code(owner, cipher.encrypt(Base32.decode(key), owner), step + 1));
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> String.format("%06d", i))
                .filter(code -> !window.contains(code))
                .findFirst()
                .orElseThrow();
    }

    private void advance(Duration duration) {
        ((MutableClock) clock).setInstant(LOGIN_AT.plus(duration));
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

    private SpringSessionRows sessions() {
        return new SpringSessionRows(jdbc);
    }
}

package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.TestcontainersConfiguration;
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
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * 初回の TOTP の登録（A-07）のセキュリティの統合テスト（Bolt 31b、US-18 AC7、SEC-09・SEC-16）。本物のフィルターの連なりと
 * Spring Session JDBC（PostgreSQL）の上で、登録の流れ、session に平文を置かないこと、登録の途中の上限（15 分）、CSRF を確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TotpEnrollmentSecurityIntegrationTest.FixedClock.class})
class TotpEnrollmentSecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-13T00:00:00Z");
    private static final String TOTP_URL = "/login/totp";
    private static final String ENROLL_URL = "/login/totp/enroll";
    private static final Pattern MANUAL_KEY = Pattern.compile("data-manual-key=\"([A-Z2-7 ]+)\"");
    private static final Pattern RECOVERY_CODE = Pattern.compile("[0-9a-f]{4}-[0-9a-f]{4}");

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
                .contains("この画面を開いてから 15 分以内に登録してください。")
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
        assertThat(sessionAttributeBytes(session))
                .as("session の属性に、手入力のキー（Base32 の平文の秘密）の文字列がない")
                .noneMatch(bytes -> new String(bytes, StandardCharsets.ISO_8859_1).contains(first));
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
        assertThat(RECOVERY_CODE
                        .matcher(html)
                        .results()
                        .map(match -> match.group())
                        .distinct())
                .hasSize(10);
        assertThat(html).contains("回復コードを安全な場所に保存した").contains("required");
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

        advance(Duration.ofMinutes(1).plus(Duration.ofMinutes(15)));
        mvc.perform(get(ENROLL_URL).cookie(late)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void A07を開いていないpasswordの段は5分のまま() throws Exception {
        Cookie session = loginWithPassword(unenrolledUser());

        advance(Duration.ofMinutes(5));
        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    // --- 補助 ---

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

    private List<byte[]> sessionAttributeBytes(Cookie session) {
        String sessionId = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.US_ASCII);
        return jdbc.query(
                "SELECT a.attribute_bytes FROM platform.spring_session_attributes a"
                        + " JOIN platform.spring_session s ON s.primary_id = a.session_primary_id WHERE s.session_id = ?",
                (rs, n) -> rs.getBytes(1),
                sessionId);
    }
}

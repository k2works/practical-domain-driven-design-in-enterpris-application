package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecordRepository;
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
import com.example.cargotracker.identity.domain.model.aggregates.UserStandingRepository;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * request ごとの利用者の確かめのセキュリティの統合テスト（Bolt 32c、US-18 AC6、IA-INV-04・IA-INV-08、ADR-012 の見直しの 3）。
 * 本物のフィルターの連なりと PostgreSQL の上で、利用停止・所属企業の無効化・利用者の削除の後の次の request が session を捨てて
 * A-03 に移り SESSION_REVOKED が残ること、役割の取消し・追加が次の request から認可とナビに効くこと、読み出しが 1 回であること、
 * 読み出し・監査の誤りと役割 0 件が session を捨てて 500 になることを確かめる。利用停止・役割の変更の操作は Bolt 34 で作るので、
 * DB の値を変えて確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SessionRevalidationSecurityIntegrationTest.FixedClock.class})
class SessionRevalidationSecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "SESSION";
    private static final Instant LOGIN_AT = Instant.parse("2026-10-14T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String TOTP_URL = "/login/totp";
    private static final String EXPIRED_URL = "/session-expired";
    private static final String SALES_URL = "/staff/transport-requests";
    private static final String ROUTING_URL = "/staff/routing-cases";
    private static final String TRACKING_URL = "/staff/tracking-records";
    private static final String RECOVERY_CODE = "1a2b-3c4d";
    /** フィルターがエラーのレベルで残す行の文言（ほかのログの行と分ける）。 */
    private static final String FAILURE_LOG = "request ごとの利用者の確かめに失敗した";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @MockitoSpyBean
    UserStandingRepository standings;

    @MockitoSpyBean
    UserRepository users;

    @MockitoSpyBean
    CompanyRepository companies;

    @MockitoSpyBean
    AuditRecordRepository auditRecords;

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
    TotpCodeGenerator totpCodes;

    @Autowired
    AesGcmSecretCipher cipher;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    RecoveryCodeHasher hasher;

    @Autowired
    org.springframework.security.web.FilterChainProxy filterChainProxy;

    private Company company;

    @BeforeEach
    void 時計を戻し企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        company = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(company);
    }

    // --- AC6 の利用停止・無効な企業・利用者の削除 ---

    @Test
    void 利用停止の後の次のrequestは業務データを出さずsessionを捨ててA03に移りSESSION_REVOKEDが残る() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(status().isOk());
        assertThat(sessionExists(session)).as("止める前は session がある").isTrue();

        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", email);
        MvcResult revoked = mvc.perform(get(SALES_URL).cookie(session))
                .andExpect(redirectedUrl(EXPIRED_URL))
                .andReturn();

        assertThat(revoked.getResponse().getContentAsString()).as("業務データを出さない").isEmpty();
        assertThat(sessionExists(session)).as("session を捨てた").isFalse();
        assertThat(revocations(email)).containsExactly(Map.of("result", "FAILURE", "reason", "SUSPENDED"));
        assertThat(companyOfRevocation(email)).isEqualTo(company.id().value());
    }

    @Test
    void 所属企業を無効にした後の次のrequestはCOMPANY_INACTIVEで取り消す() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(status().isOk());

        jdbc.update(
                "UPDATE identity.company SET active = FALSE WHERE id = ?",
                company.id().value());

        mvc.perform(get(SALES_URL).cookie(session)).andExpect(redirectedUrl(EXPIRED_URL));
        assertThat(sessionExists(session)).isFalse();
        assertThat(revocations(email)).containsExactly(Map.of("result", "FAILURE", "reason", "COMPANY_INACTIVE"));
    }

    @Test
    void 利用者を消した後の次のrequestはUNKNOWN_USERで取り消しsessionの主体で記録する() throws Exception {
        String email = user(Set.of(Role.SALES));
        UserId userId = userIdOf(email);
        Cookie session = login(email);
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(status().isOk());

        deleteUser(userId);

        mvc.perform(get(SALES_URL).cookie(session)).andExpect(redirectedUrl(EXPIRED_URL));
        assertThat(sessionExists(session)).isFalse();
        assertThat(jdbc.queryForList(
                        "SELECT reason FROM identity.audit_record WHERE actor_user_id = ? AND action = 'SESSION_REVOKED'",
                        String.class,
                        userId.value()))
                .containsExactly("UNKNOWN_USER");
    }

    @Test
    void passwordの段だけの主体も利用停止の後はA02の送信がA03になりTOTPを照合しない() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie passwordStage = loginWithPassword(email);

        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", email);
        mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("code", currentCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl(EXPIRED_URL));

        assertThat(sessionExists(passwordStage)).isFalse();
        assertThat(actions(email)).containsOnlyOnce("SESSION_REVOKED").doesNotContain("LOGIN_SUCCEEDED");
        assertThat(lastUsedStep(email)).as("TOTP の照合に進まない（刻みを使わない）").isZero();
    }

    @Test
    void passwordの段だけの主体は利用停止の後は回復コードの送信もA03になり回復コードを使わない() throws Exception {
        String email = user(Set.of(Role.SALES));
        UserId userId = userIdOf(email);
        UtcInstant now = new UtcInstant(clock.instant());
        recoveryCodes.replaceAll(
                userId,
                IntStream.range(0, RecoveryCode.CODES_PER_USER)
                        .mapToObj(i -> RecoveryCode.issue(
                                userId, i == 0 ? RECOVERY_CODE : String.format("0000-%04x", i), hasher, now))
                        .toList());
        Cookie passwordStage = loginWithPassword(email);
        mvc.perform(get(TOTP_URL).cookie(passwordStage)).andExpect(status().isOk());
        assertThat(sessionExists(passwordStage)).as("止める前は session がある").isTrue();

        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", email);
        mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("recoveryCode", RECOVERY_CODE)
                        .with(csrf()))
                .andExpect(redirectedUrl(EXPIRED_URL));

        assertThat(sessionExists(passwordStage)).isFalse();
        assertThat(recoveryCodes.findUnusedByUserId(userId)).as("回復コードを使わない").hasSize(RecoveryCode.CODES_PER_USER);
        assertThat(actions(email))
                .containsOnlyOnce("SESSION_REVOKED")
                .doesNotContain("RECOVERY_CODE_USED", "LOGIN_SUCCEEDED");
    }

    // --- 役割の取消し・追加 ---

    @Test
    void 役割を外すと次のrequestでその役割のURLがA04になり残した役割のURLは開け監査記録は書かない() throws Exception {
        String email = user(Set.of(Role.SALES, Role.ROUTE_DESIGNER));
        Cookie session = login(email);
        mvc.perform(get(ROUTING_URL).cookie(session)).andExpect(status().isOk());
        assertThat(salesPage(session)).as("外す前はナビに経路設計がある").contains("href=\"/staff/routing-cases\"");

        jdbc.update(
                "DELETE FROM identity.user_role WHERE user_id = ? AND role = 'ROUTE_DESIGNER'",
                userIdOf(email).value());

        mvc.perform(get(ROUTING_URL).cookie(session)).andExpect(status().isForbidden());
        String salesPage = mvc.perform(get(SALES_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(salesPage).as("ナビから経路設計が消える").doesNotContain("href=\"/staff/routing-cases\"");
        assertThat(sessionExists(session)).as("session は捨てない").isTrue();
        assertThat(actions(email)).doesNotContain("SESSION_REVOKED");
    }

    @Test
    void 役割を足すと次のrequestからそのURLとナビの項目が出る() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        mvc.perform(get(TRACKING_URL).cookie(session)).andExpect(status().isForbidden());

        jdbc.update(
                "INSERT INTO identity.user_role (user_id, role, granted_at) VALUES (?, 'TRACKING_MANAGER', now())",
                userIdOf(email).value());

        mvc.perform(get(TRACKING_URL).cookie(session)).andExpect(status().isOk());
        String salesPage = mvc.perform(get(SALES_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(salesPage).contains("href=\"/staff/tracking-records\"");
    }

    @Test
    void 役割を変えても要素の権限と8時間の起点は変わらない() throws Exception {
        String email = user(Set.of(Role.SALES, Role.ROUTE_DESIGNER));
        Cookie session = login(email);
        jdbc.update(
                "DELETE FROM identity.user_role WHERE user_id = ? AND role = 'ROUTE_DESIGNER'",
                userIdOf(email).value());

        advance(Duration.ofHours(8).minusSeconds(1));
        // 2 つの要素を求める業務の URL が開ける（要素の権限が残る）
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(status().isOk());
        advance(Duration.ofHours(8));
        // 8 時間の起点はログインのまま
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(redirectedUrl(EXPIRED_URL));
    }

    // --- 同じ session での利用者の切り替え（T-102） ---

    @Test
    void 同じsessionで別の利用者にログインし直すと後の利用者で確かめる() throws Exception {
        String first = user(Set.of(Role.SALES));
        String second = user(Set.of(Role.SALES));
        Cookie firstSession = login(first);
        Cookie switched = submitTotp(loginWithPassword(second, firstSession), second);

        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", first);
        mvc.perform(get(SALES_URL).cookie(switched)).andExpect(status().isOk());
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", second);
        mvc.perform(get(SALES_URL).cookie(switched)).andExpect(redirectedUrl(EXPIRED_URL));

        assertThat(actions(first)).doesNotContain("SESSION_REVOKED");
        assertThat(actions(second)).containsOnlyOnce("SESSION_REVOKED");
    }

    // --- 読み出しの回数（ADR-012 の見直しの 3） ---

    @Test
    void 認証済みの1つのrequestの読み出しは1回で利用者と企業のリポジトリは呼ばない() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        clearInvocations(standings, users, companies);

        mvc.perform(get(SALES_URL).cookie(session)).andExpect(status().isOk());

        verify(standings, times(1)).findById(userIdOf(email));
        verify(users, never()).findById(any());
        verify(users, never()).findByEmail(any());
        verify(companies, never()).findById(any());
    }

    @Test
    void 未認証のrequestと期限の切れたsessionでは読まない() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        advance(Duration.ofHours(8));
        clearInvocations(standings);

        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get(SALES_URL)).andExpect(status().is3xxRedirection());
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(redirectedUrl(EXPIRED_URL));

        verify(standings, never()).findById(any());
    }

    @Test
    void 認証済みのsessionでも公開のURLと静的な資源では読まない() throws Exception {
        // 1 画面の表示で静的な資源の数だけ読まず、止められた利用者の並行の資源の request で取消しを重ねて書かない（Bolt 32c の開発レビュー）
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(status().isOk());
        clearInvocations(standings);

        mvc.perform(get("/css/app.css").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/js/transport-request-form.js").cookie(session)).andExpect(status().isOk());
        mvc.perform(get(EXPIRED_URL).cookie(session)).andExpect(status().isOk());

        verify(standings, never()).findById(any());
        mvc.perform(get(SALES_URL).cookie(session)).andExpect(status().isOk());
        verify(standings, times(1)).findById(userIdOf(email));
    }

    @Test
    void 確かめのフィルターは期限のフィルターの直後で認証のフィルターより前にある() {
        // addFilterAfter は順序の値が UsernamePasswordAuthenticationFilter と同じになり、登録の順で前に来る。その順を見張る（開発レビュー）
        List<Class<?>> order = filterChainProxy.getFilterChains().getFirst().getFilters().stream()
                .<Class<?>>map(Object::getClass)
                .toList();

        assertThat(order)
                .containsSubsequence(
                        SessionLifetimeFilter.class,
                        SessionRevalidationFilter.class,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class,
                        TotpAuthenticationFilter.class);
        assertThat(order.indexOf(SessionRevalidationFilter.class))
                .as("期限のフィルターの直後")
                .isEqualTo(order.indexOf(SessionLifetimeFilter.class) + 1);
    }

    // --- 誤りは fail-closed ---

    @Test
    void 読み出しのDBの誤りはsessionを捨てて500にしエラーのログを残す() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        doThrow(new DataAccessResourceFailureException("DB の誤り"))
                .when(standings)
                .findById(any());
        RequestBuilder request = get(SALES_URL).cookie(session);

        String logs;
        try (LogCapture capture = LogCapture.start()) {
            assertThatThrownBy(() -> mvc.perform(request)).isInstanceOf(DataAccessResourceFailureException.class);
            logs = capture.text();
        } finally {
            reset(standings);
        }

        assertThat(sessionExists(session)).isFalse();
        assertThat(logs).contains(FAILURE_LOG, userIdOf(email).value().toString());
    }

    @Test
    void 取消しの監査記録の書き込みの誤りはsessionを捨てて500にする() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        jdbc.update("UPDATE identity.app_user SET status = 'SUSPENDED' WHERE email = ?", email);
        doThrow(new DataAccessResourceFailureException("DB の誤り"))
                .when(auditRecords)
                .append(any());
        RequestBuilder request = get(SALES_URL).cookie(session);

        try (LogCapture capture = LogCapture.start()) {
            assertThatThrownBy(() -> mvc.perform(request)).isInstanceOf(DataAccessResourceFailureException.class);
            assertThat(capture.text())
                    .contains(FAILURE_LOG, userIdOf(email).value().toString());
        } finally {
            reset(auditRecords);
        }

        assertThat(sessionExists(session)).isFalse();
        assertThat(actions(email)).doesNotContain("SESSION_REVOKED");
    }

    @Test
    void 役割が0件の利用者はsessionを捨てて500にする() throws Exception {
        String email = user(Set.of(Role.SALES));
        Cookie session = login(email);
        jdbc.update(
                "DELETE FROM identity.user_role WHERE user_id = ?",
                userIdOf(email).value());
        RequestBuilder request = get(SALES_URL).cookie(session);

        try (LogCapture capture = LogCapture.start()) {
            assertThatThrownBy(() -> mvc.perform(request)).isInstanceOf(IllegalStateException.class);
            assertThat(capture.text())
                    .contains(FAILURE_LOG, userIdOf(email).value().toString());
        }
        assertThat(sessionExists(session)).isFalse();
    }

    // --- 補助 ---

    private void advance(Duration duration) {
        ((MutableClock) clock).setInstant(LOGIN_AT.plus(duration));
    }

    private String user(Set<Role> roles) {
        String email = "revalidate-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of(email),
                "営業 一郎",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                roles));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return email;
    }

    private Cookie login(String email) throws Exception {
        return submitTotp(loginWithPassword(email), email);
    }

    private Cookie loginWithPassword(String email) throws Exception {
        return loginWithPassword(email, null);
    }

    private Cookie loginWithPassword(String email, Cookie current) throws Exception {
        var request = post("/login")
                .param("username", email)
                .param("password", PASSWORD)
                .with(csrf());
        MvcResult result = mvc.perform(current == null ? request : request.cookie(current))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        return sessionOf(result);
    }

    private Cookie submitTotp(Cookie passwordStage, String email) throws Exception {
        MvcResult result = mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("code", currentCode(email))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl())
                .doesNotContain("error")
                .doesNotContain("expired");
        return sessionOf(result);
    }

    private String currentCode(String email) {
        TotpCredential credential =
                totpCredentials.findByUserId(userIdOf(email)).orElseThrow();
        return totpCodes.code(
                credential.userId(), credential.secret(), TotpCredential.stepAt(new UtcInstant(clock.instant())));
    }

    private String salesPage(Cookie session) throws Exception {
        return mvc.perform(get(SALES_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long lastUsedStep(String email) {
        return totpCredentials.findByUserId(userIdOf(email)).orElseThrow().lastUsedStep();
    }

    private static Cookie sessionOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("session の Cookie").isNotNull();
        return cookie;
    }

    private boolean sessionExists(Cookie session) {
        String sessionId = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.US_ASCII);
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM platform.spring_session WHERE session_id = ?", Integer.class, sessionId);
        return rows != null && rows > 0;
    }

    private UserId userIdOf(String email) {
        return jdbc.queryForObject(
                "SELECT id FROM identity.app_user WHERE email = ?",
                (rs, n) -> new UserId(rs.getObject("id", UUID.class)),
                email);
    }

    private void deleteUser(UserId userId) {
        for (String table : List.of(
                "identity.user_role", "identity.totp_credential", "identity.recovery_code", "identity.login_failure")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId.value());
        }
        jdbc.update("DELETE FROM identity.app_user WHERE id = ?", userId.value());
    }

    private List<String> actions(String email) {
        return jdbc.queryForList(
                "SELECT action FROM identity.audit_record WHERE actor_user_id = ?",
                String.class,
                userIdOf(email).value());
    }

    private List<Map<String, String>> revocations(String email) {
        return jdbc.query(
                "SELECT result, reason FROM identity.audit_record WHERE actor_user_id = ? AND action = 'SESSION_REVOKED'",
                (rs, n) -> Map.of("result", rs.getString("result"), "reason", rs.getString("reason")),
                userIdOf(email).value());
    }

    private UUID companyOfRevocation(String email) {
        return jdbc.queryForObject(
                "SELECT actor_company_id FROM identity.audit_record WHERE actor_user_id = ? AND action = 'SESSION_REVOKED'",
                UUID.class,
                userIdOf(email).value());
    }
}

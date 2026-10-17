package com.example.cargotracker.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.domain.model.aggregates.AuditRecord;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCode;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeHasher;
import com.example.cargotracker.identity.domain.model.aggregates.RecoveryCodeRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AuditAction;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * 認証の入口の誤りの扱い（Bolt 32 の確認ポイント 8、Bolt 31d から送った「実装の誤りは 500」）。実装の誤りと書き込みの DB の誤りは
 * 500 にしてログインを通さず、利用者の誤りとして数えない。利用者の読み出しの DB の誤りは、枠組みが包んで失敗へ倒し
 * {@code /login?error}（理由 {@code SERVICE_ERROR}、数えない）。ロックの読み出しの DB の誤りは 500（照合の後の確認と TOTP の段）。
 */
class AuthenticationErrorHandlingIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final String PASSWORD = "correct horse battery staple";
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private static final String TOTP_URL = "/login/totp";
    private static final String RECOVERY_CODE = "1a2b-3c4d";
    private static final String SECOND_RECOVERY_CODE = "5e6f-7a8b";
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    MockMvc mvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    AesGcmSecretCipher cipher;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Autowired
    RecoveryCodeRepository recoveryCodes;

    @Autowired
    RecoveryCodeHasher recoveryCodeHasher;

    private Company company;

    @BeforeEach
    void 企業を登録する() {
        // 共通の時計（MutableClock）を決めた時刻に置く。前のテストのクラスが残した時刻を引き継がない（Bolt 32d）
        ((MutableClock) clock).setInstant(NOW);
        company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(company);
    }

    @Test
    void 実装の誤りは500にしログインを通さず利用者の誤りとして数えない() throws Exception {
        String email = user();
        Cookie session = logins().loginWithPassword(email);
        doThrow(new IllegalArgumentException("値の検証の誤り")).when(totpCodes).code(any(), any(), anyLong());
        RequestBuilder code = totpCode(session);

        assertThatThrownBy(() -> mvc.perform(code)).isInstanceOf(IllegalArgumentException.class);

        assertThat(failureReasons(email)).as("監査の失敗を残さない").isEmpty();
        assertThat(failures(email)).isZero();
    }

    @Test
    void 失敗の書き込みのDBの誤りは500にし誤りの応答にしない() {
        String email = user();
        doThrow(new DataAccessResourceFailureException("DB の誤り"))
                .when(loginFailures)
                .recordFailure(any(), any(), any());
        RequestBuilder wrong = password(email, "wrong");

        assertThatThrownBy(() -> mvc.perform(wrong)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(failureReasons(email)).as("回数と同じトランザクションの監査記録も戻る").isEmpty();
    }

    @Test
    void 利用者の読み出しのDBの誤りは失敗へ倒れ理由SERVICE_ERRORで数えない() throws Exception {
        String email = user();
        int before = serviceErrors();
        doThrow(new DataAccessResourceFailureException("DB の誤り")).when(users).findByEmail(any());

        String logs;
        try (LogCapture capture = LogCapture.start()) {
            mvc.perform(post("/login")
                            .param("username", email)
                            .param("password", PASSWORD)
                            .with(csrf()))
                    .andExpect(redirectedUrl("/login?error"));
            logs = capture.text();
        }

        assertThat(serviceErrors()).as("この送信の 1 行").isEqualTo(before + 1);
        assertThat(logs).doesNotContain(PASSWORD);
        assertThat(failures(email)).as("数えない").isZero();

        org.mockito.Mockito.reset(users);
        mvc.perform(password(email, "wrong")).andExpect(redirectedUrl("/login?error"));
        assertThat(failures(email)).as("DB が戻れば誤りは数える").isEqualTo(1);
    }

    @Test
    void 失敗の監査記録の書き込みのDBの誤りは回数も戻す() {
        // IA-INV-08: 加算の後に監査の追記が失敗したら、回数も同じトランザクションで戻る
        String email = user();
        doThrow(new DataAccessResourceFailureException("DB の誤り"))
                .when(auditRecords)
                .append(argThat((AuditRecord appended) -> appended.action() == AuditAction.LOGIN_FAILED));
        RequestBuilder wrong = password(email, "wrong");

        assertThatThrownBy(() -> mvc.perform(wrong)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(failures(email)).isZero();
    }

    @Test
    void ロックの監査記録の書き込みのDBの誤りは5回目の加算も戻しロックしない() throws Exception {
        String email = user();
        for (int i = 0; i < 4; i++) {
            mvc.perform(password(email, "wrong")).andExpect(redirectedUrl("/login?error"));
        }
        doThrow(new DataAccessResourceFailureException("DB の誤り"))
                .when(auditRecords)
                .append(argThat((AuditRecord appended) -> appended.action() == AuditAction.ACCOUNT_LOCKED));
        RequestBuilder fifth = password(email, "wrong");

        assertThatThrownBy(() -> mvc.perform(fifth)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(failures(email)).as("4 回のまま").isEqualTo(4);
        assertThat(failureReasons(email)).as("5 回目の LOGIN_FAILED も戻る").hasSize(4);
    }

    @Test
    void ロックの読み出しのDBの誤りはpasswordの段でもTOTPの段でも500にする() throws Exception {
        String email = user();
        Cookie session = logins().loginWithPassword(email);
        doThrow(new DataAccessResourceFailureException("DB の誤り"))
                .when(loginFailures)
                .findByUserId(any());
        RequestBuilder correct = password(email, PASSWORD);
        RequestBuilder code = totpCode(session);
        RequestBuilder recovery = post(TOTP_URL)
                .cookie(session)
                .param("recoveryCode", RECOVERY_CODE)
                .with(csrf());

        assertThatThrownBy(() -> mvc.perform(correct))
                .as("照合の後の確認の例外は枠組みが包まない")
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThatThrownBy(() -> mvc.perform(code)).isInstanceOf(DataAccessResourceFailureException.class);
        assertThatThrownBy(() -> mvc.perform(recovery)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void 回復コードのログインで成功の監査を書けなければ500にしコードは使用済みのまま残る() throws Exception {
        // 特性テスト（Bolt 34c の確認ポイント 7）。コードの使用と RECOVERY_CODE_USED は 1 つのトランザクション、LOGIN_SUCCEEDED と回数の
        // 戻しは成功のイベントのリスナーが別のトランザクションで書く（IA-INV-08）。後者が失敗したときの今の振る舞いを固定する。
        // 望ましい振る舞いかは後の Bolt の議題（使ったコードが戻らず、同じ session で送り直すたびにコードが減る）。変えるときは書き換える
        String email = user();
        UserId userId = userIdOf(email);
        issueRecoveryCodes(userId);
        mvc.perform(password(email, "wrong-" + PASSWORD)).andExpect(redirectedUrl("/login?error"));
        assertThat(failures(email))
                .as("誤った password で 1 回と数える（password の段の成功では戻さない）")
                .isEqualTo(1);
        Cookie session = logins().loginWithPassword(email);
        doThrow(new DataAccessResourceFailureException("監査の表に届かない"))
                .when(auditRecords)
                .append(argThat(written -> written.action() == AuditAction.LOGIN_SUCCEEDED));
        RequestBuilder first = recoveryCode(session, RECOVERY_CODE);
        RequestBuilder second = recoveryCode(session, SECOND_RECOVERY_CODE);

        assertThatThrownBy(() -> mvc.perform(first))
                .as("500 にする（監査を書けないログインを通さない）")
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(recoveryCodes.findUnusedByUserId(userId))
                .as("使ったコードは戻らない（コードの使用と RECOVERY_CODE_USED は先にコミットしている）")
                .hasSize(RecoveryCode.CODES_PER_USER - 1);
        assertThat(auditCount(AuditAction.RECOVERY_CODE_USED, userId))
                .as("コードの使用と同じトランザクションで先にコミットされる")
                .isEqualTo(1);
        assertThat(auditCount(AuditAction.LOGIN_SUCCEEDED, userId))
                .as("成功の監査はリスナーの別のトランザクションで書けない")
                .isZero();
        assertThat(failures(email)).as("回数の戻しも書かれない").isEqualTo(1);
        assertThat(failureReasons(email)).as("誤りとして数えない").hasSize(1);
        mvc.perform(get("/customer/transport-requests").cookie(session))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .as("session は password の段のまま残り、A-02 へ導く")
                        .startsWith(TOTP_URL + "?factor.type=totp"));
        assertThatThrownBy(() -> mvc.perform(second))
                .as("同じ session で別のコードを送り直せ、また 500 になる")
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(recoveryCodes.findUnusedByUserId(userId))
                .as("送り直すたびにコードが 1 つずつ使用済みになる")
                .hasSize(RecoveryCode.CODES_PER_USER - 2);
    }

    private void issueRecoveryCodes(UserId userId) {
        UtcInstant now = new UtcInstant(clock.instant());
        recoveryCodes.replaceAll(
                userId,
                IntStream.range(0, RecoveryCode.CODES_PER_USER)
                        .mapToObj(i -> RecoveryCode.issue(
                                userId,
                                i == 0 ? RECOVERY_CODE : i == 1 ? SECOND_RECOVERY_CODE : String.format("0000-%04x", i),
                                recoveryCodeHasher,
                                now))
                        .toList());
    }

    private static RequestBuilder recoveryCode(Cookie session, String code) {
        return post(TOTP_URL).cookie(session).param("recoveryCode", code).with(csrf());
    }

    private int auditCount(AuditAction action, UserId userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = ? AND actor_user_id = ?",
                Integer.class,
                action.name(),
                userId.value());
    }

    // --- 補助 ---

    private static RequestBuilder password(String email, String password) {
        return post("/login")
                .param("username", email)
                .param("password", password)
                .with(csrf());
    }

    private static RequestBuilder totpCode(Cookie session) {
        return post(TOTP_URL).cookie(session).param("code", "123456").with(csrf());
    }

    private String user() {
        String email = "error-" + UUID.randomUUID() + "@example.com";
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

    private UserId userIdOf(String email) {
        return new UserId(jdbc.queryForObject("SELECT id FROM identity.app_user WHERE email = ?", UUID.class, email));
    }

    private int failures(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT coalesce(max(consecutive_failures), 0) FROM identity.login_failure WHERE user_id = ?",
                Integer.class,
                userIdOf(email).value());
        return count == null ? 0 : count;
    }

    private int serviceErrors() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'LOGIN_FAILED' AND reason = 'SERVICE_ERROR'",
                Integer.class);
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

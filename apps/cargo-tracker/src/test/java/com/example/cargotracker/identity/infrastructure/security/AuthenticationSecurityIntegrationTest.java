package com.example.cargotracker.identity.infrastructure.security;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static com.example.cargotracker.identity.IdentityLogins.SESSION_COOKIE;
import static com.example.cargotracker.identity.IdentityLogins.TOTP_URL;
import static com.example.cargotracker.identity.IdentityLogins.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AuditRecordRows;
import com.example.cargotracker.identity.AuthenticationFactorFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.LogCapture;
import com.example.cargotracker.identity.SpringSessionRows;
import com.example.cargotracker.identity.application.internal.session.SessionKeys;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetToken;
import com.example.cargotracker.identity.domain.model.aggregates.PasswordResetTokenRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.PasswordResetTokenPurpose;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.identity.infrastructure.crypto.Base32;
import com.example.cargotracker.identity.infrastructure.crypto.Sha256PasswordResetTokenHasher;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * セキュリティの統合テスト（US-18 の password の段と TOTP の段、ADR-011・ADR-012、テスト戦略「セキュリティの統合テスト」）。
 * 本物のフィルターの連なりと Spring Session JDBC（PostgreSQL 18）の上で、ログイン・拒否・失効・CSRF・役割・監査を確かめる。
 * session は Cookie（{@code SESSION}）で受け渡し、ログインの前後で値が変わることを見る（session の固定化の防止。T-40）。
 */
@IdentityIntegrationTest
class AuthenticationSecurityIntegrationTest {

    /** 全 URL の見張りから除く、password の段だけで開ける認証の画面と、未認証で開ける公開の画面（Bolt 31d。A-05 は Bolt 33）。 */
    private static final Set<String> AUTHENTICATION_SCREENS = Set.of(
            "/login",
            "/login/totp",
            "/login/totp/enroll",
            "/session-expired",
            "/password-reset",
            "/password-reset/new",
            "/password-reset/{token}");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private static final Instant LOGIN_AT = Instant.parse("2026-10-06T00:00:00Z");
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
    TrackingRecordRepository trackingRecords;

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    TotpCodeGenerator totpCodes;

    @Autowired
    AesGcmSecretCipher cipher;

    private Company shipperCompany;

    @BeforeEach
    void 時計を戻し荷主の企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        shipperCompany = company(true);
    }

    // --- 未認証 ---

    @Test
    void 未認証で荷主の画面を開くとログインへ移り業務データを返さない() throws Exception {
        mvc.perform(get("/customer/transport-requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 未認証で社内の画面を開くとログインへ移る() throws Exception {
        mvc.perform(get("/staff/transport-requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // --- ログインの成功（AC1） ---

    @Test
    void 荷主担当者はpasswordの後にTOTPを入れると荷主のホームへ移り段ごとにsessionIDが変わる() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie before = sessionOf(mvc.perform(get("/login")).andReturn());

        Cookie passwordStage = sessionOf(mvc.perform(post("/login")
                        .cookie(before)
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn());
        MvcResult totp = mvc.perform(post(TOTP_URL)
                        .cookie(passwordStage)
                        .param("code", logins().currentCode(email))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie after = sessionOf(totp);
        assertThat(passwordStage.getValue()).isNotEqualTo(before.getValue());
        assertThat(after.getValue()).as("権限が上がる TOTP の段でも変える（T-40）").isNotEqualTo(passwordStage.getValue());
        mvc.perform(get("/").cookie(after)).andExpect(redirectedUrl("/customer/transport-requests"));
        mvc.perform(get("/customer/transport-requests").cookie(after)).andExpect(status().isOk());
    }

    // --- TOTP の段（US-18 AC1、ADR-011。Bolt 31） ---

    @Test
    void passwordだけでは業務の画面を開けずA02へ導かれる() throws Exception {
        Cookie session = logins().loginWithPassword(shipper(UserStatus.ACTIVE));

        mvc.perform(get("/customer/transport-requests").cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectsToTotp());
        mvc.perform(get("/").cookie(session)).andExpect(redirectsToTotp());
        mvc.perform(get(TOTP_URL).cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("autocomplete=\"one-time-code\"")))
                // 欄の桁数は TotpCredential.DIGITS から描く。表示は Bolt 33d の前と同じ（見出しの文言は残す）
                .andExpect(content().string(Matchers.containsString("pattern=\"[0-9]{6}\"")))
                .andExpect(content().string(Matchers.containsString("maxlength=\"6\"")))
                .andExpect(content().string(Matchers.containsString("6 桁のコードを入力してください")));
    }

    @Test
    void passwordの段の主体は画面のモデルに業務の利用者として置かず二つの要素がそろうと置く() throws Exception {
        // ADR-012 の見直しの 4（Bolt 31c）。「ないこと」と並べて「あるべきもの」を確かめる（T-106）
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().loginWithPassword(email);

        mvc.perform(get(TOTP_URL).cookie(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentActor", Matchers.nullValue()));
        Cookie full = submitTotp(session, logins().currentCode(email));
        mvc.perform(get("/customer/transport-requests").cookie(full))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentActor", Matchers.notNullValue()));
    }

    @Test
    void A02のPOSTにもCSRFのトークンが要る() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().loginWithPassword(email);

        // A-02 の画面を開くと session に CSRF のトークンができる。トークンを付けない送信と誤ったトークンの送信は拒否する
        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(status().isOk());

        mvc.perform(post(TOTP_URL).cookie(session).param("code", logins().currentCode(email)))
                .andExpect(status().isForbidden());
        mvc.perform(post(TOTP_URL)
                        .cookie(session)
                        .param("code", logins().currentCode(email))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectsToTotp());
    }

    @Test
    void passwordの段を経ずにTOTPを送っても通らず認証コードの誤りの文言も出さない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        mvc.perform(post(TOTP_URL).param("code", logins().currentCode(email)).with(csrf()))
                .andExpect(redirectedUrl("/login"));

        mvc.perform(get(TOTP_URL)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 誤ったTOTPではpasswordの要素を捨ててA01に戻る() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().loginWithPassword(email);

        mvc.perform(post(TOTP_URL)
                        .cookie(session)
                        .param("code", wrongCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?totp-error"));

        // 捨てた session の Cookie は、無効な session として A-03 に送られる（A-02 を開けない）
        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(redirectedUrl("/session-expired"));
        mvc.perform(get("/login").param("totp-error", ""))
                .andExpect(
                        content()
                                .string(
                                        Matchers.containsString(
                                                "認証コードまたは回復コードが正しくないか、すでに使われています。もう一度ログインして、認証アプリに表示された新しいコードか、まだ使っていない回復コードを入れてください。")));
    }

    @Test
    void 一度通ったコードは同じ刻みの間にもう一度は使えない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        String code = logins().currentCode(email);
        submitTotp(logins().loginWithPassword(email), code);

        mvc.perform(post(TOTP_URL)
                        .cookie(logins().loginWithPassword(email))
                        .param("code", code)
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?totp-error"));
    }

    @Test
    void 別の利用者のpasswordを送ると先の利用者のTOTPの要素は引き継がれない() throws Exception {
        String first = shipper(UserStatus.ACTIVE);
        String second = shipper(UserStatus.ACTIVE);
        Cookie session = logins().login(first);

        Cookie switched = sessionOf(mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", second)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn());

        mvc.perform(get("/customer/transport-requests").cookie(switched)).andExpect(redirectsToTotp());
    }

    @Test
    void 利用者を切り替えたpasswordの段は前の利用者の時刻を引き継がず5分で失効しTOTPから8時間を数え直す() throws Exception {
        String first = shipper(UserStatus.ACTIVE);
        String second = shipper(UserStatus.ACTIVE);
        Cookie session = logins().login(first);
        advance(Duration.ofHours(7));
        Cookie switched = sessionOf(mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", second)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn());

        advance(Duration.ofHours(7).plusMinutes(4));
        Cookie secondSession = submitTotp(switched, logins().currentCode(second));

        advance(Duration.ofHours(8).plusMinutes(1));
        // 前の利用者の起点（0 時）から 8 時間を過ぎても、後の利用者の TOTP の時刻（7 時間 4 分）から数える
        mvc.perform(get("/customer/transport-requests").cookie(secondSession)).andExpect(status().isOk());

        Cookie other = logins().login(first);
        Cookie switchedAgain = sessionOf(mvc.perform(post("/login")
                        .cookie(other)
                        .param("username", second)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn());
        advance(Duration.ofHours(8).plusMinutes(6));
        // 切り替えた password の段は 5 分で失効する
        mvc.perform(get(TOTP_URL).cookie(switchedAgain)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void TOTPを登録していない利用者はpasswordの後にA02を経て登録の画面へ移り業務の画面を開けない() throws Exception {
        String email = userWithoutTotp(shipperCompany, Role.SHIPPER);

        // 認証の入口は登録の有無を見ずに A-02 へ送り、A-02 の画面が未登録を A-07 へ送る（Bolt 31b の確認ポイント 10）
        Cookie session = sessionOf(mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn());

        mvc.perform(get("/login/totp/enroll").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("認証アプリで QR コードを読み取る")));
        mvc.perform(get(TOTP_URL).cookie(session)).andExpect(redirectedUrl("/login/totp/enroll"));
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectsToTotp());
    }

    @Test
    void passwordの段は5分の1秒前までTOTPを送れ5分で再認証の案内へ移る() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie early = logins().loginWithPassword(email);
        Cookie late = logins().loginWithPassword(email);

        advance(Duration.ofMinutes(5).minusSeconds(1));
        submitTotp(early, logins().currentCode(email));

        advance(Duration.ofMinutes(5));
        resetTotpStep(email);
        mvc.perform(post(TOTP_URL)
                        .cookie(late)
                        .param("code", logins().currentCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 最長8時間はTOTPが初めて通った時刻から数える() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie passwordStage = logins().loginWithPassword(email);
        advance(Duration.ofMinutes(4));
        Cookie session = submitTotp(passwordStage, logins().currentCode(email));

        advance(Duration.ofHours(8));
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());

        advance(Duration.ofHours(8).plusMinutes(4));
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 最長8時間はTOTPの時刻から数えて1秒前は使える() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie passwordStage = logins().loginWithPassword(email);
        advance(Duration.ofMinutes(4));
        Cookie session = submitTotp(passwordStage, logins().currentCode(email));

        advance(Duration.ofMinutes(4).plus(Duration.ofHours(8)).minusSeconds(1));
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void 有効期間の中でTOTPを送り直しても8時間の起点は延びない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().login(email);
        advance(Duration.ofHours(7));

        Cookie resent = submitTotp(session, logins().currentCode(email));

        advance(Duration.ofHours(8));
        mvc.perform(get("/customer/transport-requests").cookie(resent)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void DBで別の利用者の暗号文に入れ替えるとTOTPは通らず500にせずA01に戻す() throws Exception {
        // 暗号文は持ち主の利用者 ID に結び付く（AAD。Bolt 31b）。DB に書ける者が別の利用者の暗号文を写しても、その秘密では通らない
        String victim = shipper(UserStatus.ACTIVE);
        String attacker = shipper(UserStatus.ACTIVE);
        jdbc.update(
                "UPDATE identity.totp_credential SET secret_encrypted ="
                        + " (SELECT secret_encrypted FROM identity.totp_credential WHERE user_id = ?) WHERE user_id = ?",
                userIdOf(attacker).value(),
                userIdOf(victim).value());
        Cookie session = logins().loginWithPassword(victim);
        TotpCredential attackersCredential =
                totpCredentials.findByUserId(userIdOf(attacker)).orElseThrow();
        String attackersCode = totpCodes.code(
                attackersCredential.userId(),
                attackersCredential.secret(),
                TotpCredential.stepAt(new UtcInstant(clock.instant())));

        mvc.perform(post(TOTP_URL).cookie(session).param("code", attackersCode).with(csrf()))
                .andExpect(redirectedUrl("/login?totp-error"));

        assertThat(auditOf(victim))
                .contains(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "INVALID_TOTP"));
    }

    @Test
    void 秘密を復号できない利用者は500にせずA01に戻し監査記録に残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().loginWithPassword(email);
        jdbc.update(
                "UPDATE identity.totp_credential SET secret_encrypted = 'v9:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'"
                        + " WHERE user_id = ?",
                userIdOf(email).value());

        String logs;
        try (LogCapture capture = LogCapture.start()) {
            mvc.perform(post(TOTP_URL).cookie(session).param("code", "654321").with(csrf()))
                    .andExpect(redirectedUrl("/login?totp-error"));
            logs = capture.text();
        }

        assertThat(auditOf(email))
                .contains(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "INVALID_TOTP"));
        // ログの平文の見張り（Bolt 31d）。エラーの行があること（T-107）と、送ったコード・password がないこと。この場面は暗号文を
        // 壊してあり平文の秘密はアプリの手に入らないので、秘密は正しい秘密で誤ったコードを送る場面で見張る
        assertThat(logs)
                .contains("TOTP の秘密を復号できず", userIdOf(email).value().toString())
                .doesNotContain("654321")
                .doesNotContain(PASSWORD);
    }

    @Test
    void 秘密を復号して誤ったコードを拒むときもログに秘密もコードも出さない() throws Exception {
        // ログの平文の見張り（Bolt 31d の開発レビュー）。秘密を実際に復号する場面で、ASCII と Base32 の両方の形を見る
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().loginWithPassword(email);
        String wrong = wrongCode(email);

        String logs;
        try (LogCapture capture = LogCapture.start()) {
            // この経路はエラーのログを出さないので、取り込みが効いていることを目印の行で確かめる（T-107）
            LoggerFactory.getLogger(AuthenticationSecurityIntegrationTest.class).info("ログの見張りの目印");
            mvc.perform(post(TOTP_URL).cookie(session).param("code", wrong).with(csrf()))
                    .andExpect(redirectedUrl("/login?totp-error"));
            logs = capture.text();
        }

        assertThat(logs)
                .as("ログの取り込みが効いている")
                .contains("ログの見張りの目印")
                .doesNotContain(new String(TOTP_SECRET, StandardCharsets.US_ASCII))
                .doesNotContain(Base32.encode(TOTP_SECRET))
                .doesNotContain(wrong)
                .doesNotContain(PASSWORD);
    }

    @Test
    void 本体のpasswordの符号化は委譲する形のbcryptで強さ10() {
        // スパイクの「既定の bcrypt の強さは 10」を本体の部品で確かめる（Bolt 31d の対応表の (d)）
        assertThat(passwordEncoder.encode(PASSWORD)).startsWith("{bcrypt}$2a$10$");
    }

    @Test
    void 期限切れのsessionにTOTPを送っても延びない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().login(email);
        advance(Duration.ofHours(8));

        mvc.perform(post(TOTP_URL)
                        .cookie(session)
                        .param("code", logins().currentCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl("/session-expired"));
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    // --- 8 時間の起点の値と監査の順（Bolt 31d から送った。Bolt 32d） ---

    @Test
    void 八時間の起点はTOTPが通った時刻で監査記録はpasswordの段の後にログインの成功が並ぶ() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie passwordStage = logins().loginWithPassword(email);
        Instant totpAt = LOGIN_AT.plus(Duration.ofMinutes(2));
        advance(Duration.ofMinutes(2));
        resetTotpStep(email);

        Cookie full = submitTotp(passwordStage, logins().currentCode(email));

        assertThat(sessions().attribute(full, SessionKeys.AUTHENTICATED_AT))
                .as("8 時間の起点は TOTP が通った時刻（password の段の時刻でない。Bolt 31）")
                .isEqualTo(totpAt);
        assertThat(new AuditRecordRows(jdbc).actionsAt(userIdOf(email), LOGIN_AT))
                .as("password の段が先")
                .containsExactly("PASSWORD_VERIFIED");
        assertThat(new AuditRecordRows(jdbc).actionsAt(userIdOf(email), totpAt)).containsExactly("LOGIN_SUCCEEDED");
        assertThat(new AuditRecordRows(jdbc).actionsOf(userIdOf(email)))
                .as("ほかの時刻の行はない")
                .hasSize(2);
    }

    // --- TOTP の失敗の監査（ADR-012 の見直しの 1、IA-INV-08。Bolt 31） ---

    @Test
    void 誤ったTOTPは利用者と理由で失敗として監査記録に残り存在しない利用者としては数えない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().loginWithPassword(email);
        Integer unknownBefore = unknownUserFailures();

        mvc.perform(post(TOTP_URL)
                        .cookie(session)
                        .param("code", wrongCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?totp-error"));

        assertThat(auditOf(email))
                .containsExactlyInAnyOrder(
                        Map.of("action", "PASSWORD_VERIFIED", "result", "SUCCESS", "reason", "-"),
                        Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "INVALID_TOTP"));
        assertThat(unknownUserFailures()).isEqualTo(unknownBefore);
    }

    @Test
    void TOTPを登録していない利用者がA02を経ずにコードを送ると登録していないという理由で監査記録に残る() throws Exception {
        // 確認ポイント 3（Bolt 31d）。利用者への応答は TOTP の誤りと同じ（AC2）
        String email = userWithoutTotp(shipperCompany, Role.SHIPPER);
        Cookie session = logins().loginWithPassword(email);

        mvc.perform(post(TOTP_URL).cookie(session).param("code", "123456").with(csrf()))
                .andExpect(redirectedUrl("/login?totp-error"));

        assertThat(auditOf(email))
                .filteredOn(row -> row.get("action").equals("LOGIN_FAILED"))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "TOTP_NOT_ENROLLED"));
    }

    @Test
    void 使ったコードの再利用は理由を分けて監査記録に残る() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        String code = logins().currentCode(email);
        submitTotp(logins().loginWithPassword(email), code);

        mvc.perform(post(TOTP_URL)
                        .cookie(logins().loginWithPassword(email))
                        .param("code", code)
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?totp-error"));

        assertThat(auditOf(email))
                .filteredOn(row -> row.get("action").equals("LOGIN_FAILED"))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "TOTP_REUSED"));
    }

    /**
     * 見張り（T-88。Bolt 31 のレビュー 17 で代表の URL から全 URL に広げた。Bolt 31d）。全体の注釈で 2 つの要素を求めるので、URL ごとの
     * 規則は役割だけのままにした（ADR-012 の見直しの 2）。役割がそろっていても TOTP の要素の欠けた主体は、画面のハンドラーの一覧の
     * どの URL（GET・POST）も開けず A-02 へ導かれる。役割の誤り（403）と区別するため、URL の接頭辞の規則に合う役割の主体で送る。
     * パスの変数は仮の値で埋める（認可はハンドラーより前に効くので、値の形は問わない）。
     */
    @Test
    void 役割があってもTOTPの要素の欠けた主体は画面のどのURLも開けずA02へ導かれる() throws Exception {
        Map<Role, Cookie> passwordStage = new EnumMap<>(Role.class);
        passwordStage.put(Role.SHIPPER, logins().loginWithPassword(shipper(UserStatus.ACTIVE)));
        passwordStage.put(Role.SALES, logins().loginWithPassword(user(staffCompany(), Role.SALES, UserStatus.ACTIVE)));
        passwordStage.put(
                Role.ROUTE_DESIGNER,
                logins().loginWithPassword(user(staffCompany(), Role.ROUTE_DESIGNER, UserStatus.ACTIVE)));
        passwordStage.put(
                Role.TRACKING_MANAGER,
                logins().loginWithPassword(user(staffCompany(), Role.TRACKING_MANAGER, UserStatus.ACTIVE)));
        List<String> checked = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : entry.getKey().getPatternValues()) {
                if (AUTHENTICATION_SCREENS.contains(pattern) || pattern.startsWith("/error")) {
                    continue;
                }
                String url = withSampleVariables(pattern);
                Cookie session = passwordStage.get(roleFor(url));
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    MockHttpServletRequestBuilder request = method == RequestMethod.GET
                            ? get(url)
                            : request(HttpMethod.valueOf(method.name()), url).with(csrf());
                    mvc.perform(request.cookie(session)).andExpect(redirectsToTotp());
                    checked.add(method + " " + url);
                }
            }
        }

        // 一覧が空のまま通らないことを確かめる（T-107）
        assertThat(checked)
                .as("見張りの対象の URL")
                .contains("GET /customer/transport-requests", "GET /staff/transport-requests")
                .anyMatch(each -> each.startsWith("POST "));
    }

    @Test
    void 営業担当者はログインすると社内のホームへ移る() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/transport-requests"));
    }

    @Test
    void 画面のまだない役割の利用者はログインできてもホームは権限なしになり行き先が循環しない() throws Exception {
        // 経路設計者は Bolt 17、追跡管理者は Bolt 26 でホームを持った。画面のまだない役割の例をデータ責任者にした
        Cookie session = logins().login(user(staffCompany(), Role.DATA_STEWARD, UserStatus.ACTIVE));

        mvc.perform(get("/").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/routing-cases").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/tracking-records").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 追跡管理者はログインすると追跡一覧へ移りほかの役割の画面は開けない() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.TRACKING_MANAGER, UserStatus.ACTIVE));

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/tracking-records"));
        mvc.perform(get("/staff/tracking-records").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/bookings").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/kpi-observations").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/routing-cases").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 荷主担当者と営業担当者と経路設計者は追跡の画面を開けない() throws Exception {
        for (Role role : List.of(Role.SHIPPER, Role.SALES, Role.ROUTE_DESIGNER)) {
            Cookie session = logins().login(
                            user(role == Role.SHIPPER ? shipperCompany : staffCompany(), role, UserStatus.ACTIVE));

            mvc.perform(get("/staff/tracking-records").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get("/staff/tracking-records/CTABCDEFGH2345").cookie(session))
                    .andExpect(status().isForbidden());
            // S-13 主要実績の登録も追跡管理者だけ（Bolt 26c）
            mvc.perform(get("/staff/tracking-records/CTABCDEFGH2345/milestones/new")
                            .cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/staff/tracking-records/CTABCDEFGH2345/milestones")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 社内のナビは役割の画面だけを出す() throws Exception {
        // 表示の制御は権限の防御ではない（URL を直接開いても A-04）。開発戦略のナビの骨格の確かめ（Bolt 26）。
        // ナビのリンクは 1 行で書く前提で、現在の項目は aria-current が href の後に付くので、属性の順によらずに探す
        Map<Role, String> homes = Map.of(
                Role.SALES, "/staff/transport-requests",
                Role.ROUTE_DESIGNER, "/staff/routing-cases",
                Role.TRACKING_MANAGER, "/staff/tracking-records");
        Map<String, String> items = Map.of(
                "/staff/transport-requests", "見積依頼",
                "/staff/bookings", "予約",
                "/staff/kpi-observations", "KPI 計測記録",
                "/staff/routing-cases", "経路設計",
                "/staff/tracking-records", "追跡");
        Map<Role, Set<String>> shown = Map.of(
                Role.SALES, Set.of("/staff/transport-requests", "/staff/bookings", "/staff/kpi-observations"),
                Role.ROUTE_DESIGNER, Set.of("/staff/routing-cases"),
                Role.TRACKING_MANAGER, Set.of("/staff/tracking-records"));
        for (Map.Entry<Role, String> home : homes.entrySet()) {
            Cookie session = logins().login(user(staffCompany(), home.getKey(), UserStatus.ACTIVE));
            String page = mvc.perform(get(home.getValue()).cookie(session))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            for (Map.Entry<String, String> item : items.entrySet()) {
                String link = "href=\"" + item.getKey() + "\"[^>]*>" + item.getValue() + "</a>";
                if (shown.get(home.getKey()).contains(item.getKey())) {
                    assertThat(page)
                            .as("%s のナビの %s", home.getKey(), item.getValue())
                            .containsPattern(link);
                } else {
                    assertThat(page)
                            .as("%s のナビの %s", home.getKey(), item.getValue())
                            .doesNotContainPattern(link);
                }
            }
        }
    }

    @Test
    void 経路設計者はログインすると案件一覧へ移り営業の画面は開けない() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.ROUTE_DESIGNER, UserStatus.ACTIVE));

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl("/staff/routing-cases"));
        mvc.perform(get("/staff/routing-cases").cookie(session)).andExpect(status().isOk());
        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/kpi-observations").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void ログインの前に開いた画面があればログインの後にそこへ戻る() throws Exception {
        String email = user(staffCompany(), Role.SALES, UserStatus.ACTIVE);
        MvcResult first = mvc.perform(get("/staff/kpi-observations")).andReturn();

        MvcResult password = mvc.perform(post("/login")
                        .cookie(sessionOf(first))
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        mvc.perform(post(TOTP_URL)
                        .cookie(sessionOf(password))
                        .param("code", logins().currentCode(email))
                        .with(csrf()))
                // Spring Security は保存した request へ戻すとき、目印の continue を付ける。TOTP の段の後に戻す
                .andExpect(redirectedUrl("http://localhost/staff/kpi-observations?continue"));
    }

    @Test
    void メールアドレスは大文字でもログインできる() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        mvc.perform(post("/login")
                        .param("username", "  " + email.toUpperCase(java.util.Locale.ROOT) + " ")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl(TOTP_URL));
    }

    @Test
    void 長いメールアドレスの利用者もログインできる() throws Exception {
        String local = "long-" + "x".repeat(150);
        String email = user(shipperCompany, Role.SHIPPER, UserStatus.ACTIVE, local + "@example.com");

        assertThat(logins().login(email)).as("2 つの要素がそろった session の Cookie").isNotNull();
    }

    @Test
    void ログインの成功を監査記録に残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        logins().login(email);

        assertThat(auditOf(email))
                .as("password の段と 2 つの要素がそろった段を分けて残す（ADR-012 の見直しの 1。同じ時刻なので順は問わない）")
                .containsExactlyInAnyOrder(
                        Map.of("action", "PASSWORD_VERIFIED", "result", "SUCCESS", "reason", "-"),
                        Map.of("action", "LOGIN_SUCCEEDED", "result", "SUCCESS", "reason", "-"));
    }

    @Test
    void ログインの成功の監査記録に利用者と企業と認証時刻を残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        UUID userId =
                users.findByEmail(EmailAddress.of(email)).orElseThrow().id().value();

        logins().login(email);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT actor_company_id, occurred_at FROM identity.audit_record WHERE actor_user_id = ?"
                        + " AND action = 'LOGIN_SUCCEEDED'",
                userId);
        assertThat(row).containsEntry("actor_company_id", shipperCompany.id().value());
        assertThat(((java.sql.Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(LOGIN_AT);
    }

    // --- ログインの失敗（AC2・AC4） ---

    @Test
    void 誤ったpasswordではログインできず失敗の理由は監査記録にだけ残る() throws Exception {
        String email = shipper(UserStatus.ACTIVE);

        assertLoginRejected(email, "wrong password");

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "BAD_CREDENTIALS"));
    }

    @Test
    void 利用停止の利用者は正しいpasswordでもログインできない() throws Exception {
        String email = shipper(UserStatus.SUSPENDED);

        assertLoginRejected(email, PASSWORD);

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "SUSPENDED"));
    }

    @Test
    void 無効な企業の利用者は正しいpasswordでもログインできない() throws Exception {
        String email = user(company(false), Role.SHIPPER, UserStatus.ACTIVE);

        assertLoginRejected(email, PASSWORD);

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "COMPANY_INACTIVE"));
    }

    @Test
    void 利用停止の利用者でもpasswordが誤っていればpasswordの誤りとして扱う() throws Exception {
        String email = shipper(UserStatus.SUSPENDED);

        assertLoginRejected(email, "wrong password");

        assertThat(auditOf(email))
                .containsExactly(Map.of("action", "LOGIN_FAILED", "result", "FAILURE", "reason", "BAD_CREDENTIALS"));
    }

    @Test
    void 存在しないメールアドレスではログインできず監査記録に操作者もメールアドレスも残さない() throws Exception {
        Integer before = unknownUserFailures();

        assertLoginRejected("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD);

        assertThat(unknownUserFailures()).isEqualTo(before + 1);
    }

    @Test
    void 形式の誤ったメールアドレスでも同じ応答にし存在しないメールアドレスとして記録する() throws Exception {
        Integer before = unknownUserFailures();

        assertLoginRejected("not-an-email", PASSWORD);

        assertThat(unknownUserFailures()).isEqualTo(before + 1);
    }

    // --- ログアウト ---

    @Test
    void ログアウトするとログインへ移りsessionが使えなくなり監査記録に残す() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().login(email);
        advance(Duration.ofMinutes(1));

        mvc.perform(post("/logout").cookie(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().is3xxRedirection());
        assertThat(auditOf(email))
                .extracting(row -> row.get("action"))
                .containsExactlyInAnyOrder("PASSWORD_VERIFIED", "LOGIN_SUCCEEDED", "LOGOUT");
    }

    @Test
    void GETのログアウトではログアウトしない() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        mvc.perform(get("/logout").cookie(session));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    // --- CSRF ---

    @Test
    void CSRFのトークンのないログインは拒否する() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie page = sessionOf(mvc.perform(get("/login")).andReturn());

        mvc.perform(post("/login").cookie(page).param("username", email).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFのトークンが誤った業務のPOSTは拒否する() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        mvc.perform(post("/customer/transport-requests").cookie(session).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionにトークンのない古いフォームの送信は処理せず再認証の案内へ移る() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        mvc.perform(post("/customer/transport-requests").cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/session-expired"));
    }

    // --- 役割の分離 ---

    @Test
    void 荷主担当者は社内の画面を開けない() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        mvc.perform(get("/staff/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 営業担当者と荷主担当者は経路設計の画面を開けず候補の算出と経路の確定を送れない() throws Exception {
        for (Cookie session : List.of(
                logins().login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE)),
                logins().login(shipper(UserStatus.ACTIVE)))) {
            mvc.perform(get("/staff/routing-cases").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get("/staff/routing-cases/RC-2026-0001").cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/staff/routing-cases/RC-2026-0001/candidates")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
            // 経路の確定（S-07。Bolt 19）も経路設計者だけ
            mvc.perform(get("/staff/routing-cases/RC-2026-0001/confirmation?candidate=1")
                            .cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/staff/routing-cases/RC-2026-0001/confirmation")
                            .param("candidate", "1")
                            .param("rationale", "根拠")
                            .param("expectedVersion", "0")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 営業担当者は荷主の画面を開けない() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 荷主担当者は見積りの提示のPOSTを送れない() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        mvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations/1/presentation")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 営業担当者は荷主の回答のPOSTを送れない() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(post("/customer/transport-requests/TR-2026-0001/quotations/1/response")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 営業担当者と経路設計者は見積りと経路の承認の画面を開けずPOSTも送れない() throws Exception {
        for (Role role : List.of(Role.SALES, Role.ROUTE_DESIGNER)) {
            Cookie session = logins().login(user(staffCompany(), role, UserStatus.ACTIVE));

            mvc.perform(get("/customer/transport-requests/TR-2026-0001/quotations/1/approval")
                            .cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/customer/transport-requests/TR-2026-0001/quotations/1/approval")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 荷主担当者と経路設計者は本予約の確定と予約の詳細と予約一覧を開けずPOSTも送れない() throws Exception {
        for (Role role : List.of(Role.SHIPPER, Role.ROUTE_DESIGNER)) {
            Cookie session = logins().login(
                            user(role == Role.SHIPPER ? shipperCompany : staffCompany(), role, UserStatus.ACTIVE));

            mvc.perform(get("/staff/bookings/new?transportRequest=TR-2026-0001&quotation=1")
                            .cookie(session))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/staff/bookings/CTABCDEFGH2345").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get("/staff/bookings").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(post("/staff/bookings")
                            .param("commandId", "00000000-0000-0000-0000-0000000000c1")
                            .param("transportRequest", "TR-2026-0001")
                            .param("quotation", "1")
                            .param("staffConfirmed", "true")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 荷主担当者はナビの追跡の照会を開くとC10になり準備中の画面ではない() throws Exception {
        Cookie session = logins().login(user(shipperCompany, Role.SHIPPER, UserStatus.ACTIVE));

        // 準備中の画面を C-10 追跡の照会に置き換えた（Bolt 27）。ナビの「追跡の照会」は C-10 を指し、現在の項目になる
        String page = mvc.perform(get("/customer/tracking-records").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page)
                .contains("<h1>追跡の照会</h1>")
                .doesNotContain("この画面は準備中です")
                // 属性の順によらずに探す（Bolt 26 のナビの表示のテストと同じ）
                .containsPattern(
                        "<a(?=[^>]*href=\"/customer/tracking-records\")(?=[^>]*aria-current=\"page\")[^>]*>追跡の照会</a>");
        mvc.perform(get("/customer/tracking").cookie(session)).andExpect(status().isNotFound());
    }

    @Test
    void 荷主担当者が他社や存在しない形式の誤った追跡番号を開くとどれも同じ404の案内になる() throws Exception {
        Cookie session = logins().login(user(shipperCompany, Role.SHIPPER, UserStatus.ACTIVE));
        TrackingNumber otherShippers = new TrackingNumber("CTSECVRTYXHR27");
        trackingRecords.save(TrackingRecord.start(
                        otherShippers,
                        UUID.randomUUID(),
                        new CompanyId(UUID.randomUUID()),
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord());

        // フィルターと実際のエラー処理を通して、状態と本文が同じことを確かめる（BR-07。Bolt 27 の開発レビュー）。
        // CSRF のトークンは要求ごとに変わるので除いて比べる。session の警告の戻り先（Bolt 32e）は開いた URL そのものなので除く
        // （利用者が送った URL で、追跡の記録の有無や持ち主を示さない）
        String other = notFoundBody(session, otherShippers.value());
        assertThat(other).contains("<h1>追跡番号が見つかりません</h1>");
        assertThat(notFoundBody(session, "CTZZZZZZZZZZZZ")).isEqualTo(other);
        assertThat(notFoundBody(session, "not-a-tracking-number")).isEqualTo(other);
    }

    private String notFoundBody(Cookie session, String trackingNumber) throws Exception {
        String url = "/customer/tracking-records/" + trackingNumber;
        String body = mvc.perform(get(url).cookie(session))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).as("戻り先は開いた URL そのもの").contains("data-return-path=\"" + url + "\"");
        return body.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\"")
                .replaceAll("data-return-path=\"[^\"]*\"", "data-return-path");
    }

    @Test
    void 荷主担当者はナビの予約を開くとC06になり準備中の画面ではない() throws Exception {
        Cookie session = logins().login(user(shipperCompany, Role.SHIPPER, UserStatus.ACTIVE));

        // 準備中の画面を C-06 予約一覧に置き換えた（Bolt 27b）。ナビの「予約」は C-06 を指し、現在の項目になる
        String page = mvc.perform(get("/customer/bookings").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(page)
                .contains("<h1>予約一覧</h1>")
                .doesNotContain("この画面は準備中です")
                .containsPattern("<a(?=[^>]*href=\"/customer/bookings\")(?=[^>]*aria-current=\"page\")[^>]*>予約</a>");
    }

    /** 役割ごとに分けて、最初の失敗で残りの役割を隠さない（Bolt 27b の開発レビュー）。 */
    @ParameterizedTest
    @EnumSource(
            value = Role.class,
            names = {"SALES", "ROUTE_DESIGNER", "TRACKING_MANAGER"})
    void 社内の役割は荷主の予約一覧を開けない(Role role) throws Exception {
        Cookie session = logins().login(user(staffCompany(), role, UserStatus.ACTIVE));

        mvc.perform(get("/customer/bookings").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void 社内の役割は荷主の追跡の照会を開けない() throws Exception {
        for (Role role : List.of(Role.SALES, Role.ROUTE_DESIGNER, Role.TRACKING_MANAGER)) {
            Cookie session = logins().login(user(staffCompany(), role, UserStatus.ACTIVE));

            mvc.perform(get("/customer/tracking-records").cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get("/customer/tracking-records/CTABCDEFGH2345").cookie(session))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 営業担当者はナビの予約を開くと予約一覧になる() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        // 準備中の画面を S-10 予約一覧の最小の表示に置き換えた（Bolt 25b）
        mvc.perform(get("/staff/bookings").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h1>予約一覧</h1>")))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("この画面は準備中です"))));
    }

    @Test
    void CSRFのトークンが誤った本予約の確定は拒否する() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(post("/staff/bookings")
                        .param("commandId", "00000000-0000-0000-0000-0000000000c1")
                        .param("transportRequest", "TR-2026-0001")
                        .param("quotation", "1")
                        .param("staffConfirmed", "true")
                        .cookie(session)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRFのトークンが誤った見積りの提示は拒否する() throws Exception {
        Cookie session = logins().login(user(staffCompany(), Role.SALES, UserStatus.ACTIVE));

        mvc.perform(post("/staff/transport-requests/TR-2026-0001/quotations/1/presentation")
                        .cookie(session)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    // --- 発行から 8 時間（AC5。T-38 の 3 点） ---

    @Test
    void 発行から8時間の1秒前は使える() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        advance(Duration.ofHours(8).minusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void 発行から8時間ちょうどで再認証の案内へ移り業務データを返さない() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        advance(Duration.ofHours(8));

        mvc.perform(get("/customer/transport-requests").cookie(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 発行から8時間の1秒後も再認証の案内へ移り同じsessionは使えない() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        advance(Duration.ofHours(8).plusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
        ((MutableClock) clock).setInstant(LOGIN_AT);
        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void 発行から8時間を過ぎたsessionではログインの送信も受け付けずログインし直すと上限は新しいログインから数える() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = logins().login(email);
        advance(Duration.ofHours(8));

        // 上限の確認は認証の処理より前にある（T-40）。古い session のままのログインの送信も A-03 へ移す
        mvc.perform(post("/login")
                        .cookie(session)
                        .param("username", email)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(redirectedUrl("/session-expired"));

        Cookie renewed = logins().login(email);
        advance(Duration.ofHours(8 + 7));
        mvc.perform(get("/customer/transport-requests").cookie(renewed)).andExpect(status().isOk());
    }

    // --- 無操作 30 分（AC5。T-38 の 3 点。Spring Session の最終アクセスの時刻を戻して確かめる） ---

    @Test
    void 無操作30分の1秒前は使える() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        sessions().idle(session, Duration.ofMinutes(30).minusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(status().isOk());
    }

    /**
     * 最終アクセスの時刻を 30 分前に書いてから request を送るまでに数ミリ秒かかるため、判定の時点では 30 分を数ミリ秒過ぎている。
     * ちょうど 30 分の扱い（以上か超えか）は Spring Session の実装に任せ、このテストでは確かめない（T-38）。
     */
    @Test
    void 無操作30分で再認証の案内へ移る() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        sessions().idle(session, Duration.ofMinutes(30));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 無操作30分の1秒後も再認証の案内へ移る() throws Exception {
        Cookie session = logins().login(shipper(UserStatus.ACTIVE));

        sessions().idle(session, Duration.ofMinutes(30).plusSeconds(1));

        mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/session-expired"));
    }

    @Test
    void 再認証の案内とログインの画面は未認証で開ける() throws Exception {
        mvc.perform(get("/session-expired")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    // --- password の再設定の依頼（A-05。US-18 AC8、SEC-08。Bolt 33） ---

    private static final String RESET_URL = "/password-reset";
    private static final String RESET_REQUESTED = "/login?reset-requested";
    private static final String RESET_NOTICE = "password の再設定の依頼を受け付けました。";

    @Test
    void 再設定の依頼の画面は未認証で開ける() throws Exception {
        mvc.perform(get(RESET_URL))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("再設定のリンクを送る")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("autocomplete=\"email\"")));
    }

    @Test
    void 再設定の依頼はCSRFのトークンが合わなければ処理しない() throws Exception {
        // 既存の扱いのとおり、トークンのない送信は A-03（session のない古いフォームの送信。Bolt 32e）、誤ったトークンは 403 にする。
        // どちらも依頼を処理しないことを監査で確かめる
        String email = shipper(UserStatus.ACTIVE);

        mvc.perform(post(RESET_URL).param("email", email)).andExpect(redirectedUrl("/session-expired"));
        mvc.perform(post(RESET_URL).param("email", email).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        assertThat(auditOf(email)).noneMatch(row -> "PASSWORD_RESET_REQUESTED".equals(row.get("action")));
        mvc.perform(post(RESET_URL).param("email", email).with(csrf())).andExpect(redirectedUrl(RESET_REQUESTED));
        assertThat(auditOf(email)).anyMatch(row -> "PASSWORD_RESET_REQUESTED".equals(row.get("action")));
    }

    @Test
    void 再設定の依頼は登録の有無と利用の状態にかかわらず同じ応答でログインの画面に戻る() throws Exception {
        String active = shipper(UserStatus.ACTIVE);
        String suspended = shipper(UserStatus.SUSPENDED);
        String unknown = "unknown-" + UUID.randomUUID() + "@example.com";

        for (String email : List.of(active, suspended, unknown)) {
            mvc.perform(post(RESET_URL).param("email", email).with(csrf()))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl(RESET_REQUESTED));
        }
    }

    @Test
    void ログインの画面は依頼の印があるときだけ再設定の依頼の知らせを出す() throws Exception {
        mvc.perform(get(RESET_REQUESTED))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(RESET_NOTICE)));
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password を忘れた場合")))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(RESET_NOTICE))));
    }

    @Test
    void 再設定の依頼の入力の形の誤りは画面に示し入れた値を残す() throws Exception {
        mvc.perform(post(RESET_URL).param("email", "  ").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("メールアドレスを入力してください。")));
        mvc.perform(post(RESET_URL).param("email", "not-an-address").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("メールアドレスの形式が正しくありません。")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"not-an-address\"")));
    }

    @Test
    void ログイン中とpasswordの段のsessionで依頼の画面を開いてもsessionの利用者は変わらない() throws Exception {
        Cookie loggedIn = logins().login(shipper(UserStatus.ACTIVE));
        Cookie passwordStage = logins().loginWithPassword(shipper(UserStatus.ACTIVE));

        mvc.perform(get(RESET_URL).cookie(loggedIn)).andExpect(status().isOk());
        mvc.perform(post(RESET_URL)
                        .param("email", shipper(UserStatus.ACTIVE))
                        .with(csrf())
                        .cookie(loggedIn))
                .andExpect(redirectedUrl(RESET_REQUESTED));
        mvc.perform(get(RESET_URL).cookie(passwordStage)).andExpect(status().isOk());

        mvc.perform(get("/customer/transport-requests").cookie(loggedIn)).andExpect(status().isOk());
        mvc.perform(get(TOTP_URL).cookie(passwordStage)).andExpect(status().isOk());
    }

    // --- password の再設定（A-06。US-18 AC8 の後半、SEC-04・SEC-05・SEC-08。Bolt 33a） ---

    private static final String NEW_URL = "/password-reset/new";
    private static final String COMPLETED_URL = "/login?reset-completed";
    private static final String NEW_PASSWORD = "a brand new long passphrase";
    private static final String INVALID_TITLE = "この再設定のリンクは使えません";

    @Autowired
    PasswordResetTokenRepository resetTokens;

    /** 再設定のトークンを発行して保存し、リンクに入る値（平文）を返す。 */
    private String issueResetToken(String email) {
        String token = "tok" + UUID.randomUUID().toString().replace("-", "");
        resetTokens.add(PasswordResetToken.issue(
                PasswordResetTokenPurpose.RESET,
                UUID.randomUUID(),
                userIdOf(email),
                new Sha256PasswordResetTokenHasher().hash(token),
                new UtcInstant(clock.instant())));
        return token;
    }

    /** リンクを開いて A-06 へ移った session（の Cookie）。 */
    private Cookie openResetLink(String token, Cookie session) throws Exception {
        MockHttpServletRequestBuilder request = get(RESET_URL + "/" + token);
        if (session != null) {
            request = request.cookie(session);
        }
        MvcResult result = mvc.perform(request)
                .andExpect(redirectedUrl(NEW_URL))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        Cookie created = result.getResponse().getCookie(SESSION_COOKIE);
        return created != null ? created : session;
    }

    private MockHttpServletRequestBuilder setPassword(Cookie session, String password, String confirm) {
        return post(NEW_URL)
                .cookie(session)
                .param("newPassword", password)
                .param("confirmPassword", confirm)
                .with(csrf());
    }

    private static String sessionIdOf(Cookie cookie) {
        return new String(java.util.Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    @Test
    void リンクを開くとトークンのないA06へ移りURLとsessionにトークンが残らない() throws Exception {
        String token = issueResetToken(shipper(UserStatus.ACTIVE));

        Cookie session = openResetLink(token, null);

        assertThat(session).as("トークンの ID を置く session を作った").isNotNull();
        Integer attributes = jdbc.queryForObject(
                "SELECT count(*) FROM platform.spring_session_attributes a JOIN platform.spring_session s"
                        + " ON s.primary_id = a.session_primary_id WHERE s.session_id = ?",
                Integer.class,
                sessionIdOf(session));
        assertThat(attributes).as("session に属性がある（空振りしない）").isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM platform.spring_session_attributes a JOIN platform.spring_session s"
                                + " ON s.primary_id = a.session_primary_id WHERE s.session_id = ?"
                                + " AND encode(a.attribute_bytes, 'escape') LIKE ?",
                        Integer.class,
                        sessionIdOf(session),
                        "%UUID%"))
                .as("同じ照合でトークンの ID の属性を見つけられる（陽性の対照）")
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM platform.spring_session_attributes"
                                + " WHERE encode(attribute_bytes, 'escape') LIKE ?",
                        Integer.class,
                        "%" + token + "%"))
                .as("session の表にトークンの平文がない")
                .isZero();
        mvc.perform(get(NEW_URL).cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password を設定する")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("autocomplete=\"new-password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(token))));
    }

    @Test
    void 使えないリンクとsessionにトークンのないA06はどれも同じ案内になる() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        String token = issueResetToken(email);
        Cookie session = openResetLink(token, null);
        mvc.perform(setPassword(session, NEW_PASSWORD, NEW_PASSWORD)).andExpect(redirectedUrl(COMPLETED_URL));

        mvc.perform(get(RESET_URL + "/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(INVALID_TITLE)));
        mvc.perform(get(RESET_URL + "/unknown-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(INVALID_TITLE)));
        mvc.perform(get(NEW_URL)).andExpect(content().string(org.hamcrest.Matchers.containsString(INVALID_TITLE)));
    }

    @Test
    void 設定すると完了の知らせに移りほかの端末はA03で古いpasswordは使えず新しいpasswordとTOTPでログインできる() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie otherDevice = logins().login(email);
        mvc.perform(get("/customer/transport-requests").cookie(otherDevice)).andExpect(status().isOk());
        Cookie session = openResetLink(issueResetToken(email), null);

        mvc.perform(setPassword(session, NEW_PASSWORD, NEW_PASSWORD)).andExpect(redirectedUrl(COMPLETED_URL));

        mvc.perform(get(COMPLETED_URL))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password を変更しました。")))
                // ロックの数値は LockPolicy から描く（Bolt 33c。表示は変えない）
                .andExpect(content().string(org.hamcrest.Matchers.containsString("15 分たってから、もう一度ログインしてください。")));
        mvc.perform(get("/customer/transport-requests").cookie(otherDevice))
                .andExpect(redirectedUrl("/session-expired"));
        assertLoginRejected(email, PASSWORD);
        Cookie passwordStage = loginWithPassword(email, NEW_PASSWORD);
        resetTotpStep(email);
        Cookie loggedIn = submitTotp(passwordStage, logins().currentCode(email));
        mvc.perform(get("/customer/transport-requests").cookie(loggedIn)).andExpect(status().isOk());
    }

    @Test
    void 確認の不一致と方針の誤りは欄の下に示しトークンを使わない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = openResetLink(issueResetToken(email), null);

        mvc.perform(setPassword(session, NEW_PASSWORD, NEW_PASSWORD + "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("確認の欄に、新しい password と同じものを入力してください。")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>エラー: password の再設定")));
        mvc.perform(setPassword(session, "password1234", "password1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("この password はよく使われているため、使えません。")))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("value=\"password1234\""))));
        mvc.perform(setPassword(session, "short", "short"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("12 文字以上の password を入力してください。")));
        mvc.perform(setPassword(session, "a".repeat(65), "a".repeat(65)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("64 文字までの password を入力してください。")));
        mvc.perform(setPassword(session, "あ".repeat(25), "あ".repeat(25)))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.containsString(
                                "全角の文字を含めると長すぎます。全角の文字（かな・漢字など）は 1 文字を 3 文字分として数え、合わせて 72 文字分までの"
                                        + " password を入力してください。")));

        mvc.perform(setPassword(session, NEW_PASSWORD, NEW_PASSWORD)).andExpect(redirectedUrl(COMPLETED_URL));
    }

    @Test
    void ログイン中や別の利用者のブラウザでリンクを開いて設定しても完了の知らせが出て今の認証は捨てる() throws Exception {
        String owner = shipper(UserStatus.ACTIVE);
        Cookie ownersBrowser = logins().login(owner);
        Cookie session = openResetLink(issueResetToken(owner), ownersBrowser);

        MvcResult done = mvc.perform(setPassword(session, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(redirectedUrl(COMPLETED_URL))
                .andReturn();
        Cookie renewed = sessionOf(done);
        mvc.perform(get(COMPLETED_URL).cookie(renewed))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password を変更しました。")));
        mvc.perform(get("/customer/transport-requests").cookie(ownersBrowser))
                .andExpect(redirectedUrl("/session-expired"));

        String someoneElse = shipper(UserStatus.ACTIVE);
        String target = shipper(UserStatus.ACTIVE);
        Cookie othersBrowser = logins().login(someoneElse);
        Cookie othersSession = openResetLink(issueResetToken(target), othersBrowser);
        MvcResult other = mvc.perform(setPassword(othersSession, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(redirectedUrl(COMPLETED_URL))
                .andReturn();
        mvc.perform(get("/customer/transport-requests").cookie(sessionOf(other)))
                .andExpect(redirectedUrl("/login"));
    }

    /** session の表に、トークンの平文を含む属性がない。 */
    private void assertNoTokenInSessions(String token) {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM platform.spring_session_attributes"
                                + " WHERE encode(attribute_bytes, 'escape') LIKE ?",
                        Integer.class,
                        "%" + token + "%"))
                .as("session の表にトークンの平文がない")
                .isZero();
    }

    @Test
    void passwordの段や失効したCookieのブラウザでリンクを開いて設定してもトークンは残らず完了の知らせが出る() throws Exception {
        String midLogin = shipper(UserStatus.ACTIVE);
        Cookie passwordStage = logins().loginWithPassword(midLogin);
        String token = issueResetToken(midLogin);
        Cookie session = openResetLink(token, passwordStage);
        assertThat(session.getValue())
                .as("トークンの ID を置くときに session の ID を変える（session 固定化を防ぐ）")
                .isNotEqualTo(passwordStage.getValue());
        assertNoTokenInSessions(token);
        mvc.perform(setPassword(session, NEW_PASSWORD, NEW_PASSWORD)).andExpect(redirectedUrl(COMPLETED_URL));

        String staleBrowser = shipper(UserStatus.ACTIVE);
        Cookie stale = new Cookie(
                SESSION_COOKIE,
                java.util.Base64.getEncoder()
                        .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
        String staleToken = issueResetToken(staleBrowser);
        // 失効した Cookie では A-03 に移り、トークンは使わず session にも置かない。リンクを開き直せば使える（マニュアル 2.6）
        MvcResult expired = mvc.perform(get(RESET_URL + "/" + staleToken).cookie(stale))
                .andExpect(redirectedUrl("/session-expired"))
                .andReturn();
        assertNoTokenInSessions(staleToken);
        Cookie renewed = openResetLink(staleToken, expired.getResponse().getCookie(SESSION_COOKIE));
        assertThat(renewed.getValue()).as("失効した Cookie を使い続けていない").isNotEqualTo(stale.getValue());
        assertNoTokenInSessions(staleToken);
        mvc.perform(setPassword(renewed, NEW_PASSWORD, NEW_PASSWORD)).andExpect(redirectedUrl(COMPLETED_URL));
    }

    @Test
    void 未認証のsessionでリンクを開いてもsessionのIDを変える() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie first = openResetLink(issueResetToken(email), null);

        Cookie second = openResetLink(issueResetToken(email), first);

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void トークンの後ろに段を足したURLは公開しない() throws Exception {
        String token = issueResetToken(shipper(UserStatus.ACTIVE));

        mvc.perform(get(RESET_URL + "/" + token + "/x")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void 古いpasswordで認証コードの画面にいたほかの端末は再設定の後にログインを終えられない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie otherDevice = logins().loginWithPassword(email);
        Cookie session = openResetLink(issueResetToken(email), null);
        mvc.perform(setPassword(session, NEW_PASSWORD, NEW_PASSWORD)).andExpect(redirectedUrl(COMPLETED_URL));

        resetTotpStep(email);
        // password の段の session も失効していて、認証コードを送ってもログインの画面に戻る
        mvc.perform(post(TOTP_URL)
                        .cookie(otherDevice)
                        .param("code", logins().currentCode(email))
                        .with(csrf()))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 設定の送信はCSRFのトークンがなければ処理しない() throws Exception {
        String email = shipper(UserStatus.ACTIVE);
        Cookie session = openResetLink(issueResetToken(email), null);

        mvc.perform(post(NEW_URL)
                        .cookie(session)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        assertThat(auditOf(email)).noneMatch(row -> "PASSWORD_RESET_COMPLETED".equals(row.get("action")));
        assertLoginRejected(email, NEW_PASSWORD);
    }

    // --- 応答のヘッダーと Cookie（SEC-17） ---

    @Test
    void 応答はクリックジャッキングと型の推測とスクリプトの読み込みを防ぐ() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        "default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'; object-src 'none'"));
    }

    // --- 準備 ---

    private Company company(boolean active) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, active);
        companies.add(company);
        return company;
    }

    private Company staffCompany() {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(company);
        return company;
    }

    private String shipper(UserStatus status) {
        return user(shipperCompany, Role.SHIPPER, status);
    }

    private String user(Company company, Role role, UserStatus status) {
        return user(company, role, status, "user-" + UUID.randomUUID() + "@example.com");
    }

    private String user(Company company, Role role, UserStatus status, String email) {
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                company.id(),
                EmailAddress.of(email),
                "利用者",
                passwordEncoder.encode(PASSWORD),
                status,
                Set.of(role)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return email;
    }

    /** TOTP を登録していない利用者（31b までは登録の案内へ送る）。 */
    private String userWithoutTotp(Company company, Role role) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        users.add(User.of(
                new UserId(UUID.randomUUID()),
                company.id(),
                EmailAddress.of(email),
                "利用者",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(role)));
        return email;
    }

    /** 指定の password で password の段だけを通す（A-02 へ移る。Bolt 33a の再設定の後）。 */
    private Cookie loginWithPassword(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(TOTP_URL))
                .andReturn();
        return sessionOf(result);
    }

    private Cookie submitTotp(Cookie passwordStage, String code) throws Exception {
        MvcResult result = mvc.perform(
                        post(TOTP_URL).cookie(passwordStage).param("code", code).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl())
                .doesNotContain("error")
                .doesNotContain("expired");
        return sessionOf(result);
    }

    /** テストの準備として、使った刻みを戻す（同じ利用者が同じ刻みの間に入り直すため。Bolt 31 の確認ポイント 7）。 */
    private void resetTotpStep(String email) {
        AuthenticationFactorFixtures.resetTotpStep(jdbc, userIdOf(email));
    }

    /** 足りない TOTP の要素を求めて A-02 へ導く（枠組みが factor.type・factor.reason を付ける）。 */
    /** いまの時計の前後 1 つの刻みのどれとも一致しないコード。 */
    private String wrongCode(String email) {
        TotpCredential credential =
                totpCredentials.findByUserId(userIdOf(email)).orElseThrow();
        long step = TotpCredential.stepAt(new UtcInstant(clock.instant()));
        List<String> window = List.of(
                totpCodes.code(credential.userId(), credential.secret(), step - 1),
                totpCodes.code(credential.userId(), credential.secret(), step),
                totpCodes.code(credential.userId(), credential.secret(), step + 1));
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> String.format("%06d", i))
                .filter(code -> !window.contains(code))
                .findFirst()
                .orElseThrow();
    }

    /** パスの変数を仮の値で埋める（正規表現の候補は最初の値）。 */
    private static String withSampleVariables(String pattern) {
        return pattern.replaceAll("\\{[^}:]+:([^}|]+)[^}]*}", "$1").replaceAll("\\{[^}]+}", "1");
    }

    /** URL の接頭辞の規則（SecurityConfiguration）に合う役割。 */
    private static Role roleFor(String url) {
        if (url.startsWith("/staff/routing-cases")) {
            return Role.ROUTE_DESIGNER;
        }
        if (url.startsWith("/staff/tracking-records")) {
            return Role.TRACKING_MANAGER;
        }
        return url.startsWith("/staff") ? Role.SALES : Role.SHIPPER;
    }

    private static ResultMatcher redirectsToTotp() {
        return result -> assertThat(result.getResponse().getRedirectedUrl())
                .as("A-02 へ導く")
                .startsWith(TOTP_URL + "?factor.type=totp");
    }

    private UserId userIdOf(String email) {
        return users.findByEmail(EmailAddress.of(email.strip())).orElseThrow().id();
    }

    private void assertLoginRejected(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .param("username", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andReturn();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        if (session != null) {
            mvc.perform(get("/customer/transport-requests").cookie(session)).andExpect(redirectedUrl("/login"));
        }
    }

    private void advance(Duration duration) {
        ((MutableClock) clock).setInstant(LOGIN_AT.plus(duration));
    }

    private List<Map<String, String>> auditOf(String email) {
        UUID userId =
                users.findByEmail(EmailAddress.of(email)).orElseThrow().id().value();
        return jdbc.query(
                "SELECT action, result, reason FROM identity.audit_record WHERE actor_user_id = ?"
                        + " ORDER BY occurred_at",
                (rs, n) -> Map.of(
                        "action", rs.getString("action"),
                        "result", rs.getString("result"),
                        "reason", rs.getString("reason") == null ? "-" : rs.getString("reason")),
                userId);
    }

    private Integer unknownUserFailures() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'LOGIN_FAILED'"
                        + " AND reason = 'UNKNOWN_USER' AND actor_user_id IS NULL AND actor_company_id IS NULL",
                Integer.class);
    }

    private SpringSessionRows sessions() {
        return new SpringSessionRows(jdbc);
    }

    private IdentityLogins logins() {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock);
    }
}

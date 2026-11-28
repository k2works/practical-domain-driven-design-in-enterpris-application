package com.example.cargotracker.identity.infrastructure.security;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.valueobjects.AdministrationScope;
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
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S-20 利用者の管理のセキュリティの統合テスト（Bolt 34。US-16 の一部）。本物のフィルターの連なりと PostgreSQL の上で、
 * `/staff/users/**` はシステム管理者だけが開けること、システム管理者のホームと社内のナビ、役割の変更の確定の POST を画面を経ずに
 * 送っても同じ判定で拒むこと、変えた役割が相手の次の request から効くことを確かめる。
 */
class UserAdministrationSecurityIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final Instant LOGIN_AT = Instant.parse("2026-10-16T00:00:00Z");
    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String USERS_URL = "/staff/users";

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

    // --- 認可とホーム ---

    @Test
    void システム管理者はホームから利用者の一覧へ移り一覧と詳細を開ける() throws Exception {
        Cookie session = logins().login(admin());
        String target = user(operator, Role.SALES);

        mvc.perform(get("/").cookie(session)).andExpect(redirectedUrl(USERS_URL));
        String list = page(session, USERS_URL);
        assertThat(list).contains(target).contains("営業担当者");
        String detail =
                page(session, USERS_URL + "/" + logins().userIdOf(target).value());
        assertThat(detail).contains(target).contains("A 社");
    }

    @Test
    void システム管理者はほかの社内と荷主の画面を開けない() throws Exception {
        Cookie session = logins().login(admin());

        for (String url : new String[] {
            "/staff/transport-requests",
            "/staff/bookings",
            "/staff/kpi-observations",
            "/staff/routing-cases",
            "/staff/tracking-records",
            "/customer/transport-requests"
        }) {
            mvc.perform(get(url).cookie(session)).andExpect(status().isForbidden());
        }
    }

    @Test
    void システム管理者のほかの役割は利用者の管理を開けない() throws Exception {
        String target = user(operator, Role.SALES);
        String detailUrl = USERS_URL + "/" + logins().userIdOf(target).value();
        for (Role role : EnumSet.complementOf(EnumSet.of(Role.SYSTEM_ADMIN))) {
            Cookie session = logins().login(user(role == Role.SHIPPER ? shipper : operator, role));

            mvc.perform(get(USERS_URL).cookie(session)).andExpect(status().isForbidden());
            mvc.perform(get(detailUrl).cookie(session)).andExpect(status().isForbidden());
            mvc.perform(post(detailUrl + "/role-changes/confirmation")
                            .cookie(session)
                            .param("roles", Role.ROUTE_DESIGNER.name())
                            .param("version", "0")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(rolesOf(target)).containsExactly("SALES");
    }

    @Test
    void いない利用者の詳細は404() throws Exception {
        Cookie session = logins().login(admin());

        mvc.perform(get(USERS_URL + "/" + UUID.randomUUID()).cookie(session)).andExpect(status().isNotFound());
    }

    @Test
    void 社内のナビの利用者はシステム管理者にだけ出す() throws Exception {
        String link = "href=\"" + USERS_URL + "\"[^>]*>利用者</a>";
        Cookie admin = logins().login(admin());
        Cookie sales = logins().login(user(operator, Role.SALES));

        assertThat(page(admin, USERS_URL)).containsPattern(link);
        assertThat(page(sales, "/staff/transport-requests")).doesNotContainPattern(link);
    }

    // --- 役割の変更の確定（H1・H3） ---

    @Test
    void 確定のPOSTを画面を経ずに送っても同じ判定で拒み何も書かない() throws Exception {
        Cookie session = logins().login(admin());
        String target = user(operator, Role.SALES);
        String url = USERS_URL + "/" + logins().userIdOf(target).value() + "/role-changes/confirmation";

        String page = mvc.perform(post(url)
                        .cookie(session)
                        .param("roles", Role.SALES.name(), Role.SYSTEM_ADMIN.name())
                        .param("version", "0")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 監督の役割は画面で付け外ししない（Bolt 34 の開発レビューの決定）
        assertThat(page).contains("システム管理者・監査担当者の役割は、この画面では付けたり外したりできません。");
        assertThat(rolesOf(target)).containsExactly("SALES");
        assertThat(rolesChangedOf(target)).isZero();
    }

    @Test
    void 確定のPOSTにもCSRFのトークンが要る() throws Exception {
        // 業務の画面の CSRF の拒否は 403 ではなく案内へ移す（Bolt 32f）。役割も監査も書かないことを確かめる
        Cookie session = logins().login(admin());
        String target = user(operator, Role.SALES);
        String detailUrl = USERS_URL + "/" + logins().userIdOf(target).value();

        MvcResult rejected = mvc.perform(post(detailUrl + "/role-changes/confirmation")
                        .cookie(session)
                        .param("roles", Role.ROUTE_DESIGNER.name())
                        .param("version", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(rejected.getResponse().getRedirectedUrl()).isNotEqualTo(detailUrl);
        assertThat(rolesOf(target)).containsExactly("SALES");
        assertThat(rolesChangedOf(target)).isZero();
    }

    @Test
    void 変えた役割は相手の次のrequestから効く() throws Exception {
        Cookie admin = logins().login(admin());
        String target = user(operator, Role.SALES);
        Cookie targetSession = logins().login(target);
        mvc.perform(get("/staff/transport-requests").cookie(targetSession)).andExpect(status().isOk());

        mvc.perform(post(USERS_URL + "/" + logins().userIdOf(target).value() + "/role-changes/confirmation")
                        .cookie(admin)
                        .param("roles", Role.ROUTE_DESIGNER.name())
                        .param("version", "0")
                        .with(csrf()))
                .andExpect(redirectedUrl(
                        USERS_URL + "/" + logins().userIdOf(target).value()));

        mvc.perform(get("/staff/transport-requests").cookie(targetSession)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/routing-cases").cookie(targetSession)).andExpect(status().isOk());
        assertThat(rolesChangedOf(target)).isEqualTo(1);
    }

    @Test
    void 監査担当者に業務の役割を足すPOSTは職務分離で拒む() throws Exception {
        Cookie session = logins().login(admin());
        String target = user(operator, Role.AUDITOR);

        String page = postChange(session, target, "0", Role.AUDITOR.name(), Role.SALES.name())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("業務データを変更できる役割（営業担当者）は併せて付けられません。");
        assertThat(rolesOf(target)).containsExactly("AUDITOR");
    }

    @Test
    void 自分の詳細には役割のフォームを出さずPOSTでも自分の役割は変えられない() throws Exception {
        String admin = admin();
        Cookie session = logins().login(admin);

        String detail = page(session, USERS_URL + "/" + logins().userIdOf(admin).value());
        assertThat(detail).contains("自分の役割は変更できません。").doesNotContain("変更内容を確認する");
        String page = postChange(session, admin, "0", Role.SYSTEM_ADMIN.name(), Role.SALES.name())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("自分の役割は変更できません。ほかのシステム管理者に依頼してください。");
        assertThat(rolesOf(admin)).containsExactly("SYSTEM_ADMIN");
    }

    @Test
    void 役割を選ばない送信は役割なしで拒む() throws Exception {
        Cookie session = logins().login(admin());
        String target = user(operator, Role.SALES);

        String page = postChange(session, target, "0")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page).contains("役割を 1 つ以上選んでください。");
        assertThat(rolesOf(target)).containsExactly("SALES");
    }

    @Test
    void 知らない役割の名前と数でない版と版のない送信は400() throws Exception {
        Cookie session = logins().login(admin());
        String target = user(operator, Role.SALES);
        String url = USERS_URL + "/" + logins().userIdOf(target).value() + "/role-changes/confirmation";

        postChange(session, target, "0", "ROOT").andExpect(status().isBadRequest());
        postChange(session, target, "x", Role.ROUTE_DESIGNER.name()).andExpect(status().isBadRequest());
        mvc.perform(post(url)
                        .cookie(session)
                        .param("roles", Role.ROUTE_DESIGNER.name())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url).cookie(session).param("roles", "ROOT").param("version", "0"))
                .andExpect(status().isBadRequest());
        assertThat(rolesOf(target)).containsExactly("SALES");
    }

    @Test
    void 形の誤った利用者IDは詳細も確認も確定も404() throws Exception {
        Cookie session = logins().login(admin());
        String url = USERS_URL + "/not-a-uuid";

        mvc.perform(get(url).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(get(url + "/role-changes/confirmation").cookie(session).param("version", "0"))
                .andExpect(status().isNotFound());
        mvc.perform(post(url + "/role-changes/confirmation")
                        .cookie(session)
                        .param("roles", Role.SALES.name())
                        .param("version", "0")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 荷受人の企業の利用者の詳細には役割のフォームを出さない() throws Exception {
        Company consignee = Company.of(new CompanyId(UUID.randomUUID()), "荷受人 A", CompanyKind.CONSIGNEE, true);
        companies.add(consignee);
        String admin = admin();
        AdministrationFixtures.assignCompany(jdbc, logins().userIdOf(admin), consignee.id());
        Cookie session = logins().login(admin);
        String target = user(consignee, Role.SHIPPER);

        String detail =
                page(session, USERS_URL + "/" + logins().userIdOf(target).value());

        assertThat(detail).contains("荷受人の企業の利用者には役割を付けません。").doesNotContain("変更内容を確認する");
    }

    // --- 管理範囲（Bolt 34b。US-16 AC2、IA-INV-14） ---

    @Test
    void 範囲外の利用者は一覧に出ず詳細も確認も確定も404で確定の試行だけが1行残り役割も版も変わらない() throws Exception {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        String admin = admin();
        Cookie session = logins().login(admin);
        String inside = user(operator, Role.SALES);
        String stranger = user(outside, Role.SALES);
        String detailUrl = USERS_URL + "/" + logins().userIdOf(stranger).value();

        assertThat(page(session, USERS_URL)).contains(inside).doesNotContain(stranger);
        mvc.perform(get(detailUrl).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(get(detailUrl + "/role-changes/confirmation")
                        .cookie(session)
                        .param("roles", Role.ROUTE_DESIGNER.name())
                        .param("version", "0"))
                .andExpect(status().isNotFound());
        assertThat(scopeDenialsOf(stranger))
                .as("GET は CSRF の守りがないので記録しない（Bolt 34b の開発レビューの決定）")
                .isZero();
        postChange(session, stranger, "0", Role.ROUTE_DESIGNER.name()).andExpect(status().isNotFound());
        assertThat(scopeDenialsOf(stranger)).isEqualTo(1);

        assertThat(rolesOf(stranger)).containsExactly("SALES");
        assertThat(jdbc.queryForObject(
                        "SELECT version FROM identity.app_user WHERE id = ?",
                        Long.class,
                        logins().userIdOf(stranger).value()))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT actor_user_id FROM identity.audit_record WHERE action = 'ADMIN_SCOPE_DENIED'"
                                + " AND target_id = ?",
                        UUID.class,
                        logins().userIdOf(stranger).value().toString()))
                .isEqualTo(logins().userIdOf(admin).value());
    }

    @Test
    void 詳細を読んだ後に管理範囲から外れた確定も404で試行を1行だけ書く() throws Exception {
        String admin = admin();
        Cookie session = logins().login(admin);
        String target = user(operator, Role.SALES);
        // 1 回目（controller の詳細の読み出し）は範囲内、2 回目（役割の変更の判定）は範囲外
        doReturn(new AdministrationScope(Set.of(operator.id())))
                .doReturn(new AdministrationScope(Set.of()))
                .when(assignments)
                .scopeOf(logins().userIdOf(admin));

        postChange(session, target, "0", Role.ROUTE_DESIGNER.name()).andExpect(status().isNotFound());

        assertThat(scopeDenialsOf(target)).isEqualTo(1);
        assertThat(rolesOf(target)).containsExactly("SALES");
    }

    @Test
    void 自分の企業を管理範囲に持つシステム管理者はほかのシステム管理者の詳細を開ける() throws Exception {
        Cookie session = logins().login(admin());
        String other = admin();

        assertThat(page(session, USERS_URL + "/" + logins().userIdOf(other).value()))
                .contains(other);
    }

    @Test
    void 範囲外とない利用者は同じ404で区別できない() throws Exception {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        Cookie session = logins().login(admin());
        String stranger = user(outside, Role.SALES);

        String outOfScope = mvc.perform(
                        get(USERS_URL + "/" + logins().userIdOf(stranger).value())
                                .cookie(session))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missing = mvc.perform(get(USERS_URL + "/" + UUID.randomUUID()).cookie(session))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // MockMvc はエラーの画面を描かない（本文は空）。共通の 404 の画面の文言は画面の層の受入シナリオで確かめる
        assertThat(outOfScope).isEqualTo(missing).doesNotContain(stranger);
    }

    @Test
    void 管理範囲のないシステム管理者には一覧が空で自分の詳細も404() throws Exception {
        String admin = admin();
        jdbc.update(
                "DELETE FROM identity.user_assignment WHERE user_id = ?",
                logins().userIdOf(admin).value());
        Cookie session = logins().login(admin);

        assertThat(page(session, USERS_URL)).contains("管理する企業の利用者はいません。").doesNotContain(admin);
        mvc.perform(get(USERS_URL + "/" + logins().userIdOf(admin).value()).cookie(session))
                .andExpect(status().isNotFound());
        assertThat(scopeDenialsOf(admin)).as("GET は記録しない").isZero();
    }

    @Test
    void 範囲外の試行を書けなければ404で隠さず失敗にする() throws Exception {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の会社", CompanyKind.OPERATOR, true);
        companies.add(outside);
        Cookie session = logins().login(admin());
        String stranger = user(outside, Role.SALES);
        doThrow(new IllegalStateException("監査の表に届かない"))
                .when(auditRecords)
                .append(argThat(written -> written.action() == AuditAction.ADMIN_SCOPE_DENIED));
        String url = USERS_URL + "/" + logins().userIdOf(stranger).value() + "/role-changes/confirmation";

        var change = post(url)
                .cookie(session)
                .param("roles", Role.ROUTE_DESIGNER.name())
                .param("version", "0")
                .with(csrf());

        assertThatThrownBy(() -> mvc.perform(change))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("監査の表に届かない");
    }

    // --- helpers ---

    int scopeDenialsOf(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ADMIN_SCOPE_DENIED' AND target_id = ?",
                Integer.class,
                logins().userIdOf(email).value().toString());
    }

    private org.springframework.test.web.servlet.ResultActions postChange(
            Cookie session, String target, String version, String... roles) throws Exception {
        return mvc.perform(post(USERS_URL + "/" + logins().userIdOf(target).value() + "/role-changes/confirmation")
                .cookie(session)
                .param("roles", roles)
                .param("version", version)
                .with(csrf()));
    }

    private String page(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** 管理範囲を明示して入れたシステム管理者（#50 (a)。テストの A 社と荷主 A。ほかの企業はテストごとに割り当てる）。 */
    private String admin() {
        String email = user(operator, Role.SYSTEM_ADMIN);
        AdministrationFixtures.assignCompany(jdbc, logins().userIdOf(email), operator.id());
        AdministrationFixtures.assignCompany(jdbc, logins().userIdOf(email), shipper.id());
        return email;
    }

    @Test
    void システム管理者を作っただけでは管理範囲は入らず明示の補助で入る() {
        // Bolt 38e の行 12（#50 (a)。作る順序に依存せず、準備が見える）
        String plain = user(operator, Role.SYSTEM_ADMIN);
        String explicit = admin();

        assertThat(scopeRowsOf(plain)).isZero();
        assertThat(scopeRowsOf(explicit)).isEqualTo(2);
    }

    private int scopeRowsOf(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.user_assignment WHERE user_id = ?",
                Integer.class,
                logins().userIdOf(email).value());
    }

    private String user(Company company, Role role) {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
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
        // 管理範囲は作っただけでは入れない。明示の補助 admin() で入れる（Bolt 38e。#50 (a)）
        return email;
    }

    int rolesChangedOf(String email) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ROLES_CHANGED' AND target_id = ?",
                Integer.class,
                logins().userIdOf(email).value().toString());
    }

    java.util.List<String> rolesOf(String email) {
        return jdbc.queryForList(
                "SELECT role FROM identity.user_role WHERE user_id = ? ORDER BY role",
                String.class,
                logins().userIdOf(email).value());
    }

    private IdentityLogins logins() {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock);
    }
}

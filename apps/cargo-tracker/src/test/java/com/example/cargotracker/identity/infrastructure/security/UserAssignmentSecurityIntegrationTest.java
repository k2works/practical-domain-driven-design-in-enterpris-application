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
import com.example.cargotracker.identity.application.internal.session.SessionPaths;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * S-20 の担当範囲の変更のセキュリティの統合テスト（Bolt 38e。US-16 AC1、IA-INV-02・IA-INV-14）。本物のフィルターの連なりと PostgreSQL の
 * 上で、確定の POST を画面を経ずに送っても同じ判定で拒むこと（選べない企業・担当範囲のない役割）、範囲外の試行の記録（値の誤りによらない。
 * T-211）、確認の GET が何も書かないこと、ほかの役割の 403 と CSRF を確かめる。管理範囲は明示の補助で入れる（#50 (a)。管理者を作っただけでは
 * 入らない）。文言は画面の層の受入シナリオで確かめる（T-145）。
 */
class UserAssignmentSecurityIntegrationTest extends IdentitySpyIntegrationTestBase {

    private static final Instant LOGIN_AT = Instant.parse("2026-11-09T00:00:00Z");
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
    private Company shipperA;
    private Company shipperB;
    private Company consignee;

    private Company newCompany(String name, CompanyKind kind) {
        Company company = Company.of(new CompanyId(UUID.randomUUID()), name, kind, true);
        companies.add(company);
        return company;
    }

    @BeforeEach
    void 時計を戻し企業を登録する() {
        ((MutableClock) clock).setInstant(LOGIN_AT);
        operator = newCompany("A 社", CompanyKind.OPERATOR);
        shipperA = newCompany("荷主 A", CompanyKind.SHIPPER);
        shipperB = newCompany("荷主 B", CompanyKind.SHIPPER);
        consignee = newCompany("荷受人 C", CompanyKind.CONSIGNEE);
    }

    /** 管理範囲を明示して入れたシステム管理者（#50 (a)）。 */
    private String admin(Company... scope) {
        String email = user(operator, Role.SYSTEM_ADMIN);
        for (Company company : scope) {
            AdministrationFixtures.assignCompany(jdbc, logins().userIdOf(email), company.id());
        }
        return email;
    }

    private String fullAdmin() {
        return admin(operator, shipperA, shipperB, consignee);
    }

    @Test
    void 確認のGETは何も書かず確定で担当企業と版と監査を書き詳細へ戻す() throws Exception {
        // 行 3（AC-2）
        Cookie session = logins().login(fullAdmin());
        String target = user(operator, Role.SALES);

        mvc.perform(get(confirmationUrl(target))
                        .cookie(session)
                        .param("companies", id(shipperA))
                        .param("version", "0"))
                .andExpect(status().isOk());
        assertThat(companiesOf(target)).as("確認の GET は書かない").isEmpty();
        assertThat(audits(target, "ASSIGNMENTS_CHANGED")).isZero();

        postChange(session, target, "0", id(shipperA), id(shipperB)).andExpect(redirectedUrl(detailUrl(target)));

        assertThat(companiesOf(target))
                .containsExactlyInAnyOrder(shipperA.id().value(), shipperB.id().value());
        assertThat(versionOf(target)).isEqualTo(1L);
        assertThat(audits(target, "ASSIGNMENTS_CHANGED")).isEqualTo(1);
    }

    @Test
    void 選べない企業の確定は何も書かない() throws Exception {
        // 行 5（AC-3、V1）。運用会社・荷受人・範囲外の荷主を、画面を経ずに送る
        Company outside = newCompany("範囲外の荷主", CompanyKind.SHIPPER);
        Cookie session = logins().login(fullAdmin());
        String target = user(operator, Role.SALES);

        for (Company unselectable : List.of(operator, consignee, outside)) {
            postChange(session, target, "0", id(unselectable)).andExpect(status().isOk());
        }

        assertThat(companiesOf(target)).isEmpty();
        assertThat(versionOf(target)).isZero();
        assertThat(audits(target, "ASSIGNMENTS_CHANGED")).isZero();
    }

    @Test
    void 担当範囲のない役割の利用者への確定は何も書かない() throws Exception {
        // 行 8（V14）。画面からは来ない
        Cookie session = logins().login(fullAdmin());
        String auditor = user(operator, Role.AUDITOR);

        postChange(session, auditor, "0", id(shipperA)).andExpect(status().isOk());

        assertThat(companiesOf(auditor)).isEmpty();
        assertThat(audits(auditor, "ASSIGNMENTS_CHANGED")).isZero();
    }

    @ParameterizedTest(name = "範囲外の利用者への確定（{0}）")
    @ValueSource(strings = {"正しい値", "版の誤り", "企業IDの誤り", "範囲外の企業", "変更なし"})
    void 範囲外の利用者の確認は404で記録せず確定は値の誤りによらず404で試行を1行残す(String kind) throws Exception {
        // 行 10（T-211、V7）。値は範囲の判定の後に解釈する
        Company outsideCompany = newCompany("範囲外の会社", CompanyKind.OPERATOR);
        Cookie session = logins().login(admin(operator, shipperA));
        String stranger = user(outsideCompany, Role.SALES);

        mvc.perform(get(confirmationUrl(stranger))
                        .cookie(session)
                        .param("companies", id(shipperA))
                        .param("version", "0"))
                .andExpect(status().isNotFound());
        assertThat(audits(stranger, "ADMIN_SCOPE_DENIED")).as("確認の GET は記録しない").isZero();

        ResultActions wrong =
                switch (kind) {
                    case "版の誤り" -> postChange(session, stranger, "x", id(shipperA));
                    case "企業IDの誤り" -> postChange(session, stranger, "0", "not-a-uuid");
                    case "範囲外の企業" -> postChange(session, stranger, "0", id(shipperB));
                    case "変更なし" -> postChange(session, stranger, "0");
                    default -> postChange(session, stranger, "0", id(shipperA));
                };
        wrong.andExpect(status().isNotFound());
        postChange(session, stranger, "0", id(shipperA)).andExpect(status().isNotFound());

        assertThat(audits(stranger, "ADMIN_SCOPE_DENIED")).isEqualTo(2);
        assertThat(companiesOf(stranger)).isEmpty();
    }

    @Test
    void システム管理者のほかの役割は確認も確定もできない() throws Exception {
        String target = user(operator, Role.SALES);
        Cookie session = logins().login(user(operator, Role.CUSTOMER_SUPPORT));

        mvc.perform(get(confirmationUrl(target))
                        .cookie(session)
                        .param("companies", id(shipperA))
                        .param("version", "0"))
                .andExpect(status().isForbidden());
        postChange(session, target, "0", id(shipperA)).andExpect(status().isForbidden());

        assertThat(companiesOf(target)).isEmpty();
    }

    @Test
    void 確定のPOSTにもCSRFのトークンが要り失効の案内へ移し何も書かない() throws Exception {
        // 前例（Bolt 32f）どおり、業務の画面の CSRF の拒否は失効の案内へ移しその session を終える
        Cookie session = logins().login(fullAdmin());
        String target = user(operator, Role.SALES);

        MvcResult rejected = mvc.perform(post(confirmationUrl(target))
                        .cookie(session)
                        .param("companies", id(shipperA))
                        .param("version", "0"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(rejected.getResponse().getRedirectedUrl()).isEqualTo(SessionPaths.EXPIRED_URL);
        assertThat(companiesOf(target)).isEmpty();
        assertThat(audits(target, "ASSIGNMENTS_CHANGED")).isZero();
    }

    // --- helpers ---

    private static String id(Company company) {
        return company.id().value().toString();
    }

    private ResultActions postChange(Cookie session, String target, String version, String... companies)
            throws Exception {
        var request = post(confirmationUrl(target))
                .cookie(session)
                .param("version", version)
                .with(csrf());
        if (companies.length > 0) {
            request.param("companies", companies);
        }
        return mvc.perform(request);
    }

    private String detailUrl(String email) {
        return USERS_URL + "/" + logins().userIdOf(email).value();
    }

    private String confirmationUrl(String email) {
        return detailUrl(email) + "/assignment-changes/confirmation";
    }

    private List<UUID> companiesOf(String email) {
        return jdbc.queryForList(
                "SELECT scope_id FROM identity.user_assignment WHERE user_id = ? AND scope_kind = 'COMPANY'",
                UUID.class,
                logins().userIdOf(email).value());
    }

    private long versionOf(String email) {
        return jdbc.queryForObject("SELECT version FROM identity.app_user WHERE email = ?", Long.class, email);
    }

    private int audits(String email, String action) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = ? AND target_id = ?",
                Integer.class,
                action,
                logins().userIdOf(email).value().toString());
    }

    private String user(Company company, Role role) {
        String email = "assignment-test-" + UUID.randomUUID() + "@example.com";
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
        return email;
    }

    private IdentityLogins logins() {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock);
    }
}

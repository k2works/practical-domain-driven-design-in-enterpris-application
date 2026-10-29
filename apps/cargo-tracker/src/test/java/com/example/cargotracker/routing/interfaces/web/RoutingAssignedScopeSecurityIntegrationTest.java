package com.example.cargotracker.routing.interfaces.web;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.identity.AdministrationFixtures;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCodeGenerator;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredential;
import com.example.cargotracker.identity.domain.model.aggregates.TotpCredentialRepository;
import com.example.cargotracker.identity.domain.model.aggregates.User;
import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.identity.domain.model.valueobjects.EmailAddress;
import com.example.cargotracker.identity.domain.model.valueobjects.UserStatus;
import com.example.cargotracker.identity.infrastructure.crypto.AesGcmSecretCipher;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 経路設計の社内の画面の担当範囲の統合テスト（Bolt 38g。US-16 AC4、IA-INV-02・IA-INV-08、SEC-12、ADR-019）。本物のフィルターの連なりと
 * PostgreSQL の上で、経路設計者の 5 つの入口を案件の荷主企業で絞り、範囲外の GET は 404 で記録せず、範囲外の状態を変える POST（候補の算出・
 * 確定）は値の誤りによらず 404 で担当範囲の外の試行を 1 行残すことを確かめる。型の変換で止まる値（数でない版・候補番号）は範囲によらず 400 で
 * 記録しない。38f の {@code StaffAssignedScopeSecurityIntegrationTest} と同じ構成にし、Spring の文脈を増やさない（#50）。共有の表は対象の
 * 案件番号と操作者で絞る（T-113）。案件番号はほかのテストとぶつからないよう 2087 年を使う。
 */
@IdentityIntegrationTest
class RoutingAssignedScopeSecurityIntegrationTest {

    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String CASES_URL = "/staff/routing-cases";
    private static final String NO_ASSIGNED_COMPANIES = "担当企業がまだないので、表示できる経路設計の案件はありません。システム管理者に担当企業の割り当てを頼んでください。";
    /** テストの時刻（38f と同じ。置かないと TOTP の刻みが 0 になる）。見積有効期限と希望到着期限は 2099 年。 */
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    CompanyRepository companies;

    @Autowired
    UserRepository users;

    @Autowired
    TotpCredentialRepository totpCredentials;

    @Autowired
    TotpCodeGenerator totpCodes;

    @Autowired
    AesGcmSecretCipher cipher;

    @Autowired
    RoutingCaseRepository routingCases;

    @Autowired
    RoutingCaseNumberIssuer numberIssuer;

    private Company operator;
    private CompanyId shipperA;
    private CompanyId shipperB;
    /** 最後に作った経路設計者（監査の件数を操作者で絞る。T-113）。 */
    private UserId lastDesigner;

    @BeforeEach
    void 企業を用意する() {
        ((MutableClock) clock).setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        shipperA = new CompanyId(UUID.randomUUID());
        shipperB = new CompanyId(UUID.randomUUID());
    }

    @Test
    void 案件一覧には担当企業の案件だけが出る() throws Exception {
        // 行 1a
        String inScope = opened(shipperA);
        String outOfScope = opened(shipperB);
        Cookie session = login(designer(shipperA));

        assertThat(body(session, CASES_URL)).contains(inScope).doesNotContain(outOfScope);
    }

    @Test
    void 範囲の外の案件だけなら案件一覧に何も出ない() throws Exception {
        // 行 1b
        String outOfScope = opened(shipperB);
        Cookie session = login(designer(shipperA));

        assertThat(body(session, CASES_URL)).doesNotContain(outOfScope).doesNotContain(NO_ASSIGNED_COMPANIES);
    }

    @Test
    void 範囲の外の案件の詳細と確定の確認は404で記録しない() throws Exception {
        // 行 2a
        String outOfScope = opened(shipperB);
        Cookie session = login(designer(shipperA));

        mvc.perform(get(CASES_URL + "/" + outOfScope).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(get(CASES_URL + "/" + outOfScope + "/confirmation")
                        .param("candidate", "1")
                        .cookie(session))
                .andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isZero();
    }

    @Test
    void 範囲の外の案件の候補の算出は404で試行を1行記録する() throws Exception {
        // 行 3a（候補の算出）
        String outOfScope = opened(shipperB);
        Cookie session = login(designer(shipperA));

        mvc.perform(calculate(outOfScope, "0").cookie(session)).andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isOne();
        assertThat(evaluatedVersionsOf(outOfScope)).isZero();
    }

    @Test
    void 範囲の外の算出済みの案件の確定は404で試行を1行記録し経路を確定しない() throws Exception {
        // 行 3a（確定）。候補があり版も合う確定なので、範囲の判定がなければ確定まで進む（開発レビューの低 9）
        String outOfScope = calculated(shipperB);
        Cookie session = login(designer(shipperA));

        mvc.perform(confirm(outOfScope, "1", "直行で期限に間に合う。", String.valueOf(aggregateVersionOf(outOfScope)))
                        .cookie(session))
                .andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isOne();
        assertThat(routeVersionStatusOf(outOfScope)).isEqualTo("CANDIDATES_PRESENTED");
    }

    static Stream<String> 範囲の外で値が誤っている確定() {
        // 行 4（判断根拠の欠け、古い版）
        return Stream.of("", "直行で期限に間に合う。");
    }

    @ParameterizedTest
    @MethodSource("範囲の外で値が誤っている確定")
    void 範囲の外では値の誤りより先に404で記録する(String rationale) throws Exception {
        // 行 4（T-211。理由の欠けと古い版のどちらでも、範囲の判定が先）
        String outOfScope = opened(shipperB);
        Cookie session = login(designer(shipperA));

        mvc.perform(confirm(outOfScope, "99", rationale, "7").cookie(session)).andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isOne();
    }

    @Test
    void 型の誤りは範囲によらず400で記録しない() throws Exception {
        // 行 4b
        String outOfScope = opened(shipperB);
        Cookie session = login(designer(shipperA));

        mvc.perform(calculate(outOfScope, "abc").cookie(session)).andExpect(status().isBadRequest());
        mvc.perform(confirm(outOfScope, "abc", "直行で期限に間に合う。", "0").cookie(session))
                .andExpect(status().isBadRequest());
        mvc.perform(confirm(outOfScope, "1", "直行で期限に間に合う。", "abc").cookie(session))
                .andExpect(status().isBadRequest());
        mvc.perform(get(CASES_URL + "/" + outOfScope + "/confirmation")
                        .param("candidate", "abc")
                        .cookie(session))
                .andExpect(status().isBadRequest());

        assertThat(deniedOf(outOfScope)).isZero();
    }

    @Test
    void 範囲の中の案件は今のまま開けて候補を算出できる() throws Exception {
        // 行 5a
        String inScope = opened(shipperA);
        Cookie session = login(designer(shipperA));

        assertThat(body(session, CASES_URL + "/" + inScope)).contains(inScope);
        mvc.perform(calculate(inScope, "0").cookie(session)).andExpect(status().is3xxRedirection());

        assertThat(deniedOf(inScope)).isZero();
    }

    @Test
    void 担当企業がない経路設計者は一覧が空で案内が出て詳細は404で算出は404で記録する() throws Exception {
        // 行 6
        String routingCase = opened(shipperA);
        Cookie session = login(designer());

        assertThat(body(session, CASES_URL)).doesNotContain(routingCase).contains(NO_ASSIGNED_COMPANIES);
        mvc.perform(get(CASES_URL + "/" + routingCase).cookie(session)).andExpect(status().isNotFound());
        assertThat(deniedOf(routingCase)).isZero();
        mvc.perform(calculate(routingCase, "0").cookie(session)).andExpect(status().isNotFound());
        assertThat(deniedOf(routingCase)).isOne();
    }

    private String designer(CompanyId... assigned) {
        String email = "routing-scope-test-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        lastDesigner = userId;
        users.add(User.of(
                userId,
                operator.id(),
                EmailAddress.of(email),
                "経路 設計",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(Role.ROUTE_DESIGNER)));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        for (CompanyId company : assigned) {
            AdministrationFixtures.assignCompany(jdbc, userId, company);
        }
        return email;
    }

    private Cookie login(String email) throws Exception {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock).login(email);
    }

    /** 荷主企業の経路設計案件（経路版 1 は作成中）を、DE-16 の処理を通さずに保存する（テストの準備）。 */
    private String opened(CompanyId shipper) {
        RoutingCase routingCase = newCase(shipper);
        routingCases.save(routingCase);
        return routingCase.number().text();
    }

    /**
     * 荷主企業の経路設計案件に、その荷主を担当する経路設計者が画面から候補を算出した状態にする（確定まで進める準備。開発レビューの低 9）。
     * 確定は航海を読み直すので、直行の航海を表に入れる（取得時刻はテストの時刻で、情報の鮮度で拒まれない。前例
     * {@code RouteConfirmedAssignmentIntegrationTest}）。
     */
    private String calculated(CompanyId shipper) throws Exception {
        String caseNumber = opened(shipper);
        directVoyage();
        Cookie session = login(designer(shipper));
        mvc.perform(calculate(caseNumber, String.valueOf(aggregateVersionOf(caseNumber)))
                        .cookie(session))
                .andExpect(status().is3xxRedirection());
        assertThat(routeVersionStatusOf(caseNumber)).isEqualTo("CANDIDATES_PRESENTED");
        return caseNumber;
    }

    private void directVoyage() {
        String voyageNumber = "IT-B38G-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO routing.voyage (voyage_number, adopted_info_version, source_kind, source_ref,"
                        + " acquired_at, version, updated_at) VALUES (?, ?, 'MANUAL_ENTRY', 'テスト',"
                        + " TIMESTAMP WITH TIME ZONE '2026-11-09 00:00:00+00', 0, CURRENT_TIMESTAMP)",
                voyageNumber,
                voyageNumber + "@1");
        jdbc.update(
                "INSERT INTO routing.port_call VALUES (?, 1, 'JPTYO', NULL, TIMESTAMP WITH TIME ZONE '2099-10-12 00:00:00+00')",
                voyageNumber);
        jdbc.update(
                "INSERT INTO routing.port_call VALUES (?, 2, 'NLRTM', TIMESTAMP WITH TIME ZONE '2099-10-30 00:00:00+00', NULL)",
                voyageNumber);
    }

    private RoutingCase newCase(CompanyId shipper) {
        return RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                numberIssuer.next(2087),
                UUID.randomUUID(),
                "TR-2087-0001",
                1,
                UUID.randomUUID(),
                List.of(),
                new RouteSpecification(
                        new Location("JPTYO"), new Location("NLRTM"), at("2099-10-31T00:00:00Z"), "GENERAL"),
                at("2026-11-08T00:00:00Z"),
                at("2099-11-30T00:00:00Z"),
                UUID.randomUUID(),
                shipper);
    }

    private static MockHttpServletRequestBuilder calculate(String caseNumber, String expectedVersion) {
        return post(CASES_URL + "/" + caseNumber + "/candidates")
                .with(csrf())
                .param("expectedVersion", expectedVersion);
    }

    private static MockHttpServletRequestBuilder confirm(
            String caseNumber, String candidate, String rationale, String expectedVersion) {
        return post(CASES_URL + "/" + caseNumber + "/confirmation")
                .with(csrf())
                .param("candidate", candidate)
                .param("rationale", rationale)
                .param("expectedVersion", expectedVersion);
    }

    private int deniedOf(String caseNumber) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ASSIGNED_SCOPE_DENIED'"
                        + " AND target_type = 'ROUTING_CASE' AND target_id = ? AND actor_user_id = ?",
                Integer.class,
                caseNumber,
                lastDesigner.value());
    }

    private int evaluatedVersionsOf(String caseNumber) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM routing.route_version v JOIN routing.routing_case c ON v.routing_case_id = c.id"
                        + " WHERE c.case_number = ? AND v.candidates_evaluated_at IS NOT NULL",
                Integer.class,
                caseNumber);
    }

    private long aggregateVersionOf(String caseNumber) {
        return jdbc.queryForObject(
                "SELECT version FROM routing.routing_case WHERE case_number = ?", Long.class, caseNumber);
    }

    private String routeVersionStatusOf(String caseNumber) {
        return jdbc.queryForObject(
                "SELECT v.status FROM routing.route_version v JOIN routing.routing_case c ON v.routing_case_id = c.id"
                        + " WHERE c.case_number = ? AND v.route_version_no = 1",
                String.class,
                caseNumber);
    }

    private String body(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}

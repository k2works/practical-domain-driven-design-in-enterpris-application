package com.example.cargotracker.tracking.interfaces.web;

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
import java.time.Instant;
import java.util.Map;
import java.util.Random;
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
 * 追跡の社内の画面の担当範囲の統合テスト（Bolt 38g。US-16 AC4、IA-INV-02・IA-INV-08、SEC-12、ADR-019）。本物のフィルターの連なりと
 * PostgreSQL の上で、追跡管理者の 4 つの入口を追跡記録の荷主企業で絞り、範囲外の GET は 404 で記録せず、範囲外のマイルストーンの登録の POST は
 * 値の誤り（型の誤りを含む。束縛の誤りは BindingResult が受ける）によらず 404 で担当範囲の外の試行を 1 行残すことを確かめる。38f の
 * {@code StaffAssignedScopeSecurityIntegrationTest} と同じ構成にし、Spring の文脈を増やさない（#50）。共有の表は対象の追跡番号と操作者で
 * 絞る（T-113）。
 */
@IdentityIntegrationTest
class TrackingAssignedScopeSecurityIntegrationTest {

    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String RECORDS_URL = "/staff/tracking-records";
    private static final String NO_ASSIGNED_COMPANIES = "担当企業がまだないので、表示できる追跡記録はありません。システム管理者に担当企業の割り当てを頼んでください。";
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Random RANDOM = new Random();
    /** テストの時刻（38f と同じ。置かないと TOTP の刻みが 0 になる）。追跡の開始は 2099 年（一覧の先頭に出す）。 */
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
    TrackingRecordRepository trackingRecords;

    private Company operator;
    private CompanyId shipperA;
    private CompanyId shipperB;
    /** 最後に作った追跡管理者（監査の件数を操作者で絞る。T-113）。 */
    private UserId lastManager;

    @BeforeEach
    void 企業を用意する() {
        ((MutableClock) clock).setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        shipperA = new CompanyId(UUID.randomUUID());
        shipperB = new CompanyId(UUID.randomUUID());
    }

    @Test
    void 追跡一覧には担当企業の追跡記録だけが出る() throws Exception {
        // 行 1c
        String inScope = started(shipperA);
        String outOfScope = started(shipperB);
        Cookie session = login(manager(shipperA));

        assertThat(body(session, RECORDS_URL)).contains(inScope).doesNotContain(outOfScope);
    }

    @Test
    void 範囲の外の追跡記録だけなら追跡一覧に出ない() throws Exception {
        // 行 1d
        String outOfScope = started(shipperB);
        Cookie session = login(manager(shipperA));

        assertThat(body(session, RECORDS_URL)).doesNotContain(outOfScope).doesNotContain(NO_ASSIGNED_COMPANIES);
    }

    @Test
    void 範囲の外の追跡の詳細と登録の画面は404で記録しない() throws Exception {
        // 行 2b
        String outOfScope = started(shipperB);
        Cookie session = login(manager(shipperA));

        mvc.perform(get(RECORDS_URL + "/" + outOfScope).cookie(session)).andExpect(status().isNotFound());
        mvc.perform(get(RECORDS_URL + "/" + outOfScope + "/milestones/new").cookie(session))
                .andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isZero();
    }

    @Test
    void 範囲の外のマイルストーンの登録は404で試行を1行記録し登録しない() throws Exception {
        // 行 3b
        String outOfScope = started(shipperB);
        Cookie session = login(manager(shipperA));

        mvc.perform(register(outOfScope, Map.of()).cookie(session)).andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isOne();
        assertThat(milestonesOf(outOfScope)).isZero();
    }

    static Stream<Map<String, String>> 範囲の外で値が誤っている登録() {
        // 行 4（日時の形の誤り、数でない版、古い版）
        return Stream.of(
                Map.of("occurredAt", "2026/11/01"), Map.of("expectedVersion", "abc"), Map.of("expectedVersion", "7"));
    }

    @ParameterizedTest
    @MethodSource("範囲の外で値が誤っている登録")
    void 範囲の外では値の誤りより先に404で記録する(Map<String, String> overrides) throws Exception {
        // 行 4（T-211。範囲の確かめは入力の変換より前）
        String outOfScope = started(shipperB);
        Cookie session = login(manager(shipperA));

        mvc.perform(register(outOfScope, overrides).cookie(session)).andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isOne();
    }

    @Test
    void 範囲の中の追跡記録は今のまま開けてマイルストーンを登録できる() throws Exception {
        // 行 5b
        String inScope = started(shipperA);
        Cookie session = login(manager(shipperA));

        assertThat(body(session, RECORDS_URL + "/" + inScope)).contains(inScope);
        assertThat(body(session, RECORDS_URL + "/" + inScope + "/milestones/new"))
                .contains(inScope);
        mvc.perform(register(inScope, Map.of()).cookie(session)).andExpect(status().is3xxRedirection());

        assertThat(milestonesOf(inScope)).isOne();
        assertThat(deniedOf(inScope)).isZero();
    }

    @Test
    void 担当企業がない追跡管理者は一覧が空で案内が出て詳細は404で登録は404で記録する() throws Exception {
        // 行 6
        String trackingRecord = started(shipperA);
        Cookie session = login(manager());

        assertThat(body(session, RECORDS_URL)).doesNotContain(trackingRecord).contains(NO_ASSIGNED_COMPANIES);
        mvc.perform(get(RECORDS_URL + "/" + trackingRecord).cookie(session)).andExpect(status().isNotFound());
        assertThat(deniedOf(trackingRecord)).isZero();
        mvc.perform(register(trackingRecord, Map.of()).cookie(session)).andExpect(status().isNotFound());
        assertThat(deniedOf(trackingRecord)).isOne();
    }

    private String manager(CompanyId... assigned) {
        String email = "tracking-scope-test-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        lastManager = userId;
        users.add(User.of(
                userId,
                operator.id(),
                EmailAddress.of(email),
                "追跡 管理",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(Role.TRACKING_MANAGER)));
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

    /** 荷主企業の追跡を開始した追跡記録を、予約の確定と DE-07 の処理を通さずに保存する（テストの準備）。 */
    private String started(CompanyId shipper) {
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        TrackingRecord trackingRecord = TrackingRecord.start(
                        new TrackingNumber(value.toString()),
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        new UtcInstant(Instant.parse("2099-01-01T00:00:00Z")))
                .trackingRecord();
        trackingRecords.save(trackingRecord);
        return trackingRecord.trackingNumber().value();
    }

    /** 正しい入力のうち、指定した項目だけを置き換えて送る。 */
    private static MockHttpServletRequestBuilder register(String trackingNumber, Map<String, String> overrides) {
        MockHttpServletRequestBuilder request =
                post(RECORDS_URL + "/" + trackingNumber + "/milestones").with(csrf());
        Map<String, String> values = new java.util.LinkedHashMap<>(Map.of(
                "kind", "PICKUP",
                "location", "JPTYO",
                "occurredAt", "2026-11-08 11:30",
                "sourceKind", "FIELD_RECORD",
                "sourceReference", "F-" + UUID.randomUUID(),
                "expectedVersion", "0"));
        values.putAll(overrides);
        values.forEach(request::param);
        return request;
    }

    private int deniedOf(String trackingNumber) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ASSIGNED_SCOPE_DENIED'"
                        + " AND target_type = 'TRACKING_RECORD' AND target_id = ? AND actor_user_id = ?",
                Integer.class,
                trackingNumber,
                lastManager.value());
    }

    private int milestonesOf(String trackingNumber) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM tracking.milestone WHERE tracking_number = ?", Integer.class, trackingNumber);
    }

    private String body(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }
}

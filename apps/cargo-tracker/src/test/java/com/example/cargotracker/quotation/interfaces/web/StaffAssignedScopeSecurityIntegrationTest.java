package com.example.cargotracker.quotation.interfaces.web;

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
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.acceptance.MutableClock;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 見積りの側の社内の画面の担当範囲の統合テスト（Bolt 38f。US-16 AC4、IA-INV-02・IA-INV-08、SEC-12）。本物のフィルターの連なりと
 * PostgreSQL の上で、営業担当者の社内の 10 の入口を担当企業で絞り、範囲外の GET は 404 で記録せず、範囲外の確定の POST は値の誤りによらず
 * 404 で担当範囲の外の試行を 1 行残すことを確かめる。担当企業は request ごとの確かめで載るので、割り当てを変えると次の request から効く。
 * 共有の表は対象の業務番号で絞る（T-113）。業務番号はほかのテストとぶつからないよう 2087 年を使う。
 */
@IdentityIntegrationTest
class StaffAssignedScopeSecurityIntegrationTest {

    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String LIST_URL = "/staff/transport-requests";
    private static final String NO_ASSIGNED_COMPANIES = "担当企業がまだないので、表示できる輸送要求はありません。システム管理者に担当企業の割り当てを頼んでください。";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);
    /**
     * テストの時刻。共通の時計（{@code IdentityTestClock}）は既定で EPOCH を指し、置かないと TOTP の刻みが 0 になって、使った刻みを 0 に
     * 戻した後のコードが再利用として拒まれる（ほかのテストが先に時刻を置いたときだけ通っていた。順番に依存しない。開発レビューの直しで見つけた）。
     */
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
    TransportRequestRepository transportRequests;

    @Autowired
    TransportRequestCommandService transportRequestCommands;

    @Autowired
    TransportRequestReviewService reviews;

    @Autowired
    QuotationCommandService quotationCommands;

    private Company operator;
    private CompanyId shipperA;
    private CompanyId shipperB;
    /** 最後に作った営業担当者（監査の件数を操作者で絞る。T-113）。 */
    private UserId lastSales;

    @BeforeEach
    void 企業を用意する() {
        ((MutableClock) clock).setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        shipperA = new CompanyId(UUID.randomUUID());
        shipperB = new CompanyId(UUID.randomUUID());
    }

    private String sales(CompanyId... assigned) {
        String email = "scope-test-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        lastSales = userId;
        users.add(User.of(
                userId,
                operator.id(),
                EmailAddress.of(email),
                "営業 一郎",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                Set.of(Role.SALES)));
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

    /** 審査中の輸送要求を作る。 */
    private String underReview(CompanyId shipper) {
        TransportRequestNumber number = new TransportRequestNumber(2087, SEQUENCE.getAndIncrement());
        transportRequests.save(TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                number,
                shipper,
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(clock.instant())));
        return number.text();
    }

    /**
     * 書類 1 件の版 1 と、見積り 1 を持つ見積中の輸送要求を作る（GET の 6 入口すべてに対象がある。開発レビュー M1）。
     */
    private String quotedWithDocument(CompanyId shipper) {
        UserId staff = new UserId(UUID.randomUUID());
        AuthenticatedActor handling = AuthenticatedActorFixture.salesHandling(staff, shipper);
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) transportRequestCommands.submit(new SubmitTransportRequestCommand(
                        shipper,
                        new UserId(UUID.randomUUID()),
                        ShipmentTermsFixture.completeInput(),
                        List.of(new RequiredDocumentAttachment(
                                DocumentType.COMMERCIAL_INVOICE,
                                "invoice.pdf",
                                "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII)))));
        TransportRequestNumber number = submitted.number();
        reviews.approve(new ApproveTransportRequestCommand(number, 1, staff, "根拠"), handling);
        quotationCommands.calculate(new CalculateQuotationCommand(number, QuotationFixture.completeInput()), handling);
        return number.text();
    }

    private int deniedOf(String targetId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM identity.audit_record WHERE action = 'ASSIGNED_SCOPE_DENIED' AND target_id = ?"
                        + " AND actor_user_id = ?",
                Integer.class,
                targetId,
                lastSales.value());
    }

    private String body(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 一覧には担当企業の輸送要求だけが出る() throws Exception {
        // 行 1a・2a
        String inScope = underReview(shipperA);
        String outOfScope = underReview(shipperB);

        String page = body(login(sales(shipperA)), LIST_URL);

        assertThat(page).contains(inScope).doesNotContain(outOfScope).doesNotContain(NO_ASSIGNED_COMPANIES);
    }

    @Test
    void 担当企業のない営業担当者の一覧は空で上部に案内の文を示しGETは404() throws Exception {
        // 行 3a・3b
        String request = underReview(shipperA);
        Cookie session = login(sales());

        assertThat(body(session, LIST_URL)).contains(NO_ASSIGNED_COMPANIES).doesNotContain(request);
        mvc.perform(get(LIST_URL + "/" + request).cookie(session)).andExpect(status().isNotFound());
        assertThat(deniedOf(request)).isZero();
    }

    static Stream<String> getEntrances() {
        return Stream.of(
                "", "/versions/1/documents/1", "/quotations/new", "/quotations/1", "/quotations/1/requotation");
    }

    @ParameterizedTest
    @MethodSource("getEntrances")
    void 担当範囲の外の輸送要求のGETは404で記録しない(String path) throws Exception {
        // 行 2b・14（GET では記録しない。SEC-12）。書類と見積りを持つ輸送要求にし、範囲の判定がなくても 404 になる入口をなくす（M1）
        String outOfScope = quotedWithDocument(shipperB);
        Cookie session = login(sales(shipperA));

        mvc.perform(get(LIST_URL + "/" + outOfScope + path).cookie(session)).andExpect(status().isNotFound());

        assertThat(deniedOf(outOfScope)).isZero();
    }

    @ParameterizedTest
    @MethodSource("getEntrances")
    void 担当範囲の中の輸送要求のGETは今のまま開ける(String path) throws Exception {
        // 行 1b の対照（M1）。再見積りの画面は見積りの状態で詳細へ移ることがあるので、404 でなく 4xx・5xx でないことを見る
        String inScope = quotedWithDocument(shipperA);
        Cookie session = login(sales(shipperA));

        int status = mvc.perform(get(LIST_URL + "/" + inScope + path).cookie(session))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isLessThan(400);
    }

    static Stream<Arguments> postEntrances() {
        return Stream.of(
                Arguments.of("/reviews", "TRANSPORT_REQUEST", "", new String[] {
                    "decision", "APPROVED", "versionNo", "1", "rationale", "確認した"
                }),
                Arguments.of("/reviews", "TRANSPORT_REQUEST", "", new String[] {"decision", "知らない判断"}),
                Arguments.of("/reviews", "TRANSPORT_REQUEST", "", new String[] {}),
                Arguments.of(
                        "/quotations", "TRANSPORT_REQUEST", "", new String[] {"currency", "JPY", "expiresAt", "誤った日時"}),
                Arguments.of("/quotations/1/requotation", "QUOTATION", "/1", new String[] {"currency", "JPY"}),
                Arguments.of("/quotations/1/presentation", "QUOTATION", "/1", new String[] {}));
    }

    @ParameterizedTest
    @MethodSource("postEntrances")
    void 担当範囲の外の輸送要求の確定のPOSTは値の誤りによらず404で試行を1行残す(String path, String kind, String idSuffix, String[] params)
            throws Exception {
        // 行 2c・5・10（範囲の判定が先。記録は自分のトランザクションで確定していて、404 を投げても残る。T-211）
        String outOfScope = underReview(shipperB);
        Cookie session = login(sales(shipperA));
        MockHttpServletRequestBuilder request =
                post(LIST_URL + "/" + outOfScope + path).cookie(session).with(csrf());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }

        mvc.perform(request).andExpect(status().isNotFound());

        String targetId = outOfScope + idSuffix;
        assertThat(deniedOf(targetId)).isOne();
        assertThat(jdbc.queryForMap(
                        "SELECT actor_user_id, actor_company_id, result, reason, target_type, target_version"
                                + " FROM identity.audit_record"
                                + " WHERE action = 'ASSIGNED_SCOPE_DENIED' AND target_id = ? AND actor_user_id = ?",
                        targetId,
                        lastSales.value()))
                .containsEntry("actor_user_id", lastSales.value())
                .containsEntry("actor_company_id", operator.id().value())
                .containsEntry("result", "FAILURE")
                .containsEntry("reason", "OUT_OF_SCOPE")
                .containsEntry("target_type", kind)
                .containsEntry("target_version", null);
    }

    @Test
    void 担当企業のない営業担当者の確定のPOSTも404で試行を1行残す() throws Exception {
        // 行 3c
        String request = underReview(shipperA);
        Cookie session = login(sales());

        mvc.perform(post(LIST_URL + "/" + request + "/reviews")
                        .cookie(session)
                        .with(csrf())
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(status().isNotFound());

        assertThat(deniedOf(request)).isOne();
    }

    @Test
    void ない輸送要求のPOSTは404で記録しない() throws Exception {
        // 行 4
        String missing = new TransportRequestNumber(2087, 9000 + SEQUENCE.getAndIncrement()).text();
        Cookie session = login(sales(shipperA));

        mvc.perform(post(LIST_URL + "/" + missing + "/reviews")
                        .cookie(session)
                        .with(csrf())
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(status().isNotFound());

        assertThat(deniedOf(missing)).isZero();
    }

    @Test
    void 担当企業の輸送要求は見えて審査を確定できる() throws Exception {
        // 行 1b
        String inScope = underReview(shipperA);
        Cookie session = login(sales(shipperA));

        assertThat(body(session, LIST_URL + "/" + inScope)).contains(inScope);
        mvc.perform(post(LIST_URL + "/" + inScope + "/reviews")
                        .cookie(session)
                        .with(csrf())
                        .param("decision", "APPROVED")
                        .param("versionNo", "1")
                        .param("rationale", "確認した"))
                .andExpect(status().is3xxRedirection());
        assertThat(deniedOf(inScope)).isZero();
    }

    @Test
    void 経路の見積り番号が数でなければ範囲の外でも400で記録しない() throws Exception {
        // 計画の範囲より先の 400 の表（開発レビューの低 3。範囲によらず同じ応答なので、範囲の外があることは漏れない）
        String outOfScope = underReview(shipperB);
        Cookie session = login(sales(shipperA));

        mvc.perform(post(LIST_URL + "/" + outOfScope + "/quotations/abc/presentation")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(deniedOf(outOfScope + "/abc")).isZero();
        assertThat(deniedOf(outOfScope)).isZero();
    }

    @Test
    void 割り当てを足すと次のrequestから担当範囲の中になる() throws Exception {
        // 行 6 の逆の向き（開発レビューの低 5）
        String request = underReview(shipperA);
        Cookie session = login(sales());
        assertThat(body(session, LIST_URL)).doesNotContain(request).contains(NO_ASSIGNED_COMPANIES);

        AdministrationFixtures.assignCompany(jdbc, lastSales, shipperA);

        assertThat(body(session, LIST_URL)).contains(request).doesNotContain(NO_ASSIGNED_COMPANIES);
        assertThat(body(session, LIST_URL + "/" + request)).contains(request);
    }

    @Test
    void 割り当てを外すと次のrequestから担当範囲の外になる() throws Exception {
        // 行 6（T-216。打ち消す操作の後）
        String inScope = underReview(shipperA);
        String email = sales(shipperA);
        Cookie session = login(email);
        assertThat(body(session, LIST_URL)).contains(inScope);

        jdbc.update(
                "DELETE FROM identity.user_assignment WHERE user_id = (SELECT id FROM identity.app_user WHERE email = ?)",
                email);

        assertThat(body(session, LIST_URL)).doesNotContain(inScope).contains(NO_ASSIGNED_COMPANIES);
        mvc.perform(get(LIST_URL + "/" + inScope).cookie(session)).andExpect(status().isNotFound());
    }
}

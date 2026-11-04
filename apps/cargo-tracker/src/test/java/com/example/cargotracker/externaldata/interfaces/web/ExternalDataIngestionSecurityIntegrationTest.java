package com.example.cargotracker.externaldata.interfaces.web;

import static com.example.cargotracker.identity.IdentityLogins.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.identity.IdentityIntegrationTest;
import com.example.cargotracker.identity.IdentityLogins;
import com.example.cargotracker.identity.application.internal.eventhandlers.ExternalRecordAuditEventHandler;
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
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * S-16 外部データの取込のセキュリティの統合テスト（Bolt 39c の行 1・2・6・7・13・13b・15。US-14 AC1）。本物のフィルターの連なりと
 * PostgreSQL の上で、{@code /staff/external-data/**} はデータ責任者だけが開けること（ほかの 7 つの役割は A-04、未ログインは A-01）、
 * CSRF のトークンのない multipart の送信で取り込まないこと（取込のサービスの spy の呼び出しの記録。T-236）、画面の POST（トランザクションの
 * 外）から DB の件数と DE-12・DE-14 の監査の発行記録の完了まで（T-184）、ナビの項目がデータ責任者を持つ利用者にだけ出ることを確かめる。
 *
 * <p>取込と監査記録は追記専用で共有の DB に残る。確かめる行は、提供元の名前に UUID を含めた自分の情報源で絞り、情報源は最後に停止中にして
 * 承認済みのまま残さない（前例 {@code ExternalRecordAuditDeliveryIntegrationTest}）。
 */
@IdentityIntegrationTest
class ExternalDataIngestionSecurityIntegrationTest {

    private static final byte[] TOTP_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final Instant NOW = Instant.parse("2026-11-16T01:05:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String NEW_URL = "/staff/external-data/ingestions/new";
    private static final String INGEST_URL = "/staff/external-data/ingestions";
    /** ナビの項目（属性の順に依らない正規表現）。 */
    private static final String NAV_LINK = "href=\"" + NEW_URL + "\"[^>]*>外部データの取込</a>";

    private static final String AUDIT_LISTENER = ExternalRecordAuditEventHandler.class.getSimpleName();
    private static final String PORT_CALLS =
            "JPTYO;2026-12-01T00:00:00Z;2026-12-01T12:00:00Z|USLAX;2026-12-10T00:00:00Z;2026-12-10T12:00:00Z";
    private static final String CHANGED_PORT_CALLS =
            "JPTYO;2026-12-02T00:00:00Z;2026-12-02T12:00:00Z|USLAX;2026-12-10T00:00:00Z;2026-12-10T12:00:00Z";

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

    @MockitoSpyBean
    IngestionService ingestionService;

    private Company operator;
    private Company shipper;
    private final String provider = "画面の提供元-" + UUID.randomUUID();
    private UUID sourceId;

    @BeforeEach
    void 承認済みの情報源を用意する() {
        ((MutableClock) clock).setInstant(NOW);
        operator = Company.of(new CompanyId(UUID.randomUUID()), "A 社", CompanyKind.OPERATOR, true);
        companies.add(operator);
        shipper = Company.of(new CompanyId(UUID.randomUUID()), "荷主 A", CompanyKind.SHIPPER, true);
        companies.add(shipper);
        sourceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                        + " VALUES (?, ?, 'VOYAGE', 'APPROVED', 0)",
                sourceId,
                provider);
    }

    /** コミットした情報源を承認済みのまま残さない。取込が残っていれば消せないので、先に停止中にする。 */
    @AfterEach
    void 情報源を承認済みのまま残さない() {
        jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);
        jdbc.update(
                "DELETE FROM external_data.data_source WHERE id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM external_data.ingestion WHERE data_source_id = ?)",
                sourceId,
                sourceId);
    }

    // --- 行 1: 認可 ---

    @Test
    void 行1_データ責任者は選ぶ画面と取込と結果の画面を開ける() throws Exception {
        Cookie session = login(user(Set.of(Role.DATA_STEWARD)));

        mvc.perform(get(NEW_URL).cookie(session)).andExpect(status().isOk());
        String location = postIngest(session, file(row("V-1", PORT_CALLS)))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        assertThat(location).matches(INGEST_URL + "/[0-9a-f-]{36}");
        mvc.perform(get(location).cookie(session)).andExpect(status().isOk());
    }

    @Test
    void 行1_ほかの7つの役割は3つのURLで403になり取り込まない() throws Exception {
        String resultUrl = INGEST_URL + "/" + UUID.randomUUID();
        for (Role role : EnumSet.complementOf(EnumSet.of(Role.DATA_STEWARD))) {
            Cookie session = login(user(Set.of(role)));

            mvc.perform(get(NEW_URL).cookie(session)).andExpect(status().isForbidden());
            postIngest(session, file(row("V-1", PORT_CALLS))).andExpect(status().isForbidden());
            mvc.perform(get(resultUrl).cookie(session)).andExpect(status().isForbidden());
        }
        verify(ingestionService, never()).ingest(any(), any());
        assertThat(ingestionsOfSource()).isZero();
    }

    @Test
    void 行1_未ログインは3つのURLでA01へ移る() throws Exception {
        for (MvcResult result : List.of(
                mvc.perform(get(NEW_URL)).andReturn(),
                mvc.perform(ingestRequest(file(row("V-1", PORT_CALLS))).with(csrf()))
                        .andReturn(),
                mvc.perform(get(INGEST_URL + "/" + UUID.randomUUID())).andReturn())) {
            assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login");
        }
        verify(ingestionService, never()).ingest(any(), any());
    }

    // --- 行 2: CSRF ---

    @Test
    void 行2_CSRFのトークンのない取込の送信は拒まれ取込のサービスを呼ばない() throws Exception {
        // 業務の画面の CSRF の拒否は 403 ではなく案内へ移す（Bolt 32f）。起きないことは spy の呼び出しの記録で確かめる（T-236）
        Cookie session = login(user(Set.of(Role.DATA_STEWARD)));

        MvcResult rejected = mvc.perform(
                        ingestRequest(file(row("V-1", PORT_CALLS))).cookie(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(rejected.getResponse().getRedirectedUrl()).doesNotStartWith(INGEST_URL);
        verify(ingestionService, never()).ingest(any(), any());
        assertThat(ingestionsOfSource()).isZero();
    }

    // --- 行 6・7: 画面の POST から DB の件数まで ---

    @Test
    void 行6_取り込むと結果の画面に件数が出て読み込み直しても取込は増えず同じファイルの2度目はすべて重複() throws Exception {
        Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
        byte[] content = file(row("V-1", PORT_CALLS), row("V-2", PORT_CALLS));

        String first = ingested(session, content);
        String page = body(session, first);
        String again = body(session, first);

        assertThat(page).contains("<dd>" + provider + "（航海）</dd>").contains("<dd>2</dd>");
        assertThat(counts(page)).containsExactly(2, 0, 0);
        assertThat(again).doesNotContain("取り込みました。");
        assertThat(ingestionsOfSource()).isEqualTo(1);

        String second = ingested(session, content);
        assertThat(counts(body(session, second))).containsExactly(0, 2, 0);
        assertThat(ingestionsOfSource()).isEqualTo(2);
    }

    @Test
    void 行7_内容の違う原本を含むファイルの分類別の件数は採用と重複と隔離の3行で取込の結果と一致する() throws Exception {
        Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
        byte[] content = file(row("V-1", PORT_CALLS), row("V-1", CHANGED_PORT_CALLS));

        assertThat(counts(body(session, ingested(session, content)))).containsExactly(1, 0, 1);
        String second = body(session, ingested(session, content));

        assertThat(counts(second)).containsExactly(0, 1, 1);
        assertThat(second).doesNotContain("差異");
    }

    // --- 行 15: 画面の POST から監査の配信まで ---

    @Test
    void 行15_トランザクションの外の画面のPOSTで取り込め自分の取込のDE12とDE14の監査の発行記録が完了する() throws Exception {
        Cookie session = login(user(Set.of(Role.DATA_STEWARD)));

        String location = ingested(session, file(row("V-1", PORT_CALLS), row("V-1", CHANGED_PORT_CALLS)));
        UUID ingestionId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        UUID adopted = receivedRecordOf(ingestionId, "ADOPTED");
        UUID quarantined = receivedRecordOf(ingestionId, "QUARANTINED");
        awaitCompletedAuditPublication(VoyageAdoptedValueUpdated.class, adopted);
        awaitCompletedAuditPublication(ExternalRecordQuarantined.class, quarantined);
    }

    // --- 行 13・13b: ナビ ---

    @Test
    void 行13_ナビの外部データの取込はデータ責任者に出てほかの役割の項目は出ない() throws Exception {
        Cookie steward = login(user(Set.of(Role.DATA_STEWARD)));

        String page = body(steward, NEW_URL);

        assertThat(page)
                .containsPattern(NAV_LINK)
                .doesNotContain(">見積依頼</a>")
                .doesNotContain(">経路設計</a>")
                .doesNotContain(">追跡</a>")
                .doesNotContain(">利用者</a>");
    }

    @Test
    void 行13_データ責任者を持たない社内の役割のナビには外部データの取込が出ない() throws Exception {
        assertThat(body(login(user(Set.of(Role.SALES))), "/staff/transport-requests"))
                .doesNotContainPattern(NAV_LINK);
        assertThat(body(login(user(Set.of(Role.ROUTE_DESIGNER))), "/staff/routing-cases"))
                .doesNotContainPattern(NAV_LINK);
        assertThat(body(login(user(Set.of(Role.TRACKING_MANAGER))), "/staff/tracking-records"))
                .doesNotContainPattern(NAV_LINK);
    }

    @Test
    void 行13b_営業担当者とデータ責任者を重ねた利用者のナビには両方の項目が出る() throws Exception {
        Cookie session = login(user(Set.of(Role.SALES, Role.DATA_STEWARD)));

        assertThat(body(session, "/staff/transport-requests"))
                .containsPattern(NAV_LINK)
                .contains(">見積依頼</a>");
        assertThat(body(session, NEW_URL)).containsPattern(NAV_LINK).contains(">見積依頼</a>");
    }

    // --- helpers ---

    private String user(Set<Role> roles) {
        boolean shipperRole = roles.contains(Role.SHIPPER);
        String email = "external-data-test-" + UUID.randomUUID() + "@example.com";
        UserId userId = new UserId(UUID.randomUUID());
        users.add(User.of(
                userId,
                (shipperRole ? shipper : operator).id(),
                EmailAddress.of(email),
                "利用者",
                passwordEncoder.encode(PASSWORD),
                UserStatus.ACTIVE,
                roles));
        totpCredentials.add(TotpCredential.enroll(
                userId, cipher.encrypt(TOTP_SECRET.clone(), userId), new UtcInstant(clock.instant())));
        return email;
    }

    private Cookie login(String email) throws Exception {
        return new IdentityLogins(mvc, jdbc, totpCredentials, totpCodes, clock).login(email);
    }

    /** 航海の行（寄港 2 つ・時刻の順。取得時刻は取込の時刻より前。T-230）。外部 ID は自分の情報源の中で一意なら足りる。 */
    private static String row(String externalId, String portCalls) {
        return externalId + ",1," + NOW.minus(Duration.ofMinutes(30)) + "," + portCalls;
    }

    private static byte[] file(String... rows) {
        return ("external_id,external_version,acquired_at,port_calls\n" + String.join("\n", rows) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private MockMultipartHttpServletRequestBuilder ingestRequest(byte[] content) {
        return (MockMultipartHttpServletRequestBuilder) multipart(INGEST_URL)
                .file(new MockMultipartFile("file", "voyage.csv", "text/csv", content))
                .param("dataSourceId", sourceId.toString());
    }

    private org.springframework.test.web.servlet.ResultActions postIngest(Cookie session, byte[] content)
            throws Exception {
        return mvc.perform(ingestRequest(content).cookie(session).with(csrf()));
    }

    /** 取り込み、結果の画面の URL（PRG の行き先）を返す。 */
    private String ingested(Cookie session, byte[] content) throws Exception {
        String location = postIngest(session, content)
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
        assertThat(location).matches(INGEST_URL + "/[0-9a-f-]{36}");
        return location;
    }

    private String body(Cookie session, String url) throws Exception {
        return mvc.perform(get(url).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** 結果の画面の分類別の件数（採用・重複・隔離の順）。 */
    private static List<Integer> counts(String page) {
        return List.of(countOf(page, "採用"), countOf(page, "重複"), countOf(page, "隔離"));
    }

    private static int countOf(String page, String classification) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "<th scope=\"row\">" + classification + "</th>\\s*<td>(\\d+)</td>")
                .matcher(page);
        assertThat(matcher.find()).as("分類 %s の行", classification).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private int ingestionsOfSource() {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM external_data.ingestion WHERE data_source_id = ?", Integer.class, sourceId);
        return value == null ? 0 : value;
    }

    private UUID receivedRecordOf(UUID ingestionId, String classification) {
        return jdbc.queryForObject(
                "SELECT id FROM external_data.received_record WHERE ingestion_id = ? AND classification = ?",
                UUID.class,
                ingestionId,
                classification);
    }

    /** 監査の listener の、この受信記録のイベントの発行記録が完了になるまで待つ（前例 ExternalRecordAuditDeliveryIntegrationTest）。 */
    private void awaitCompletedAuditPublication(Class<?> eventType, UUID receivedRecordId) {
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(jdbc.queryForObject(
                                "SELECT COUNT(*) FROM platform.event_publication WHERE event_type = ?"
                                        + " AND serialized_event LIKE ? AND listener_id LIKE ?"
                                        + " AND completion_date IS NOT NULL",
                                Integer.class,
                                eventType.getName(),
                                "%" + receivedRecordId + "%",
                                "%" + AUDIT_LISTENER + "%"))
                        .isEqualTo(1));
    }
}

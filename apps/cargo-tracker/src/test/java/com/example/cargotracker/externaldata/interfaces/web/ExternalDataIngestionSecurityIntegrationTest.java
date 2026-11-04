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
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.rules.IngestionFileLimit;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect.Extent;
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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * S-16 外部データの取込のセキュリティの統合テスト（Bolt 39c の行 1・2・6・7・13・13b・15、Bolt 39c2 の行 1・2・5、Bolt 39c3 の行 10・11・15 は入れ子のクラス。US-14 AC1・AC4）。本物のフィルターの連なりと
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

    /** 拒否で原本を保存しないことを呼び出しの記録で確かめる（Bolt 39c2 の行 1・2・5。T-236）。 */
    @MockitoSpyBean
    ExternalPayloadStorage payloadStorage;

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

    /**
     * Bolt 39c2 の行 1・2・5（取り込めない送信は本物の取込のサービスで拒まれ、取込も原本も残らない）。起きないことは取込のサービスの結果の
     * 画面と、原本の保存の spy の呼び出しの記録と、自分の情報源のイベントの発行記録が 0 件であること（T-184）で確かめる（T-236）。39c の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39c2_取り込めない送信 {

        private static final String FILE_TOO_LARGE =
                "取込ファイル: ファイルが大きすぎます（1 ファイルにつきデータ行 10,000 行・40 MB まで）。ファイルを分けて取り込んでください。";
        private static final String NOT_APPROVED = "情報源: 選んだ情報源は承認済みではないため取り込めません。画面を開き直して、承認済みの情報源を選んでください。";

        @Test
        void 行1_物理データ行10001行のファイルは上限を超える文言で拒まれ取込も原本も残らない() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            String[] rows = new String[IngestionFileLimit.MAX_DATA_LINES + 1];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = row("V-%05d".formatted(i), PORT_CALLS);
            }

            String page = refusedPage(session, file(rows));

            assertThat(page).contains(FILE_TOO_LARGE);
            verify(payloadStorage, never()).store(any(), any());
            assertThat(ingestionsOfSource()).isZero();
            assertThat(eventPublicationsOfSource()).isZero();
        }

        @Test
        void 行2_41943041バイトのファイルは上限を超える文言で拒まれ取込も原本も残らない() throws Exception {
            // 大きさの判定は行の形式より前の拒否なので、2 行目は長い値の 1 行で上限を 1 バイト超える
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            byte[] head = file(row("V-1", PORT_CALLS));
            byte[] content = new byte[IngestionFileLimit.MAX_BYTES + 1];
            java.util.Arrays.fill(content, (byte) 'x');
            System.arraycopy(head, 0, content, 0, head.length);

            String page = refusedPage(session, content);

            assertThat(page).contains(FILE_TOO_LARGE);
            verify(payloadStorage, never()).store(any(), any());
            assertThat(ingestionsOfSource()).isZero();
            assertThat(eventPublicationsOfSource()).isZero();
        }

        @Test
        void 行5_選ぶ画面を開いた後に停止された情報源の送信は承認済みでない文言で拒まれ取込も原本も残らない() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            assertThat(body(session, NEW_URL)).contains(">" + provider + "（航海）</label>");
            jdbc.update("UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?", sourceId);

            String page = refusedPage(session, file(row("V-1", PORT_CALLS)));

            assertThat(page).contains(NOT_APPROVED).doesNotContain(">" + provider + "（航海）</label>");
            verify(payloadStorage, never()).store(any(), any());
            assertThat(ingestionsOfSource()).isZero();
            assertThat(eventPublicationsOfSource()).isZero();
        }

        /** 取込の送信が選ぶ画面（200）で拒まれ、取込のサービスを 1 回呼んだことを確かめて、画面を返す。 */
        private String refusedPage(Cookie session, byte[] content) throws Exception {
            String page = postIngest(session, content)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            verify(ingestionService).ingest(any(), any());
            return page;
        }
    }

    /**
     * Bolt 39c3 の行 10・11・15（取込から結果の画面の隔離の理由まで、本物の照会と PostgreSQL で通す）。画面の注記の一覧の全体を期待の一覧と
     * 比べる（T-244）。39c・39c2 の同じ番号の行と名前が衝突しないよう入れ子にする。主要実績の情報源は提供元の名前に UUID を含めて作り、
     * 最後に停止中にする（前例は外のクラスの航海の情報源）。
     */
    @Nested
    class Bolt39c3_隔離の理由 {

        private static final String ACQUIRED = NOW.minus(Duration.ofMinutes(30)).toString();
        private static final String OCCURRED = NOW.minus(Duration.ofMinutes(60)).toString();
        private static final String MILESTONE_HEADER =
                "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";
        private static final String ASK_PROVIDER = "提供元に確かめてください。";

        private UUID milestoneSourceId;

        /** 理由と、その行の注記の全文（範囲の言葉 ＋ 理由 ＋ どうすればよいか）。 */
        record ExpectedNote(FormatDefect defect, String note) {}

        /**
         * 行の範囲を持つ理由（{@code occursIn(Extent.ROW)}）の行を 1 つずつ持つファイルと、その画面の注記の一覧（行の順）。航海のファイルの最後の
         * 行と主要実績のファイルの最後の行は正しい行で、注記を持たない（採用）。
         */
        record NotesCase(String recordType, byte[] content, List<ExpectedNote> expected) {

            @Override
            public String toString() {
                return recordType;
            }
        }

        static Stream<NotesCase> 行の範囲を持つ理由のファイルと注記() {
            return Stream.of(
                    new NotesCase(
                            "VOYAGE",
                            file(
                                    "\"V-1\"x,1," + ACQUIRED + "," + PORT_CALLS,
                                    "V-2,1\"b," + ACQUIRED + "," + PORT_CALLS,
                                    row("V-3", PORT_CALLS) + ",余分",
                                    "V-4,," + ACQUIRED + "," + PORT_CALLS,
                                    "V-5,1,2026-11-16T00:35:00," + PORT_CALLS,
                                    row("V" + "0".repeat(30), PORT_CALLS),
                                    row(
                                            "V-7",
                                            "XXXX;2026-12-01T00:00:00Z;2026-12-01T12:00:00Z|USLAX;2026-12-10T00:00:00Z;"
                                                    + "2026-12-10T12:00:00Z"),
                                    row("V-8", "JPTYO;2026-12-01T00:00:00Z;2026-12-01T12:00:00Z"),
                                    row("V-9", PORT_CALLS)),
                            List.of(
                                    new ExpectedNote(
                                            FormatDefect.TEXT_AFTER_CLOSING_QUOTE,
                                            "2 行目: 閉じた引用符の後に文字があります。" + ASK_PROVIDER),
                                    new ExpectedNote(
                                            FormatDefect.BARE_QUOTE, "3 行目: 引用符で囲まない値の中に引用符があります。引用符を含む値は引用符で囲んでください。"),
                                    new ExpectedNote(
                                            FormatDefect.COLUMN_COUNT_MISMATCH,
                                            "4 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。" + ASK_PROVIDER),
                                    new ExpectedNote(
                                            FormatDefect.EMPTY_VALUE, "5 行目: 値が空です（external_version）。" + ASK_PROVIDER),
                                    new ExpectedNote(
                                            FormatDefect.INVALID_TIME,
                                            "6 行目: 時刻の書式が違います（acquired_at）。"
                                                    + "オフセット付きの ISO 8601（例 2026-11-03T09:00:00+09:00）で書いてください。"),
                                    new ExpectedNote(
                                            FormatDefect.TOO_LONG,
                                            "7 行目: 値が長すぎます（external_id、30 文字まで）。" + ASK_PROVIDER),
                                    new ExpectedNote(
                                            FormatDefect.INVALID_LOCATION,
                                            "8 行目: 場所の形が違います（port_calls、1 つ目の寄港）。"
                                                    + "UN/LOCODE（国コード 2 文字 + 地点コード 3 文字。例 JPTYO）で書いてください。"),
                                    new ExpectedNote(
                                            FormatDefect.INVALID_PORT_CALLS,
                                            "9 行目: 寄港の列の形が違います（port_calls、寄港が足りません（2 つ以上））。" + "寄港の並びを提供元に確かめてください。"))),
                    new NotesCase(
                            "MILESTONE",
                            milestoneFile(
                                    "MS-1,1," + ACQUIRED + ",CTABCDEFGH2345,departure,JPTYO," + OCCURRED,
                                    "MS-2,1," + ACQUIRED + ",CTABCDEFGH234O,DEPARTURE,JPTYO," + OCCURRED,
                                    "MS-3,1," + ACQUIRED + ",CTABCDEFGH2347,DEPARTURE,JPTYO," + OCCURRED),
                            List.of(
                                    new ExpectedNote(
                                            FormatDefect.INVALID_KIND,
                                            "2 行目: 種類が違います（kind）。"
                                                    + "PICKUP・RECEIPT_AT_ORIGIN・DEPARTURE・TRANSSHIPMENT・ARRIVAL・DELIVERY"
                                                    + " のどれかで書いてください。"),
                                    new ExpectedNote(
                                            FormatDefect.INVALID_TRACKING_NUMBER,
                                            "3 行目: 追跡番号の形が違います（tracking_number）。"
                                                    + "CT と英大文字・数字 12 桁（0・O・1・I・L を除く）で書いてください。"))));
        }

        private static byte[] milestoneFile(String... rows) {
            return (MILESTONE_HEADER + "\n" + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
        }

        @BeforeEach
        void 承認済みの主要実績の情報源を用意する() {
            milestoneSourceId = UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO external_data.data_source (id, provider, record_type, approval_status, version)"
                            + " VALUES (?, ?, 'MILESTONE', 'APPROVED', 0)",
                    milestoneSourceId,
                    provider);
        }

        @AfterEach
        void 主要実績の情報源を承認済みのまま残さない() {
            jdbc.update(
                    "UPDATE external_data.data_source SET approval_status = 'SUSPENDED' WHERE id = ?",
                    milestoneSourceId);
            jdbc.update(
                    "DELETE FROM external_data.data_source WHERE id = ?"
                            + " AND NOT EXISTS (SELECT 1 FROM external_data.ingestion WHERE data_source_id = ?)",
                    milestoneSourceId,
                    milestoneSourceId);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("行の範囲を持つ理由のファイルと注記")
        void 行10_行の誤りの注記は理由ごとの全文で結果の画面に行の順に並ぶ(NotesCase notesCase) throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            UUID source = "VOYAGE".equals(notesCase.recordType()) ? sourceId : milestoneSourceId;

            String page = body(session, ingested(session, source, notesCase.content()));

            assertThat(notes(page))
                    .containsExactlyElementsOf(notesCase.expected().stream()
                            .map(ExpectedNote::note)
                            .toList());
        }

        @Test
        void 行10_期待の組は行の範囲を持つ理由をすべて含む() {
            // 表から作る規則の網羅（T-240）。理由を足したら期待の組にも足す
            List<FormatDefect> rowDefects = Arrays.stream(FormatDefect.values())
                    .filter(defect -> defect.occursIn(Extent.ROW))
                    .toList();

            assertThat(行の範囲を持つ理由のファイルと注記()
                            .flatMap(notesCase -> notesCase.expected().stream())
                            .map(ExpectedNote::defect))
                    .containsExactlyInAnyOrderElementsOf(rowDefects);
        }

        @Test
        void 行11_航海の情報源に主要実績のファイルを取り込むと必要な列がない注記が隔離の理由の先頭に次の行動とともに出る() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            byte[] content = milestoneFile("MS-1,1," + ACQUIRED + ",CTABCDEFGH2345,DEPARTURE,JPTYO," + OCCURRED);

            String page = body(session, ingested(session, sourceId, content));

            assertThat(notes(page))
                    .containsExactly(
                            "ファイル: 必要な列がありません（port_calls）。" + "選んだ情報源の種別に合ったファイルか確かめてください。合っていれば提供元に確かめてください。");
        }

        @Test
        void 行15_閉じ忘れで取り込めなかった部分の注記は前の行の注記の後の最後に出る() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            byte[] content = file(
                    row("V-1", PORT_CALLS) + ",余分",
                    row("V-2", PORT_CALLS),
                    "V-3,1,\"" + ACQUIRED + "," + PORT_CALLS,
                    row("V-4", PORT_CALLS));

            String page = body(session, ingested(session, sourceId, content));

            assertThat(notes(page))
                    .containsExactly(
                            "2 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。" + ASK_PROVIDER,
                            "4 行目からファイルの終わりまで: 引用符が閉じていません。2 行を取り込めませんでした。ファイルを確かめてください。");
        }

        /** 選んだ情報源で取り込み、結果の画面の URL（PRG の行き先）を返す。 */
        private String ingested(Cookie session, UUID source, byte[] content) throws Exception {
            String location = mvc.perform(((MockMultipartHttpServletRequestBuilder) multipart(INGEST_URL)
                                    .file(new MockMultipartFile("file", "file.csv", "text/csv", content))
                                    .param("dataSourceId", source.toString()))
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn()
                    .getResponse()
                    .getRedirectedUrl();
            assertThat(location).matches(INGEST_URL + "/[0-9a-f-]{36}");
            return location;
        }
    }

    // --- helpers ---

    /** 結果の画面の隔離の理由の一覧の項目（画面の順。Bolt 39c3）。領域がなければ空。 */
    private static List<String> notes(String page) {
        java.util.regex.Matcher list = java.util.regex.Pattern.compile(
                        "<ul aria-labelledby=\"quarantine-notes-heading\">(.*?)</ul>", java.util.regex.Pattern.DOTALL)
                .matcher(page);
        if (!list.find()) {
            return List.of();
        }
        java.util.regex.Matcher item = java.util.regex.Pattern.compile("<li>(.*?)</li>", java.util.regex.Pattern.DOTALL)
                .matcher(list.group(1));
        List<String> notes = new java.util.ArrayList<>();
        while (item.find()) {
            notes.add(org.springframework.web.util.HtmlUtils.htmlUnescape(item.group(1)));
        }
        return notes;
    }

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

    /**
     * 自分の情報源のイベントの発行記録の数（Bolt 39c2 の行 1・2・5。W6 の Try T-184）。取込のイベントはどれも情報源 ID を持つので、
     * 直列化したイベントの中の情報源 ID で絞る（{@link #awaitCompletedAuditPublication} の受信記録 ID の絞り方と同じ）。
     */
    private int eventPublicationsOfSource() {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.event_publication WHERE serialized_event LIKE ?",
                Integer.class,
                "%" + sourceId + "%");
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

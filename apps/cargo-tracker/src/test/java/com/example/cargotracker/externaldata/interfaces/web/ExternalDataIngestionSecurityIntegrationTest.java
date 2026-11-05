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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome.Ingested;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryView;
import com.example.cargotracker.externaldata.domain.events.ExternalRecordQuarantined;
import com.example.cargotracker.externaldata.domain.events.VoyageAdoptedValueUpdated;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalPayloadStorage;
import com.example.cargotracker.externaldata.domain.model.rules.IngestionFileLimit;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect.Extent;
import com.example.cargotracker.externaldata.interfaces.file.CsvFileKind;
import com.example.cargotracker.externaldata.interfaces.file.IngestFileCommandAssembler;
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
import com.example.cargotracker.shared.domain.AuthenticatedActor;
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
import java.util.Map;
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
 * S-16 外部データの取込のセキュリティの統合テスト（Bolt 39c の行 1・2・6・7・13・13b・15、Bolt 39c2 の行 1・2・5、Bolt 39c3 の行 10・11・15、Bolt 39c4 の行 1〜5 は入れ子のクラス。US-14 AC1・AC4）。本物のフィルターの連なりと
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

    /** 権限なしと CSRF のトークンのない送信で却下しないことを呼び出しの記録で確かめる（Bolt 39d2 の行 9。T-236）。 */
    @MockitoSpyBean
    ReceivedRecordDecisionService decisionService;

    /** まとめての却下の途中の失敗で巻き戻ることを、2 件目の追記で失敗させて確かめる（Bolt 39d2b の行 14）。 */
    @MockitoSpyBean
    com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecisionRepository decisionRepository;

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
        // 差異の行は 39h。ナビの「隔離・差異の確認」（Bolt 39d）と区別するため、表の行の見出しで見る
        assertThat(second).doesNotContain("<th scope=\"row\">差異</th>");
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
        // Bolt 39c4 の行 14 で「示された情報源から選び直してください。」の形に直した（【前の決定の変更】(1)）
        private static final String NOT_APPROVED = "情報源: 選んだ情報源は承認済みではないため取り込めません。示された情報源から選び直してください。";

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

    /**
     * Bolt 39c4 の行 1〜5（要確認の復元。開始準備の決定 (2)・(3)）。取込のサービスで取り込み、取込の要約の照会で DB から読み直した要確認と
     * 取り込めなかった部分が、取込のサービスの結果（{@code requiresReview()} と取り込めなかった部分の始まり・数）と一致することを、取込の種類と
     * 期待の値の組で確かめる（T-244）。39c・39c2・39c3 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39c4_要確認の復元 {

        private static final String ACQUIRED = NOW.minus(Duration.ofMinutes(30)).toString();
        private static final String MILESTONE_HEADER =
                "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";

        @Autowired
        IngestionSummaryQueryService summaries;

        /** 取込の種類と、読み直した取り込めなかった部分の期待（要確認でなければ {@code null}）。 */
        record ReviewCase(String label, byte[] content, IngestionSummaryView.UnreadableRegion expected) {

            @Override
            public String toString() {
                return label;
            }
        }

        static Stream<ReviewCase> 取込の種類と取り込めなかった部分() {
            return Stream.of(
                    new ReviewCase("行1_形式の正しいファイル", file(row("V-1", PORT_CALLS), row("V-2", PORT_CALLS)), null),
                    new ReviewCase(
                            "行2_行の誤りだけ（列数の誤りを含む）",
                            file(
                                    row("V-1", PORT_CALLS) + ",余分",
                                    "V-2,," + ACQUIRED + "," + PORT_CALLS,
                                    row("V-3", PORT_CALLS)),
                            null),
                    new ReviewCase(
                            "行3_ファイルの誤り（データ行あり。必要な列がない）",
                            (MILESTONE_HEADER + "\n"
                                            + "MS-1,1," + ACQUIRED + ",CTABCDEFGH2345,DEPARTURE,JPTYO," + ACQUIRED
                                            + "\n"
                                            + "MS-2,1," + ACQUIRED + ",CTABCDEFGH2347,DEPARTURE,JPTYO," + ACQUIRED
                                            + "\n")
                                    .getBytes(StandardCharsets.UTF_8),
                            new IngestionSummaryView.UnreadableRegion(2, 2, true)),
                    new ReviewCase(
                            "行4_ファイルの誤り（データ行 0。0 バイト）",
                            new byte[0],
                            new IngestionSummaryView.UnreadableRegion(null, 0, true)),
                    new ReviewCase(
                            "行5_閉じ忘れ（前に正しい行と列数の誤りの行）",
                            file(
                                    row("V-1", PORT_CALLS),
                                    row("V-2", PORT_CALLS) + ",余分",
                                    "V-3,1,\"" + ACQUIRED + "," + PORT_CALLS,
                                    row("V-4", PORT_CALLS)),
                            new IngestionSummaryView.UnreadableRegion(4, 2, false)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("取込の種類と取り込めなかった部分")
        void 行1から行5_読み直した要確認と取り込めなかった部分は取込のサービスの結果と一致する(ReviewCase reviewCase) {
            AuthenticatedActor steward = new AuthenticatedActor(
                    new UserId(UUID.randomUUID()), operator.id(), Set.of(Role.DATA_STEWARD), "データ責任者", "A 社");
            Ingested outcome = (Ingested) ingestionService.ingest(
                    steward, IngestFileCommandAssembler.assemble(sourceId, CsvFileKind.VOYAGE, reviewCase.content()));

            IngestionSummaryView summary =
                    summaries.summaryOf(outcome.ingestionId()).orElseThrow();

            boolean expectedReview = reviewCase.expected() != null;
            assertThat(outcome.requiresReview()).as("取込のサービスの要確認").isEqualTo(expectedReview);
            assertThat(summary.requiresReview()).as("読み直した要確認").isEqualTo(outcome.requiresReview());
            assertThat(summary.unreadable()).isEqualTo(reviewCase.expected());
            if (expectedReview) {
                assertThat(summary.unreadable().fromLine())
                        .isEqualTo(outcome.unreadable().fromLine());
                assertThat(summary.unreadable().lineCount())
                        .isEqualTo(outcome.unreadable().lineCount());
            }
        }
    }

    /**
     * Bolt 39d の行 1・2・6・16（S-17 の判断を待つ隔離の一覧の認可と DB の通し）。取込のサービスで画面から取り込み、S-16 の結果の画面の入口と
     * 同じ取込で絞った S-17 を開いて、内容の違う原本と形式の誤りが種類と外部 ID・外部版とともにファイルの行の順に出ること、ほかの取込の隔離が
     * 出ないことを確かめる。S-17 は今の {@code /staff/external-data/**} の規則のまま、データ責任者だけが開ける。39c〜39c4 の同じ番号の行と
     * 名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d_判断を待つ隔離の一覧 {

        private static final String LIST_URL = "/staff/external-data/undecided-records";

        @Test
        void 行1_行2_行6_取込で絞った一覧に内容の違う原本と形式の誤りが行の順に出てほかの取込の隔離は出ない() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            String first = ingested(
                    session,
                    file(row("V-1", PORT_CALLS), row("V-1", CHANGED_PORT_CALLS), row("V-2", PORT_CALLS) + ",余分"));
            String other = ingested(session, file(row("V-9", PORT_CALLS), row("V-9", CHANGED_PORT_CALLS)));
            UUID ingestionId = UUID.fromString(first.substring(first.lastIndexOf('/') + 1));
            String entry = LIST_URL + "?ingestion=" + ingestionId;
            assertThat(body(session, first)).contains("href=\"" + entry + "\"");
            assertThat(other).isNotEqualTo(first);

            String page = body(session, entry);

            assertThat(page).contains("<p>この取込の判断を待つ隔離 2 件（1〜2 件目を示しています）</p>").doesNotContain("V-9");
            assertThat(rows(page))
                    .containsExactly(
                            List.of(
                                    provider + "（航海）",
                                    "3 行目",
                                    "内容の違う原本",
                                    "V-1・1",
                                    "3 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-16 01:05:00 UTC）。"),
                            List.of(
                                    provider + "（航海）",
                                    "4 行目",
                                    "形式の誤り",
                                    "—なし",
                                    "4 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。提供元に確かめてください。"));
        }

        @Test
        void 行16_ほかの7つの役割はS17で403になり未ログインはA01へ移る() throws Exception {
            for (Role role : EnumSet.complementOf(EnumSet.of(Role.DATA_STEWARD))) {
                Cookie session = login(user(Set.of(role)));

                mvc.perform(get(LIST_URL).cookie(session)).andExpect(status().isForbidden());
            }
            assertThat(mvc.perform(get(LIST_URL)).andReturn().getResponse().getRedirectedUrl())
                    .endsWith("/login");
        }

        @Test
        void 行16_データ責任者はS17を開ける() throws Exception {
            mvc.perform(get(LIST_URL).cookie(login(user(Set.of(Role.DATA_STEWARD)))))
                    .andExpect(status().isOk());
        }

        /** 一覧の表の行ごとの、取り込んだ日時を除く項目の文字（タグを除き、HTML の文字参照を戻す）。 */
        private List<List<String>> rows(String page) {
            java.util.regex.Matcher tbody = java.util.regex.Pattern.compile(
                            "<tbody>(.*?)</tbody>", java.util.regex.Pattern.DOTALL)
                    .matcher(page);
            assertThat(tbody.find()).as("一覧の表").isTrue();
            java.util.regex.Matcher row = java.util.regex.Pattern.compile(
                            "<tr>(.*?)</tr>", java.util.regex.Pattern.DOTALL)
                    .matcher(tbody.group(1));
            List<List<String>> rows = new java.util.ArrayList<>();
            while (row.find()) {
                java.util.regex.Matcher cell = java.util.regex.Pattern.compile(
                                "<td>(.*?)</td>", java.util.regex.Pattern.DOTALL)
                        .matcher(row.group(1));
                List<String> cells = new java.util.ArrayList<>();
                while (cell.find()) {
                    cells.add(org.springframework.web.util.HtmlUtils.htmlUnescape(
                            cell.group(1).replaceAll("<[^>]+>", "")));
                }
                // 取り込んだ日時と、Bolt 39d2 で足した判断の列（「判断する」のリンク）を除く
                rows.add(cells.subList(1, cells.size() - 1));
            }
            return rows;
        }
    }

    /**
     * Bolt 39d2 の行 1・9（S-17 の却下。本物のフィルターの連なりと PostgreSQL の上で）。データ責任者が判断の画面から却下すると PRG で元の取込の
     * 一覧に戻り、判断の表に判断者と理由が残り、その隔離は一覧から消える。判断の画面と却下の POST は今の {@code /staff/external-data/**} の
     * 規則のまま、ほかの役割は 403、未ログインは A-01、CSRF のトークンのない POST は却下しない。39c〜39d の同じ番号の行と名前が衝突しないよう
     * 入れ子にする。
     */
    @Nested
    class Bolt39d2_隔離の却下 {

        private static final String LIST_URL = "/staff/external-data/undecided-records";

        /** 内容の違う原本 1 つを取り込み、その受信記録 ID と取込 ID を返す。 */
        private UUID[] quarantined(Cookie session) throws Exception {
            String location = ingested(session, file(row("V-1", PORT_CALLS), row("V-1", CHANGED_PORT_CALLS)));
            UUID ingestionId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
            return new UUID[] {receivedRecordOf(ingestionId, "QUARANTINED"), ingestionId};
        }

        private int decisionsOf(UUID receivedRecordId) {
            Integer value = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM external_data.received_record_decision WHERE received_record_id = ?",
                    Integer.class,
                    receivedRecordId);
            return value == null ? 0 : value;
        }

        @Test
        void 行1_データ責任者は判断の画面から却下すると元の取込の一覧に戻り判断が残り隔離は一覧から消える() throws Exception {
            String email = user(Set.of(Role.DATA_STEWARD));
            Cookie session = login(email);
            UUID[] ids = quarantined(session);
            UUID receivedRecordId = ids[0];
            String entry = LIST_URL + "?ingestion=" + ids[1] + "&page=1";
            String decisionUrl = LIST_URL + "/" + receivedRecordId;
            assertThat(body(session, LIST_URL + "?ingestion=" + ids[1]))
                    .contains("href=\"" + decisionUrl + "?ingestion=" + ids[1] + "&amp;page=1\"");
            assertThat(body(session, decisionUrl + "?ingestion=" + ids[1] + "&page=1"))
                    .contains("<h1>隔離の判断</h1>");

            MvcResult rejected = mvc.perform(post(decisionUrl + "/decision?ingestion=" + ids[1] + "&page=1")
                            .param("reason", "提供元に確かめ、誤って送った原本と分かった。")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(rejected.getResponse().getRedirectedUrl()).isEqualTo(entry);
            assertThat((java.util.Map<String, Object>) rejected.getFlashMap()).containsEntry("result", "隔離を却下しました。");
            assertThat(body(session, entry)).contains("<p>この取込に判断を待つ隔離はありません。</p>");
            assertThat(jdbc.queryForMap(
                            "SELECT decision, reason FROM external_data.received_record_decision"
                                    + " WHERE received_record_id = ?",
                            receivedRecordId))
                    .containsEntry("decision", "REJECTED")
                    .containsEntry("reason", "提供元に確かめ、誤って送った原本と分かった。");
            // 判断済みの隔離の判断の画面と 2 度目の送信は、一覧に戻って判断済みと知らせる（行 5）
            MvcResult again = mvc.perform(post(decisionUrl + "/decision")
                            .param("reason", "二重の送信")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat((java.util.Map<String, Object>) again.getFlashMap()).containsEntry("result", "この隔離は判断済みです。");
            assertThat(decisionsOf(receivedRecordId)).isEqualTo(1);
        }

        @Test
        void 行9_ほかの7つの役割は判断の画面と却下で403になり却下しない() throws Exception {
            UUID receivedRecordId = quarantined(login(user(Set.of(Role.DATA_STEWARD))))[0];
            String decisionUrl = LIST_URL + "/" + receivedRecordId;
            for (Role role : EnumSet.complementOf(EnumSet.of(Role.DATA_STEWARD))) {
                Cookie session = login(user(Set.of(role)));

                mvc.perform(get(decisionUrl).cookie(session)).andExpect(status().isForbidden());
                mvc.perform(post(decisionUrl + "/decision")
                                .param("reason", "理由")
                                .cookie(session)
                                .with(csrf()))
                        .andExpect(status().isForbidden());
            }
            verify(decisionService, never()).reject(any(), any());
            assertThat(decisionsOf(receivedRecordId)).isZero();
        }

        @Test
        void 行9_未ログインは判断の画面と却下でA01へ移り却下しない() throws Exception {
            UUID receivedRecordId = quarantined(login(user(Set.of(Role.DATA_STEWARD))))[0];
            String decisionUrl = LIST_URL + "/" + receivedRecordId;

            assertThat(mvc.perform(get(decisionUrl)).andReturn().getResponse().getRedirectedUrl())
                    .endsWith("/login");
            assertThat(mvc.perform(post(decisionUrl + "/decision")
                                    .param("reason", "理由")
                                    .with(csrf()))
                            .andReturn()
                            .getResponse()
                            .getRedirectedUrl())
                    .endsWith("/login");
            verify(decisionService, never()).reject(any(), any());
            assertThat(decisionsOf(receivedRecordId)).isZero();
        }

        @Test
        void 行9_CSRFのトークンのない却下の送信は拒まれ却下のサービスを呼ばない() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            UUID receivedRecordId = quarantined(session)[0];
            String decisionUrl = LIST_URL + "/" + receivedRecordId;

            MvcResult rejected = mvc.perform(post(decisionUrl + "/decision")
                            .param("reason", "理由")
                            .cookie(session))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(rejected.getResponse().getRedirectedUrl()).doesNotStartWith(LIST_URL);
            verify(decisionService, never()).reject(any(), any());
            assertThat(decisionsOf(receivedRecordId)).isZero();
        }
    }

    /**
     * Bolt 39d3b の行 22（S-17 の採用の POST。本物のフィルターの連なりの上で）。データ責任者が CSRF のトークン付きで航海の内容の違う原本の隔離を
     * 採用すると、PRG で元の取込の一覧に戻り、判断（{@code ADOPTED}）と採用値が 1 行ずつ残る。採用の POST は却下と同じ
     * {@code /staff/external-data/**} の規則の中で、ほかの役割は 403、CSRF のトークンのない POST は採用しない（今の却下と同じく拒否の行き先へ移る）。
     * 39c〜39d2b の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d3b_隔離の採用 {

        private static final String LIST_URL = "/staff/external-data/undecided-records";

        /** 航海の内容の違う原本 1 つを取り込み、その受信記録 ID と取込 ID を返す。 */
        private UUID[] quarantined(Cookie session) throws Exception {
            String location = ingested(session, file(row("V-1", PORT_CALLS), row("V-1", CHANGED_PORT_CALLS)));
            UUID ingestionId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
            return new UUID[] {receivedRecordOf(ingestionId, "QUARANTINED"), ingestionId};
        }

        private int countOf(String table, UUID receivedRecordId) {
            Integer value = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM external_data." + table + " WHERE received_record_id = ?",
                    Integer.class,
                    receivedRecordId);
            return value == null ? 0 : value;
        }

        @Test
        void 行22_データ責任者はCSRFのトークン付きで採用すると元の取込の一覧に戻り判断と採用値が1行ずつ残る() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            UUID[] ids = quarantined(session);
            UUID receivedRecordId = ids[0];
            String entry = LIST_URL + "?ingestion=" + ids[1] + "&page=1";
            String adoptionUrl = LIST_URL + "/" + receivedRecordId + "/adoption";

            MvcResult adopted = mvc.perform(post(adoptionUrl + "?ingestion=" + ids[1] + "&page=1")
                            .param("reason", "")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(adopted.getResponse().getRedirectedUrl()).isEqualTo(entry);
            assertThat((Map<String, Object>) adopted.getFlashMap()).containsEntry("result", "隔離を採用しました。");
            assertThat(jdbc.queryForMap(
                            "SELECT decision, reason FROM external_data.received_record_decision"
                                    + " WHERE received_record_id = ?",
                            receivedRecordId))
                    .containsEntry("decision", "ADOPTED")
                    .containsEntry("reason", null);
            assertThat(countOf("adopted_value", receivedRecordId)).isEqualTo(1);
        }

        @Test
        void 行22_ほかの7つの役割は採用のPOSTで403になり判断も採用値も書かない() throws Exception {
            UUID receivedRecordId = quarantined(login(user(Set.of(Role.DATA_STEWARD))))[0];
            String adoptionUrl = LIST_URL + "/" + receivedRecordId + "/adoption";
            for (Role role : EnumSet.complementOf(EnumSet.of(Role.DATA_STEWARD))) {
                Cookie session = login(user(Set.of(role)));

                mvc.perform(post(adoptionUrl).param("reason", "理由").cookie(session).with(csrf()))
                        .andExpect(status().isForbidden());
            }
            verify(decisionService, never()).adopt(any(), any());
            assertThat(countOf("received_record_decision", receivedRecordId)).isZero();
            assertThat(countOf("adopted_value", receivedRecordId)).isZero();
        }

        @Test
        void 行22_CSRFのトークンのない採用の送信は拒まれ採用のサービスを呼ばない() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            UUID receivedRecordId = quarantined(session)[0];

            MvcResult refused = mvc.perform(post(LIST_URL + "/" + receivedRecordId + "/adoption")
                            .param("reason", "理由")
                            .cookie(session))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(refused.getResponse().getRedirectedUrl()).doesNotStartWith(LIST_URL);
            verify(decisionService, never()).adopt(any(), any());
            assertThat(countOf("received_record_decision", receivedRecordId)).isZero();
            assertThat(countOf("adopted_value", receivedRecordId)).isZero();
        }
    }

    /**
     * Bolt 39d2b の行 4・8・11・14（S-17 の形式の誤りのまとめての却下。本物のフィルターの連なりと PostgreSQL の上で）。データ責任者が取込で
     * 絞った一覧のリンクからまとめての却下の画面を開いて却下すると、PRG で取込で絞った一覧に戻り、その取込の形式の誤りごとに同じ判断者・
     * 時刻・理由の判断が残り、内容の違う原本は残る。まとめての却下の画面と POST は今の {@code /staff/external-data/**} の規則のまま、ほかの役割は
     * 403、未ログインは A-01、CSRF のトークンのない POST は却下しない。途中の追記の失敗はトランザクションごと巻き戻る。39c〜39d2 の同じ番号の
     * 行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d2b_形式の誤りのまとめての却下 {

        private static final String LIST_URL = "/staff/external-data/undecided-records";
        private static final String REASON = "列の数を直したファイルを取り込み直した。";

        /** 形式の誤り 2 行と内容の違う原本 1 行を含むファイルを取り込み、取込 ID を返す（T-230）。 */
        private UUID ingestion(Cookie session) throws Exception {
            String location = ingested(
                    session,
                    file(
                            row("V-1", PORT_CALLS),
                            row("V-1", CHANGED_PORT_CALLS),
                            row("V-2", PORT_CALLS) + ",余分",
                            row("V-3", PORT_CALLS) + ",余分"));
            return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        }

        private int decisionsOfIngestion(UUID ingestionId) {
            Integer value = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM external_data.received_record_decision d"
                            + " JOIN external_data.received_record r ON r.id = d.received_record_id WHERE r.ingestion_id = ?",
                    Integer.class,
                    ingestionId);
            return value == null ? 0 : value;
        }

        private String screenUrl(UUID ingestionId) {
            return LIST_URL + "/keyless-rejection?ingestion=" + ingestionId;
        }

        @Test
        void 行4_データ責任者は一覧のリンクからまとめて却下すると取込で絞った一覧に戻り形式の誤りごとに同じ判断が残る() throws Exception {
            String email = user(Set.of(Role.DATA_STEWARD));
            Cookie session = login(email);
            UUID ingestionId = ingestion(session);
            String entry = LIST_URL + "?ingestion=" + ingestionId + "&page=1";
            assertThat(body(session, LIST_URL + "?ingestion=" + ingestionId))
                    .contains("<a href=\"" + screenUrl(ingestionId) + "\">この取込の形式の誤りをまとめて却下する（2 件）</a>");
            assertThat(body(session, screenUrl(ingestionId)))
                    .contains("<h1>形式の誤りのまとめての却下</h1>")
                    .containsPattern("<dt>形式の誤り</dt>\\s*<dd>2 件</dd>");

            MvcResult rejected = mvc.perform(post(screenUrl(ingestionId))
                            .param("reason", REASON)
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(rejected.getResponse().getRedirectedUrl()).isEqualTo(entry);
            assertThat((java.util.Map<String, Object>) rejected.getFlashMap())
                    .containsEntry("result", "形式の誤り 2 件を却下しました。");
            assertThat(body(session, entry))
                    .contains("<p>この取込の判断を待つ隔離 1 件（1〜1 件目を示しています）</p>")
                    .doesNotContain("まとめて却下する");
            List<java.util.Map<String, Object>> decisions = jdbc.queryForList(
                    "SELECT d.decision, d.reason, d.decided_by, d.decided_at FROM external_data.received_record_decision d"
                            + " JOIN external_data.received_record r ON r.id = d.received_record_id"
                            + " WHERE r.ingestion_id = ? AND r.external_record_id IS NULL",
                    ingestionId);
            assertThat(decisions).hasSize(2);
            // 判断者・時刻・理由は全件で同じ
            assertThat(decisions.stream()
                            .map(decision -> List.of(
                                    decision.get("decision"),
                                    decision.get("reason"),
                                    decision.get("decided_by"),
                                    decision.get("decided_at")))
                            .distinct())
                    .hasSize(1);
            assertThat(decisions.getFirst())
                    .containsEntry("decision", "REJECTED")
                    .containsEntry("reason", REASON);
            assertThat(decisionsOfIngestion(ingestionId)).isEqualTo(2);
            // 二重の送信は判断を作らず、まとめて却下できる形式の誤りはないと知らせる（行 9）
            MvcResult again = mvc.perform(post(screenUrl(ingestionId))
                            .param("reason", "二重の送信")
                            .cookie(session)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat((java.util.Map<String, Object>) again.getFlashMap())
                    .containsEntry("result", "この取込にまとめて却下できる形式の誤りはありません。");
            assertThat(decisionsOfIngestion(ingestionId)).isEqualTo(2);
        }

        @Test
        void 行11_ほかの7つの役割はまとめての却下の画面とPOSTで403になり却下しない() throws Exception {
            UUID ingestionId = ingestion(login(user(Set.of(Role.DATA_STEWARD))));
            for (Role role : EnumSet.complementOf(EnumSet.of(Role.DATA_STEWARD))) {
                Cookie session = login(user(Set.of(role)));

                mvc.perform(get(screenUrl(ingestionId)).cookie(session)).andExpect(status().isForbidden());
                mvc.perform(post(screenUrl(ingestionId))
                                .param("reason", REASON)
                                .cookie(session)
                                .with(csrf()))
                        .andExpect(status().isForbidden());
            }
            verify(decisionService, never()).rejectKeyless(any(), any());
            assertThat(decisionsOfIngestion(ingestionId)).isZero();
        }

        @Test
        void 行11_未ログインはまとめての却下の画面とPOSTでA01へ移り却下しない() throws Exception {
            UUID ingestionId = ingestion(login(user(Set.of(Role.DATA_STEWARD))));

            assertThat(mvc.perform(get(screenUrl(ingestionId)))
                            .andReturn()
                            .getResponse()
                            .getRedirectedUrl())
                    .endsWith("/login");
            assertThat(mvc.perform(post(screenUrl(ingestionId))
                                    .param("reason", REASON)
                                    .with(csrf()))
                            .andReturn()
                            .getResponse()
                            .getRedirectedUrl())
                    .endsWith("/login");
            verify(decisionService, never()).rejectKeyless(any(), any());
            assertThat(decisionsOfIngestion(ingestionId)).isZero();
        }

        @Test
        void 行11_CSRFのトークンのないまとめての却下の送信は拒まれ却下のサービスを呼ばない() throws Exception {
            Cookie session = login(user(Set.of(Role.DATA_STEWARD)));
            UUID ingestionId = ingestion(session);

            MvcResult rejected = mvc.perform(
                            post(screenUrl(ingestionId)).param("reason", REASON).cookie(session))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            assertThat(rejected.getResponse().getRedirectedUrl()).doesNotStartWith(LIST_URL);
            verify(decisionService, never()).rejectKeyless(any(), any());
            assertThat(decisionsOfIngestion(ingestionId)).isZero();
        }

        @Test
        void 行14_2件目の追記がDBの失敗ならトランザクションごと巻き戻り判断もDE29も残らない() throws Exception {
            UUID ingestionId = ingestion(login(user(Set.of(Role.DATA_STEWARD))));
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(invocation -> {
                        if (calls.incrementAndGet() == 2) {
                            throw new org.springframework.dao.DataAccessResourceFailureException("2 件目の追記の失敗");
                        }
                        return invocation.callRealMethod();
                    })
                    .when(decisionRepository)
                    .add(any());
            AuthenticatedActor steward = new AuthenticatedActor(
                    new UserId(UUID.randomUUID()), operator.id(), Set.of(Role.DATA_STEWARD), "データ責任者", "A 社");
            com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand
                    command =
                            new com.example.cargotracker.externaldata.application.internal.commands
                                    .RejectKeylessQuarantinesCommand(ingestionId, REASON);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> decisionService.rejectKeyless(steward, command))
                    .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(decisionsOfIngestion(ingestionId)).isZero();
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM platform.event_publication WHERE event_type = ? AND serialized_event LIKE ?",
                            Integer.class,
                            com.example.cargotracker.externaldata.domain.events.ReceivedRecordDecided.class.getName(),
                            "%" + ingestionId + "%"))
                    .isZero();
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

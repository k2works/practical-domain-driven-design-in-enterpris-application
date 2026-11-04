package com.example.cargotracker.externaldata.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.IngestionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestibleDataSource;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestibleDataSourceQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryQueryService;
import com.example.cargotracker.externaldata.application.internal.queryservices.IngestionSummaryView;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.identity.infrastructure.security.TestActors;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * S-16 外部データの取込（データ責任者。US-14 AC1・AC2。Bolt 39c の行 3〜6・14、Bolt 39c2 の行 1・5〜8、Bolt 39c3 の行 13・16〜18、Bolt 39c4 の行 8・9・14・15・19 は入れ子のクラス）。取込のサービスと照会は mock にし、画面の振る舞い
 * （選ぶ画面、画面で見る誤り、PRG と知らせ、結果の画面、404）だけを見る。認証・認可・CSRF はセキュリティの統合テストで確かめる。
 * 「起きないこと」（サービスを呼ばない）は呼び出しの記録で確かめる（T-236）。
 */
@WithAuthenticatedActor(Role.DATA_STEWARD)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = ExternalDataIngestionController.class)
class ExternalDataIngestionControllerTest {

    private static final String NEW_URL = "/staff/external-data/ingestions/new";
    private static final String INGEST_URL = "/staff/external-data/ingestions";
    private static final String COMMON_REFUSAL = "取り込めませんでした。情報源とファイルを確かめてください。";
    private static final String NO_SOURCE = "取り込める情報源がありません。承認済みの情報源がありません。情報源を選定し、承認してください。" + "承認は運用の手順で行います。";
    private static final String LIMIT = "1 ファイルにつきデータ行 10,000 行・40 MB まで。超えるときはファイルを分けて取り込んでください。";
    // 情報源の拒否の 2 つの文言（Bolt 39c2 の行 5・6 と Bolt 39c4 の行 14・15・19 で使う）。Bolt 39c4 で「示された情報源から選び直してください。」の形に直した（【前の決定の変更】(1)）
    private static final String NOT_APPROVED = "選んだ情報源は承認済みではないため取り込めません。示された情報源から選び直してください。";
    private static final String NOT_FOUND = "選んだ情報源が見つかりません。示された情報源から選び直してください。";
    /** 0 件の全文の段落。エラー要約のリンクの先なので、フォーカスを受けられる（tabindex="-1"）。 */
    private static final String NO_SOURCE_PARAGRAPH = "<p id=\"no-source\" tabindex=\"-1\">";

    private static final UUID VOYAGE_SOURCE = UUID.fromString("39c00000-0000-0000-0000-000000000001");
    private static final UUID MILESTONE_SOURCE = UUID.fromString("39c00000-0000-0000-0000-000000000002");
    private static final UUID INGESTION_ID = UUID.fromString("39c00000-0000-0000-0000-0000000000aa");

    private static final String VOYAGE_CSV = """
            external_id,external_version,acquired_at,port_calls
            V2611E,1,2026-11-16T00:30:00Z,JPTYO;2026-12-01T00:00:00Z;2026-12-01T12:00:00Z|USLAX;2026-12-10T00:00:00Z;2026-12-10T12:00:00Z
            """;

    private static final String MILESTONE_CSV = """
            external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at
            MS-0001,1,2026-11-16T00:30:00Z,CTABCDEFGH2345,DEPARTURE,JPTYO,2026-11-16T00:00:00Z
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IngestionService ingestionService;

    @MockitoBean
    IngestibleDataSourceQueryService dataSources;

    @MockitoBean
    IngestionSummaryQueryService summaries;

    @BeforeEach
    void 承認済みの情報源が2つある() {
        given(dataSources.ingestibleSources())
                .willReturn(List.of(
                        new IngestibleDataSource(VOYAGE_SOURCE, "開発海運（架空）", "VOYAGE"),
                        new IngestibleDataSource(MILESTONE_SOURCE, "開発海運（架空）", "MILESTONE")));
        given(dataSources.recordTypeOf(VOYAGE_SOURCE)).willReturn(Optional.of("VOYAGE"));
        given(dataSources.recordTypeOf(MILESTONE_SOURCE)).willReturn(Optional.of("MILESTONE"));
    }

    private static MockMultipartHttpServletRequestBuilder ingest(UUID sourceId, String fileName, String content) {
        MockMultipartHttpServletRequestBuilder request = multipart(INGEST_URL)
                .file(new MockMultipartFile("file", fileName, "text/csv", content.getBytes(StandardCharsets.UTF_8)));
        if (sourceId != null) {
            request.param("dataSourceId", sourceId.toString());
        }
        return request;
    }

    private static IngestionOutcome.Ingested ingested() {
        return new IngestionOutcome.Ingested(INGESTION_ID, 1, Map.of(), List.of(), null);
    }

    // 選ぶ画面（GET）

    @Test
    void 行4_選ぶ画面に承認済みの情報源のラジオと取込ファイルの欄と上限の説明と取り込むボタンを示す() throws Exception {
        mockMvc.perform(get(NEW_URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>外部データの取込</h1>")))
                .andExpect(content().string(containsString("<legend>情報源</legend>")))
                .andExpect(content().string(containsString("value=\"" + VOYAGE_SOURCE + "\"")))
                .andExpect(content().string(containsString(">開発海運（架空）（航海）</label>")))
                .andExpect(content().string(containsString("value=\"" + MILESTONE_SOURCE + "\"")))
                .andExpect(content().string(containsString(">開発海運（架空）（主要実績）</label>")))
                .andExpect(content().string(containsString("<label for=\"file\">取込ファイル（CSV、UTF-8）</label>")))
                .andExpect(content().string(containsString("accept=\".csv,text/csv\"")))
                .andExpect(content().string(containsString(LIMIT)))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString(">取り込む</button>")))
                .andExpect(content().string(not(containsString(NO_SOURCE))))
                .andExpect(content().string(containsString("/js/transport-request-form.js")));
    }

    @Test
    void 行4_承認済みの情報源がないと全文の文言を示しファイルの欄と取り込むボタンを出さない() throws Exception {
        // US-14 AC2。共有の PostgreSQL では 0 件の状態を作れないので、照会を mock して確かめる
        given(dataSources.ingestibleSources()).willReturn(List.of());

        mockMvc.perform(get(NEW_URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(NO_SOURCE)))
                .andExpect(content().string(not(containsString("type=\"file\""))))
                .andExpect(content().string(not(containsString(">取り込む</button>"))))
                .andExpect(content().string(not(containsString("<legend>情報源</legend>"))));
    }

    @Test
    void 行13_選ぶ画面のナビに外部データの取込が現在の項目として出る() throws Exception {
        mockMvc.perform(get(NEW_URL)).andExpect(content().string(containsPattern(CURRENT_NAV_ITEM)));
    }

    /** ナビの「外部データの取込」が現在の項目（太字と aria-current）であること。属性の順に依らない。 */
    private static final String CURRENT_NAV_ITEM =
            "class=\"nav-link fw-bold\" href=\"/staff/external-data/ingestions/new\" aria-current=\"page\">外部データの取込</a>";

    private static org.hamcrest.Matcher<String> containsPattern(String regex) {
        return matchesPattern(Pattern.compile("(?s).*" + regex + ".*"));
    }

    // 画面で見る誤り（サービスを呼ばない）

    @Test
    void 行3_ファイルを選ばずに送るとエラー要約で直し方を示し選んだ情報源を戻しサービスを呼ばない() throws Exception {
        mockMvc.perform(ingest(VOYAGE_SOURCE, "", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")))
                .andExpect(content().string(containsString("<h2 id=\"error-summary-title\">取り込めませんでした</h2>")))
                .andExpect(content().string(containsString("href=\"#file\">取込ファイル: 取り込むファイルを選んでください。</a>")))
                .andExpect(content().string(not(containsString("取り込む情報源を 1 つ選んでください。"))))
                .andExpect(content().string(containsPattern("value=\"" + VOYAGE_SOURCE + "\"[^>]*checked=\"checked\"")))
                .andExpect(content().string(containsString("<title>取り込めませんでした - 外部データの取込 - cargo-tracker</title>")));

        then(ingestionService).should(never()).ingest(any(), any());
    }

    @Test
    void 行3_情報源を選ばずに送るとエラー要約で直し方を示しサービスを呼ばない() throws Exception {
        mockMvc.perform(ingest(null, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: 取り込む情報源を 1 つ選んでください。</a>")))
                .andExpect(content().string(not(containsString("取り込むファイルを選んでください。"))));

        then(ingestionService).should(never()).ingest(any(), any());
    }

    @Test
    void 行3_情報源もファイルも選ばずに送ると2件の誤りを示しサービスを呼ばない() throws Exception {
        mockMvc.perform(ingest(null, "", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: 取り込む情報源を 1 つ選んでください。</a>")))
                .andExpect(content().string(containsString("href=\"#file\">取込ファイル: 取り込むファイルを選んでください。</a>")));

        then(ingestionService).should(never()).ingest(any(), any());
    }

    // 欄ごとの誤りの示し方（社内の S-01 の企業の欄と同じ形。Bolt 39c ステップ 6）

    @Test
    void 行3_誤りのない選ぶ画面はどの欄にもaria_invalidを付けない() throws Exception {
        mockMvc.perform(get(NEW_URL)).andExpect(content().string(not(containsString("aria-invalid"))));
    }

    @Test
    void 行3_情報源の誤りは凡例の直後に赤字で示し誤りのないファイルの欄にはaria_invalidを付けない() throws Exception {
        mockMvc.perform(ingest(null, "voyage.csv", VOYAGE_CSV))
                .andExpect(content()
                        .string(containsPattern("<fieldset[^>]*aria-invalid=\"true\"[^>]*>\\s*<legend>情報源</legend>\\s*"
                                + "<p id=\"dataSourceId-error\" class=\"text-danger\">取り込む情報源を 1 つ選んでください。</p>")))
                .andExpect(content().string(containsPattern("<input id=\"file\"(?![^>]*aria-invalid)[^>]*>")));
    }

    @Test
    void 行3_ファイルの誤りは赤字で示し誤りのない情報源の欄にはaria_invalidを付けない() throws Exception {
        mockMvc.perform(ingest(VOYAGE_SOURCE, "", ""))
                .andExpect(content()
                        .string(containsString("<p id=\"file-error\" class=\"text-danger\">取り込むファイルを選んでください。</p>")))
                .andExpect(content().string(containsPattern("<input id=\"file\"[^>]*aria-invalid=\"true\"[^>]*>")))
                .andExpect(content().string(containsPattern("<fieldset(?![^>]*aria-invalid)[^>]*>")));
    }

    // 拒否（39c はエラー要約を示すこと。理由ごとの文言は下の Bolt39c2_取り込めない送信。Bolt 39c2 ステップ 2 で共通の文言の確かめを外した）

    @Test
    void 行5_承認されていない情報源の送信はエラー要約を示す() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotApproved());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")));
    }

    @Test
    void 行5_上限を超えるファイルはエラー要約を示す() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.FileTooLarge());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")));
    }

    @Test
    void 行5_サービスがない情報源を返すとエラー要約を示す() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotFound());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")));
    }

    @Test
    void 行5_ない情報源の送信は原本種別を引けないのでエラー要約を示しサービスを呼ばない() throws Exception {
        UUID unknown = UUID.randomUUID();
        given(dataSources.recordTypeOf(unknown)).willReturn(Optional.empty());

        mockMvc.perform(ingest(unknown, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")));

        then(ingestionService).should(never()).ingest(any(), any());
    }

    @Test
    void 行5_形の誤った情報源IDの送信はエラー要約を示しサービスを呼ばない() throws Exception {
        mockMvc.perform(malformedSource())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")));

        then(ingestionService).should(never()).ingest(any(), any());
    }

    private static MockMultipartHttpServletRequestBuilder malformedSource() {
        return (MockMultipartHttpServletRequestBuilder) multipart(INGEST_URL)
                .file(new MockMultipartFile(
                        "file", "voyage.csv", "text/csv", VOYAGE_CSV.getBytes(StandardCharsets.UTF_8)))
                .param("dataSourceId", "not-a-uuid");
    }

    /**
     * Bolt 39c2 の行 1・5〜8（取り込めない送信を理由と次の行動で示す。開始準備の決定 (2)・(5)）。39c の同じ番号の行と名前が衝突しないよう
     * 入れ子にする（前例 IngestionServiceTest の入れ子のクラス）。
     */
    @Nested
    class Bolt39c2_取り込めない送信 {

        private static final String FILE_TOO_LARGE = "ファイルが大きすぎます（1 ファイルにつきデータ行 10,000 行・40 MB まで）。ファイルを分けて取り込んでください。";
        private static final String RESELECT_FILE = "選んだファイルは残っていません。もう一度選んでください。";
        private static final String VOYAGE_CHECKED = "value=\"" + VOYAGE_SOURCE + "\"[^>]*checked=\"checked\"";

        @Test
        void 行1_上限を超えるファイルは取込ファイルの欄の誤りとしてエラー要約と欄の下に理由と次の行動を示し選んだ情報源を戻す() throws Exception {
            given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.FileTooLarge());

            mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"#file\">取込ファイル: " + FILE_TOO_LARGE + "</a>")))
                    .andExpect(content()
                            .string(containsString(
                                    "<p id=\"file-error\" class=\"text-danger\">" + FILE_TOO_LARGE + "</p>")))
                    .andExpect(content().string(containsString(RESELECT_FILE)))
                    .andExpect(content().string(containsPattern(VOYAGE_CHECKED)))
                    .andExpect(content().string(not(containsString(COMMON_REFUSAL))));
        }

        @Test
        void 行5_承認されていない情報源は情報源の欄の誤りとして示し選んだ情報源を戻さない() throws Exception {
            given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotApproved());

            mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: " + NOT_APPROVED + "</a>")))
                    .andExpect(content()
                            .string(containsString(
                                    "<p id=\"dataSourceId-error\" class=\"text-danger\">" + NOT_APPROVED + "</p>")))
                    .andExpect(content().string(containsString(RESELECT_FILE)))
                    .andExpect(content().string(not(containsPattern(VOYAGE_CHECKED))))
                    .andExpect(content().string(not(containsString(COMMON_REFUSAL))));
        }

        @Test
        void 行5_停止で承認済みの情報源が0件になるとフォームの外に先にエラー要約その下に0件の全文を示しファイルの欄を出さない() throws Exception {
            // 画面を開いた後に停止され、開き直した選択肢が 0 件（設計の決定「停止で承認済みが 0 件になったとき」。T-182）
            given(dataSources.ingestibleSources()).willReturn(List.of());
            given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotApproved());

            String page = mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);

            assertThat(page)
                    .contains("href=\"#no-source\">情報源: " + NOT_APPROVED + "</a>")
                    // 要約のリンクでフォーカスが段落に移るよう tabindex="-1" を付ける（Bolt 39c2 ステップ 6）
                    .contains(NO_SOURCE_PARAGRAPH + NO_SOURCE + "</p>")
                    .doesNotContain("type=\"file\"")
                    .doesNotContain(">取り込む</button>")
                    .doesNotContain("<legend>情報源</legend>");
            assertThat(page.indexOf("id=\"error-summary\"")).isPositive().isLessThan(page.indexOf(NO_SOURCE_PARAGRAPH));
        }

        @Test
        void 行6_ない情報源の送信は見つからない文言を情報源の欄の誤りとして示しサービスを呼ばない() throws Exception {
            UUID unknown = UUID.randomUUID();
            given(dataSources.recordTypeOf(unknown)).willReturn(Optional.empty());

            mockMvc.perform(ingest(unknown, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: " + NOT_FOUND + "</a>")))
                    .andExpect(content().string(not(containsString(COMMON_REFUSAL))));

            then(ingestionService).should(never()).ingest(any(), any());
        }

        @Test
        void 行6_形の誤った情報源IDの送信は見つからない文言を示しサービスを呼ばない() throws Exception {
            mockMvc.perform(malformedSource())
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: " + NOT_FOUND + "</a>")));

            then(ingestionService).should(never()).ingest(any(), any());
        }

        @Test
        void 行6_サービスがない情報源を返すと見つからない文言を示す() throws Exception {
            given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotFound());

            mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: " + NOT_FOUND + "</a>")));
        }

        @Test
        void 行7_承認済みの情報源がないと画面の層の定数の全文を示しファイルの欄と取り込むボタンを出さない() throws Exception {
            // 照会を mock して 0 件にする（共有の PostgreSQL では作れない。T-243）。全文は 39c の文言と 1 字も違えない
            given(dataSources.ingestibleSources()).willReturn(List.of());

            mockMvc.perform(get(NEW_URL))
                    .andExpect(status().isOk())
                    .andExpect(content()
                            .string(containsString(NO_SOURCE_PARAGRAPH
                                    + ExternalDataIngestionController.NO_INGESTIBLE_SOURCE + "</p>")))
                    .andExpect(content().string(not(containsString("type=\"file\""))))
                    .andExpect(content().string(not(containsString(">取り込む</button>"))))
                    .andExpect(content().string(not(containsString("id=\"error-summary\""))));
            assertThat(ExternalDataIngestionController.NO_INGESTIBLE_SOURCE).isEqualTo(NO_SOURCE);
        }

        /**
         * 行 8 の拒否の種類。取込のサービスの拒否（{@code NotPermitted} のほか）と、サービスを呼ぶ前の拒否。どの欄の誤りになるか（欄の名前と
         * 表示名）まで縛り、欄の取り違え（変異 V1: {@code FileTooLarge} を情報源の欄の誤りにする）を拾う（Bolt 39c2 ステップ 6）。
         */
        enum Refusal {
            SERVICE_NOT_FOUND(IngestionOutcome.DataSourceNotFound.class, "dataSourceId", "情報源"),
            SERVICE_NOT_APPROVED(IngestionOutcome.DataSourceNotApproved.class, "dataSourceId", "情報源"),
            SERVICE_FILE_TOO_LARGE(IngestionOutcome.FileTooLarge.class, "file", "取込ファイル"),
            UNKNOWN_SOURCE(null, "dataSourceId", "情報源"),
            MALFORMED_SOURCE(null, "dataSourceId", "情報源");

            private final Class<?> outcome;
            private final String field;
            private final String label;

            Refusal(Class<?> outcome, String field, String label) {
                this.outcome = outcome;
                this.field = field;
                this.label = label;
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Refusal.class)
        void 行8_どの拒否も決まった欄の空でない誤り1件だけになり大域の誤りを出さない(Refusal refusal) throws Exception {
            UUID unknown = UUID.randomUUID();
            given(dataSources.recordTypeOf(unknown)).willReturn(Optional.empty());
            MockMultipartHttpServletRequestBuilder request =
                    switch (refusal) {
                        case SERVICE_NOT_FOUND -> serviceRefuses(new IngestionOutcome.DataSourceNotFound());
                        case SERVICE_NOT_APPROVED -> serviceRefuses(new IngestionOutcome.DataSourceNotApproved());
                        case SERVICE_FILE_TOO_LARGE -> serviceRefuses(new IngestionOutcome.FileTooLarge());
                        case UNKNOWN_SOURCE -> ingest(unknown, "voyage.csv", VOYAGE_CSV);
                        case MALFORMED_SOURCE -> malformedSource();
                    };

            String page = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);

            java.util.regex.Matcher summary = Pattern.compile("(?s)<div id=\"error-summary\".*?<ul>(.*?)</ul>")
                    .matcher(page);
            assertThat(summary.find()).as("エラー要約").isTrue();
            List<String> items = Pattern.compile("(?s)<li>(.*?)</li>")
                    .matcher(summary.group(1))
                    .results()
                    .map(match -> match.group(1).strip())
                    .toList();
            assertThat(items)
                    .singleElement()
                    .asString()
                    .matches("<a href=\"#" + refusal.field + "\">" + refusal.label + ": [^<]+</a>");
            assertThat(page)
                    .contains("<p id=\"" + refusal.field + "-error\" class=\"text-danger\">")
                    .doesNotContain(COMMON_REFUSAL);
        }

        @Test
        void 行8_取込のサービスのNotPermittedのほかの拒否はすべて行8の拒否の種類にある() {
            // sealed の型に拒否が増えたら、行 8 の拒否の種類に足す（T-240）
            List<String> serviceRefusals = Arrays.stream(IngestionOutcome.class.getPermittedSubclasses())
                    .filter(type ->
                            type != IngestionOutcome.Ingested.class && type != IngestionOutcome.NotPermitted.class)
                    .map(Class::getName)
                    .toList();

            assertThat(Arrays.stream(Refusal.values())
                            .map(refusal -> refusal.outcome)
                            .filter(java.util.Objects::nonNull)
                            .map(Class::getName)
                            .toList())
                    .containsExactlyInAnyOrderElementsOf(serviceRefusals);
        }

        private MockMultipartHttpServletRequestBuilder serviceRefuses(IngestionOutcome outcome) {
            given(ingestionService.ingest(any(), any())).willReturn(outcome);
            return ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV);
        }
    }

    @Test
    void データ責任者でないとサービスが拒めば403() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.NotPermitted());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV)).andExpect(status().isForbidden());
    }

    // 取り込む（PRG）

    @Test
    void 行6_取り込むと情報源の原本種別で組み立てたコマンドを操作者とともに渡し結果の画面へPRGで移り知らせを示す() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(ingested());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(INGEST_URL + "/" + INGESTION_ID))
                .andExpect(flash().attribute("result", "取り込みました。"));

        ArgumentCaptor<AuthenticatedActor> operator = ArgumentCaptor.forClass(AuthenticatedActor.class);
        ArgumentCaptor<IngestFileCommand> command = ArgumentCaptor.forClass(IngestFileCommand.class);
        then(ingestionService).should().ingest(operator.capture(), command.capture());
        assertThat(operator.getValue().userId()).isEqualTo(TestActors.STAFF_USER);
        assertThat(command.getValue().dataSourceId()).isEqualTo(VOYAGE_SOURCE);
        assertThat(command.getValue().filePayload()).isEqualTo(VOYAGE_CSV.getBytes(StandardCharsets.UTF_8));
        assertThat(command.getValue().receivedCount()).isEqualTo(1);
        assertThat(command.getValue().rows())
                .singleElement()
                .satisfies(row -> assertThat(row.values()).isInstanceOf(VoyageValues.class));
    }

    @Test
    void 行6_主要実績の情報源のファイルは主要実績の種別で組み立てる() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(ingested());

        mockMvc.perform(ingest(MILESTONE_SOURCE, "milestones.csv", MILESTONE_CSV))
                .andExpect(redirectedUrl(INGEST_URL + "/" + INGESTION_ID));

        ArgumentCaptor<IngestFileCommand> command = ArgumentCaptor.forClass(IngestFileCommand.class);
        then(ingestionService).should().ingest(any(), command.capture());
        assertThat(command.getValue().dataSourceId()).isEqualTo(MILESTONE_SOURCE);
        assertThat(command.getValue().rejectedRows()).isEmpty();
        assertThat(command.getValue().rows())
                .singleElement()
                .satisfies(row -> assertThat(row.values()).isInstanceOf(MilestoneValues.class));
    }

    @Test
    void 行6_名前のある0バイトのファイルは受けて組み立てに渡す() throws Exception {
        // ファイルの誤りの隔離になり、受け取ったファイルを原本として残す（Bolt 39c の設計の決定「取込の POST の流れ」）
        given(ingestionService.ingest(any(), any())).willReturn(ingested());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "empty.csv", ""))
                .andExpect(redirectedUrl(INGEST_URL + "/" + INGESTION_ID));

        ArgumentCaptor<IngestFileCommand> command = ArgumentCaptor.forClass(IngestFileCommand.class);
        then(ingestionService).should().ingest(any(), command.capture());
        assertThat(command.getValue().filePayloadLength()).isZero();
    }

    // 結果の画面（GET）

    private static IngestionSummaryView summary() {
        return new IngestionSummaryView(
                INGESTION_ID,
                "開発海運（架空）",
                "VOYAGE",
                Instant.parse("2026-11-16T01:05:00Z"),
                125,
                118,
                4,
                3,
                List.of(
                        "3 行目: 値が空です（external_id）。提供元に確かめてください。",
                        "5 行目: 値が空です（external_id）。提供元に確かめてください。",
                        "9 行目: 値が空です（external_id）。提供元に確かめてください。"),
                0,
                null);
    }

    @Test
    void 行6_結果の画面に情報源と取り込んだ日時と受け取った件数と分類別の件数の3行と続けて取り込むを示す() throws Exception {
        given(summaries.summaryOf(INGESTION_ID)).willReturn(Optional.of(summary()));

        mockMvc.perform(get(INGEST_URL + "/" + INGESTION_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>取込の結果</h1>")))
                .andExpect(content().string(containsString("<dd>開発海運（架空）（航海）</dd>")))
                .andExpect(content()
                        .string(containsString(
                                "<dd>2026-11-16 10:05 Asia/Tokyo（UTC+09:00）（UTC 2026-11-16 01:05）</dd>")))
                .andExpect(content().string(containsString("<dt>受け取った件数（データ行）</dt>")))
                .andExpect(content().string(containsString("<dd>125</dd>")))
                .andExpect(content().string(containsString("<caption>分類別の件数（受信記録の数）</caption>")))
                .andExpect(content().string(containsString("<th scope=\"row\">採用</th>")))
                .andExpect(content().string(containsString("<td>118</td>")))
                .andExpect(content().string(containsString("<th scope=\"row\">重複</th>")))
                .andExpect(content().string(containsString("<td>4</td>")))
                .andExpect(content().string(containsString("<th scope=\"row\">隔離</th>")))
                .andExpect(content().string(containsString("<td>3</td>")))
                .andExpect(content().string(not(containsString("差異"))))
                .andExpect(content().string(containsString("href=\"/staff/external-data/ingestions/new\">続けて取り込む</a>")))
                .andExpect(content().string(not(containsString("role=\"status\""))));
    }

    @Test
    void 行6_PRGの後の結果の画面には知らせを示す() throws Exception {
        given(summaries.summaryOf(INGESTION_ID)).willReturn(Optional.of(summary()));

        mockMvc.perform(get(INGEST_URL + "/" + INGESTION_ID).flashAttr("result", "取り込みました。"))
                .andExpect(content().string(containsString("<p role=\"status\">取り込みました。</p>")));
    }

    @Test
    void 行13_結果の画面のナビの外部データの取込は現在の項目になる() throws Exception {
        given(summaries.summaryOf(INGESTION_ID)).willReturn(Optional.of(summary()));

        mockMvc.perform(get(INGEST_URL + "/" + INGESTION_ID))
                .andExpect(content().string(containsPattern(CURRENT_NAV_ITEM)));
    }

    @Test
    void 行14_ない取込IDの結果の画面は404() throws Exception {
        given(summaries.summaryOf(any())).willReturn(Optional.empty());

        mockMvc.perform(get(INGEST_URL + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void 行14_形の誤った取込IDの結果の画面は404() throws Exception {
        mockMvc.perform(get(INGEST_URL + "/not-a-uuid")).andExpect(status().isNotFound());

        then(summaries).should(never()).summaryOf(any());
    }

    /**
     * Bolt 39c3 の行 13・16〜18（結果の画面の隔離の理由。開始準備の決定 (4)・(9)）。照会は mock にし、テンプレートが注記を変えずに照会の順に
     * 示すこと、残りの件数の文、隔離が 0 件なら領域を出さないこと、注記の文字を文字として示すことを確かめる。39c・39c2 の同じ番号の行と名前が
     * 衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39c3_隔離の理由 {

        private static final String NOTES_HEADING = "<h2 id=\"quarantine-notes-heading\">隔離の理由</h2>";
        private static final String UNDECODABLE = "ファイル: 文字コードが UTF-8 ではありません。UTF-8 で保存し直したファイルを取り込んでください。";
        private static final String UNREADABLE_HEADER = "ファイル: ヘッダーを読めません。ヘッダーの引用符を確かめてください。";

        private IngestionSummaryView summaryWith(int quarantinedCount, List<String> notes, int remaining) {
            return new IngestionSummaryView(
                    INGESTION_ID,
                    "開発海運（架空）",
                    "VOYAGE",
                    Instant.parse("2026-11-16T01:05:00Z"),
                    2,
                    0,
                    0,
                    quarantinedCount,
                    notes,
                    remaining,
                    null);
        }

        private org.springframework.test.web.servlet.ResultActions resultOf(IngestionSummaryView summary)
                throws Exception {
            given(summaries.summaryOf(INGESTION_ID)).willReturn(Optional.of(summary));
            return mockMvc.perform(get(INGEST_URL + "/" + INGESTION_ID)).andExpect(status().isOk());
        }

        @Test
        void 行13_ファイルの誤りの注記は今のどうすればよいかのまま照会の順に隔離の理由の一覧に出る() throws Exception {
            resultOf(summaryWith(2, List.of(UNDECODABLE, UNREADABLE_HEADER), 0))
                    .andExpect(content().string(containsString(NOTES_HEADING)))
                    .andExpect(content().string(containsString("<p>ファイルの行の順に示します。</p>")))
                    .andExpect(content()
                            .string(matchesPattern(Pattern.compile(
                                    ".*<ul aria-labelledby=\"quarantine-notes-heading\">\\s*<li>"
                                            + Pattern.quote(UNDECODABLE) + "</li>\\s*<li>"
                                            + Pattern.quote(UNREADABLE_HEADER) + "</li>\\s*</ul>.*",
                                    Pattern.DOTALL))))
                    .andExpect(content().string(not(containsString("ほかに"))));
        }

        @ParameterizedTest(name = "残り {0} 件")
        @CsvSource({"1, 1", "9900, '9,900'"})
        void 行16_示さなかった隔離があれば残りの件数を3桁の区切りで示す(int remaining, String shownRemaining) throws Exception {
            int limit = IngestionSummaryQueryService.SHOWN_QUARANTINE_LIMIT;

            resultOf(summaryWith(limit + remaining, List.of(UNDECODABLE), remaining))
                    .andExpect(content()
                            .string(containsString(
                                    "<p>ほかに " + shownRemaining + " 件の隔離があります（最初の " + limit + " 件だけを示しています）。</p>")));
        }

        @Test
        void 行17_隔離が0件なら隔離の理由の領域を出さない() throws Exception {
            resultOf(summaryWith(0, List.of(), 0))
                    .andExpect(content().string(not(containsString("隔離の理由"))))
                    .andExpect(content().string(not(containsString("ファイルの行の順に示します。"))));
        }

        @Test
        void 行18_取込ファイルから来た文字を含む注記は文字として示す() throws Exception {
            String note = "ファイル: 同じ名前の列があります（<b>id</b>、<script>alert(1)</script>）。提供元に確かめてください。";

            resultOf(summaryWith(1, List.of(note), 0))
                    .andExpect(content()
                            .string(containsString(
                                    "<li>ファイル: 同じ名前の列があります（&lt;b&gt;id&lt;/b&gt;、&lt;script&gt;alert(1)&lt;/script&gt;）。"
                                            + "提供元に確かめてください。</li>")))
                    .andExpect(content().string(not(containsString("<b>id</b>"))))
                    .andExpect(content().string(not(containsString("<script>alert"))));
        }
    }

    /**
     * Bolt 39c4 の行 8・9・14・15・19（結果の画面の要確認と、情報源の拒否の文言。開始準備の決定 (4)・(7)）。照会は mock にし、要確認のときだけ
     * 受け取った件数の {@code dl} の後・分類別の件数の表の前に領域を出すこと、取り込めなかった部分の 3 つの形の全文、拒否の 2 つの文言を
     * 確かめる。39c・39c2・39c3 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39c4_要確認 {

        private static final String REVIEW_HEADING = "<h2 id=\"review-heading\">要確認</h2>";
        private static final String REVIEW_GUIDE =
                "<p>取り込めなかった部分があります。取り込めなかった部分の行は分類されていません。" + "下の「隔離の理由」で理由とどうすればよいかを確かめてください。</p>";

        private IngestionSummaryView summaryWith(IngestionSummaryView.UnreadableRegion unreadable) {
            return new IngestionSummaryView(
                    INGESTION_ID,
                    "開発海運（架空）",
                    "VOYAGE",
                    Instant.parse("2026-11-16T01:05:00Z"),
                    31,
                    2,
                    0,
                    1,
                    List.of("12 行目からファイルの終わりまで: 引用符が閉じていません。29 行を取り込めませんでした。ファイルを確かめてください。"),
                    0,
                    unreadable);
        }

        private String resultPage(IngestionSummaryView summary) throws Exception {
            given(summaries.summaryOf(INGESTION_ID)).willReturn(Optional.of(summary));
            return mockMvc.perform(get(INGEST_URL + "/" + INGESTION_ID))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
        }

        @Test
        void 行8_要確認でない結果の画面は要確認の領域を出さない() throws Exception {
            String page = resultPage(summaryWith(null));

            assertThat(page).doesNotContain("要確認").doesNotContain("取り込めなかった部分");
        }

        /** 取り込めなかった部分の形（ファイルの終わりまで・ファイル全体でデータ行あり・データ行なし）。行 9 の期待の組の網羅に使う（T-240）。 */
        enum RegionForm {
            UNTIL_END_OF_FILE,
            WHOLE_FILE_WITH_LINES,
            WHOLE_FILE_WITHOUT_LINES;

            static RegionForm of(IngestionSummaryView.UnreadableRegion region) {
                if (!region.wholeFile()) {
                    return UNTIL_END_OF_FILE;
                }
                return region.lineCount() > 0 ? WHOLE_FILE_WITH_LINES : WHOLE_FILE_WITHOUT_LINES;
            }
        }

        /** 行 9 の形と期待の全文の組（T-244）。 */
        record RegionCase(IngestionSummaryView.UnreadableRegion region, String expected) {

            @Override
            public String toString() {
                return expected;
            }
        }

        static Stream<RegionCase> 取り込めなかった部分と期待の全文() {
            return Stream.of(
                    new RegionCase(new IngestionSummaryView.UnreadableRegion(12, 29, false), "12 行目からファイルの終わりまで（29 行）"),
                    new RegionCase(new IngestionSummaryView.UnreadableRegion(2, 1234, true), "ファイル全体（データ行 1,234 行）"),
                    new RegionCase(new IngestionSummaryView.UnreadableRegion(null, 0, true), "ファイル全体（データ行なし）"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("取り込めなかった部分と期待の全文")
        void 行9_要確認の領域は受け取った件数の後と分類別の件数の表の前に見出しと案内の文と取り込めなかった部分の全文を示す(RegionCase regionCase) throws Exception {
            String page = resultPage(summaryWith(regionCase.region()));

            String region = "<p>取り込めなかった部分: " + regionCase.expected() + "</p>";
            assertThat(page)
                    .containsPattern("<section aria-labelledby=\"review-heading\">\\s*" + Pattern.quote(REVIEW_HEADING)
                            + "\\s*" + Pattern.quote(REVIEW_GUIDE) + "\\s*" + Pattern.quote(region) + "\\s*</section>")
                    .doesNotContain("role=\"alert\"");
            assertThat(page.indexOf("</dl>")).isPositive().isLessThan(page.indexOf(REVIEW_HEADING));
            assertThat(page.indexOf(REVIEW_HEADING)).isLessThan(page.indexOf("<table"));
        }

        @Test
        void 行9_期待の組は取り込めなかった部分の3つの形をすべて含む() {
            assertThat(取り込めなかった部分と期待の全文().map(regionCase -> RegionForm.of(regionCase.region())))
                    .containsExactlyInAnyOrder(RegionForm.values());
        }

        @Test
        void 行14_承認されていない情報源の拒否はエラー要約と欄に示された情報源から選び直す文言を示す() throws Exception {
            given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotApproved());

            mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: " + NOT_APPROVED + "</a>")))
                    .andExpect(content()
                            .string(containsString(
                                    "<p id=\"dataSourceId-error\" class=\"text-danger\">" + NOT_APPROVED + "</p>")))
                    .andExpect(content().string(not(containsString("画面を開き直して"))));
        }

        @Test
        void 行15_ない情報源の拒否はエラー要約と欄に示された情報源から選び直す文言を示す() throws Exception {
            given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotFound());

            mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"#dataSourceId\">情報源: " + NOT_FOUND + "</a>")))
                    .andExpect(content()
                            .string(containsString(
                                    "<p id=\"dataSourceId-error\" class=\"text-danger\">" + NOT_FOUND + "</p>")))
                    .andExpect(content().string(not(containsString("画面を開き直して"))));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"NOT_APPROVED", "NOT_FOUND"})
        void 行19_拒否の後に承認済みの情報源が0件なら拒否の文言のままの理由が先でその下に0件の全文を示し要約のリンクは0件の段落へ向く(String refusal) throws Exception {
            // 照会を mock して 0 件にする（共有の PostgreSQL では作れない。T-243）
            given(dataSources.ingestibleSources()).willReturn(List.of());
            boolean notApproved = "NOT_APPROVED".equals(refusal);
            given(ingestionService.ingest(any(), any()))
                    .willReturn(
                            notApproved
                                    ? new IngestionOutcome.DataSourceNotApproved()
                                    : new IngestionOutcome.DataSourceNotFound());
            String message = notApproved ? NOT_APPROVED : NOT_FOUND;

            String page = mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);

            assertThat(page)
                    .contains("href=\"#no-source\">情報源: " + message + "</a>")
                    .contains(NO_SOURCE_PARAGRAPH + NO_SOURCE + "</p>");
            assertThat(page.indexOf(message)).isPositive().isLessThan(page.indexOf(NO_SOURCE_PARAGRAPH));
        }
    }
}

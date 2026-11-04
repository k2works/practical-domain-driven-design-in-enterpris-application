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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * S-16 外部データの取込（データ責任者。US-14 AC1・AC2。Bolt 39c の行 3〜6・14）。取込のサービスと照会は mock にし、画面の振る舞い
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

    // 拒否（39c は共通の文言。理由ごとの文言は 39c2）

    @Test
    void 行5_承認されていない情報源の送信は共通の文言のエラー要約を示す() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotApproved());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"error-summary\"")))
                .andExpect(content().string(containsString(COMMON_REFUSAL)));
    }

    @Test
    void 行5_上限を超えるファイルは39cでは共通の文言のエラー要約を示す() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.FileTooLarge());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(COMMON_REFUSAL)));
    }

    @Test
    void 行5_サービスがない情報源を返すと共通の文言のエラー要約を示す() throws Exception {
        given(ingestionService.ingest(any(), any())).willReturn(new IngestionOutcome.DataSourceNotFound());

        mockMvc.perform(ingest(VOYAGE_SOURCE, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(COMMON_REFUSAL)));
    }

    @Test
    void 行5_ない情報源の送信は原本種別を引けないので共通の文言を示しサービスを呼ばない() throws Exception {
        UUID unknown = UUID.randomUUID();
        given(dataSources.recordTypeOf(unknown)).willReturn(Optional.empty());

        mockMvc.perform(ingest(unknown, "voyage.csv", VOYAGE_CSV))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(COMMON_REFUSAL)));

        then(ingestionService).should(never()).ingest(any(), any());
    }

    @Test
    void 行5_形の誤った情報源IDの送信は共通の文言を示しサービスを呼ばない() throws Exception {
        mockMvc.perform(multipart(INGEST_URL)
                        .file(new MockMultipartFile(
                                "file", "voyage.csv", "text/csv", VOYAGE_CSV.getBytes(StandardCharsets.UTF_8)))
                        .param("dataSourceId", "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(COMMON_REFUSAL)));

        then(ingestionService).should(never()).ingest(any(), any());
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
                INGESTION_ID, "開発海運（架空）", "VOYAGE", Instant.parse("2026-11-16T01:05:00Z"), 125, 118, 4, 3);
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
}

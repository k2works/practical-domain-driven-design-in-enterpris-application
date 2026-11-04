package com.example.cargotracker.externaldata.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordPage;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordQueryService;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * S-17 隔離・差異の確認（判断を待つ隔離の一覧。データ責任者。US-14 AC4。Bolt 39d の行 1・2・5・9〜14・17）。照会は mock にし、画面の
 * 振る舞い（件数と示す範囲の文、前後のページのリンク、0 件の文、取込で絞ったときの文とリンク、絞った取込がないときの文、不正なページ、
 * 項目の示し方、注記の文字）だけを見る。認証・認可はセキュリティの統合テストで確かめる。判断を待つ隔離が全体で 0 件の状態は共有の
 * PostgreSQL で作れないので、ここで確かめる（T-243）。
 */
@WithAuthenticatedActor(Role.DATA_STEWARD)
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = UndecidedRecordController.class)
class UndecidedRecordControllerTest {

    private static final String LIST_URL = "/staff/external-data/undecided-records";
    private static final UUID INGESTION_ID = UUID.fromString("39d00000-0000-0000-0000-0000000000aa");
    private static final Instant INGESTED_AT = Instant.parse("2026-11-16T01:05:00Z");
    private static final String TABLE = "<caption>判断を待つ隔離</caption>";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UndecidedRecordQueryService query;

    /** 判断の画面の行き先と却下（Bolt 39d2）。 */
    @MockitoBean
    ReceivedRecordDecisionService decisions;

    private static UndecidedRecordPage.Item keyless(int line) {
        return new UndecidedRecordPage.Item(
                UUID.randomUUID(),
                INGESTED_AT,
                "開発海運（架空）",
                "VOYAGE",
                new PhysicalLineRange(line, line),
                true,
                null,
                null,
                line + " 行目: 寄港の列の値が正しくありません。提供元に確かめてください。");
    }

    private static List<UndecidedRecordPage.Item> items(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> keyless(n + 1)).toList();
    }

    private static UndecidedRecordPage page(int total, int page, UUID filter) {
        int pageCount = (total + 99) / 100;
        int shown = total == 0 ? 0 : Math.min(100, total - (page - 1) * 100);
        return new UndecidedRecordPage(items(shown), total, page, pageCount, filter, false);
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get(LIST_URL + query)).andExpect(status().isOk());
    }

    // --- 行 1・2: 項目の示し方 ---

    @Test
    void 行1_行2_取り込んだ日時と情報源と行と種類と外部ID外部版と隔離の理由を示す() throws Exception {
        UndecidedRecordPage.Item mismatch = new UndecidedRecordPage.Item(
                UUID.randomUUID(),
                INGESTED_AT,
                "開発海運（架空）",
                "VOYAGE",
                new PhysicalLineRange(140, 141),
                false,
                "DEMO-201",
                "3",
                "140 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-15 09:00:00 UTC）。");
        UndecidedRecordPage.Item fileError = new UndecidedRecordPage.Item(
                UUID.randomUUID(),
                INGESTED_AT,
                "開発海運（架空）",
                "MILESTONE",
                null,
                true,
                null,
                null,
                "ファイル: 必要な列がありません（port_calls）。");
        given(query.page(null, 1))
                .willReturn(new UndecidedRecordPage(List.of(fileError, mismatch), 2, 1, 1, null, false));

        list("").andExpect(content().string(containsString("<h1>隔離・差異の確認</h1>")))
                .andExpect(content().string(containsString(TABLE)))
                .andExpect(content()
                        .string(matchesPattern(
                                Pattern.compile("(?s).*<th scope=\"col\">取り込んだ日時</th>\\s*<th scope=\"col\">情報源</th>\\s*"
                                        + "<th scope=\"col\">行</th>\\s*<th scope=\"col\">種類</th>\\s*"
                                        + "<th scope=\"col\">外部 ID・外部版</th>\\s*<th scope=\"col\">隔離の理由</th>\\s*"
                                        // 判断の列は Bolt 39d2 で足した
                                        + "<th scope=\"col\">判断</th>.*"))))
                .andExpect(
                        content()
                                .string(
                                        matchesPattern(
                                                Pattern.compile(
                                                        "(?s).*<tr>\\s*"
                                                                + "<td>2026-11-16 10:05 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 01:05）</td>\\s*"
                                                                + "<td>開発海運（架空）（主要実績）</td>\\s*"
                                                                // 行を持たないファイルの誤りの「—」は読み上げで「ファイル全体」と読む（Bolt 39d の開発レビュー）
                                                                + "<td><span aria-hidden=\"true\">—</span><span class=\"visually-hidden\">ファイル全体</span></td>\\s*"
                                                                + "<td>形式の誤り</td>\\s*"
                                                                + "<td><span aria-hidden=\"true\">—</span><span class=\"visually-hidden\">なし</span></td>\\s*"
                                                                + "<td>ファイル: 必要な列がありません（port_calls）。</td>\\s*<td><a [^>]*>判断する.*?</a></td>\\s*"
                                                                + "</tr>\\s*<tr>\\s*"
                                                                + "<td>2026-11-16 10:05 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 01:05）</td>\\s*"
                                                                + "<td>開発海運（架空）（航海）</td>\\s*<td>140〜141 行目</td>\\s*<td>内容の違う原本</td>\\s*"
                                                                + "<td>DEMO-201・3</td>\\s*<td>140 行目: .*?</td>\\s*<td><a [^>]*>判断する.*?</a></td>\\s*</tr>.*"))));
    }

    @Test
    void 行1_1行の範囲はN行目で示す() throws Exception {
        given(query.page(null, 1)).willReturn(new UndecidedRecordPage(List.of(keyless(102)), 1, 1, 1, null, false));

        list("").andExpect(content().string(containsString("<td>102 行目</td>")));
    }

    // --- 行 5: 件数と示す範囲の文、前後のページのリンク（T-244） ---

    @ParameterizedTest(name = "{0} 件の {1} ページ目")
    @CsvSource(
            delimiter = '|',
            value = {
                "100   | 1   | 判断を待つ隔離 100 件（1〜100 件目を示しています）          | false | false",
                "101   | 1   | 判断を待つ隔離 101 件（1〜100 件目を示しています）          | false | true",
                "101   | 2   | 判断を待つ隔離 101 件（101〜101 件目を示しています）        | true  | false",
                "10000 | 50  | 判断を待つ隔離 10,000 件（4,901〜5,000 件目を示しています）  | true  | true",
                "10000 | 100 | 判断を待つ隔離 10,000 件（9,901〜10,000 件目を示しています） | true  | false"
            })
    void 行5_件数と示す範囲を3桁の区切りで示し前後のページがあればリンクを出す(
            int total, int pageNumber, String countText, boolean previous, boolean next) throws Exception {
        given(query.page(null, pageNumber)).willReturn(page(total, pageNumber, null));

        String body = mockMvc.perform(get(LIST_URL + "?page=" + pageNumber))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThat(body).contains("<p>" + countText + "</p>");
        String previousLink = "<a href=\"" + LIST_URL + "?page=" + (pageNumber - 1) + "\">前の 100 件</a>";
        String nextLink = "<a href=\"" + LIST_URL + "?page=" + (pageNumber + 1) + "\">次の 100 件</a>";
        if (previous) {
            org.assertj.core.api.Assertions.assertThat(body).contains(previousLink);
        } else {
            org.assertj.core.api.Assertions.assertThat(body).doesNotContain("前の 100 件");
        }
        if (next) {
            org.assertj.core.api.Assertions.assertThat(body).contains(nextLink);
        } else {
            org.assertj.core.api.Assertions.assertThat(body).doesNotContain("次の 100 件");
        }
        if (!previous && !next) {
            org.assertj.core.api.Assertions.assertThat(body).doesNotContain("aria-label=\"判断を待つ隔離のページ\"");
        }
    }

    @Test
    void 行5_取込で絞っているときは前後のページのリンクも取込で絞る() throws Exception {
        given(query.page(INGESTION_ID, 2)).willReturn(page(10000, 2, INGESTION_ID));

        list("?ingestion=" + INGESTION_ID + "&page=2")
                .andExpect(content()
                        .string(containsString(
                                "<a href=\"" + LIST_URL + "?ingestion=" + INGESTION_ID + "&amp;page=1\">前の 100 件</a>")))
                .andExpect(content()
                        .string(containsString("<a href=\"" + LIST_URL + "?ingestion=" + INGESTION_ID
                                + "&amp;page=3\">次の 100 件</a>")));
    }

    // --- 行 9・10: 0 件 ---

    @Test
    void 行9_判断を待つ隔離が全体で0件なら表を出さず0件の文と外部データの取込へのリンクを示す() throws Exception {
        given(query.page(null, 1)).willReturn(page(0, 1, null));

        list("").andExpect(content().string(containsString("<p>判断を待つ隔離はありません。</p>")))
                .andExpect(content().string(not(containsString("<table"))))
                .andExpect(content().string(not(containsString("件目を示しています"))))
                .andExpect(content().string(not(containsString("すべての取込の隔離を見る"))))
                .andExpect(content()
                        .string(containsString("<a href=\"/staff/external-data/ingestions/new\">外部データの取込へ</a>")));
    }

    @Test
    void 行10_取込で絞って0件ならこの取込に判断を待つ隔離はないと示しすべての取込の隔離を見るリンクを出す() throws Exception {
        given(query.page(INGESTION_ID, 1)).willReturn(page(0, 1, INGESTION_ID));

        list("?ingestion=" + INGESTION_ID)
                .andExpect(content().string(containsString("<p>この取込に判断を待つ隔離はありません。</p>")))
                .andExpect(content().string(not(containsString("<p>判断を待つ隔離はありません。</p>"))))
                .andExpect(content().string(not(containsString("<table"))))
                .andExpect(content().string(containsString("<a href=\"" + LIST_URL + "\">すべての取込の隔離を見る</a>")));
    }

    @Test
    void 行10_取込で絞っているときは件数の文をこの取込のにしすべての取込の隔離を見るリンクを出す() throws Exception {
        given(query.page(INGESTION_ID, 1)).willReturn(page(2, 1, INGESTION_ID));

        list("?ingestion=" + INGESTION_ID)
                .andExpect(content().string(containsString("<p>この取込の判断を待つ隔離 2 件（1〜2 件目を示しています）</p>")))
                .andExpect(content().string(containsString("<a href=\"" + LIST_URL + "\">すべての取込の隔離を見る</a>")))
                .andExpect(content().string(not(containsString("外部データの取込へ"))));
    }

    // --- 行 12: ナビ ---

    @Test
    void 行12_ナビの隔離差異の確認が現在の項目になり外部データの取込は現在の項目でない() throws Exception {
        given(query.page(null, 1)).willReturn(page(0, 1, null));

        list("").andExpect(content()
                        .string(matchesPattern(Pattern.compile("(?s).*class=\"nav-link fw-bold\" href=\"" + LIST_URL
                                + "\" aria-current=\"page\">隔離・差異の確認</a>.*"))))
                .andExpect(content()
                        .string(containsString(
                                "<a class=\"nav-link\" href=\"/staff/external-data/ingestions/new\">外部データの取込</a>")));
    }

    // --- 行 13: 不正な取込 ID ---

    @Test
    void 行13_UUIDでない取込IDは400にせず絞らない一覧に指定された取込が見つからないと示す() throws Exception {
        given(query.page(null, 1)).willReturn(page(2, 1, null));

        list("?ingestion=not-a-uuid")
                .andExpect(content().string(containsString("<p>指定された取込が見つかりません。</p>")))
                .andExpect(content().string(containsString("<p>判断を待つ隔離 2 件（1〜2 件目を示しています）</p>")))
                .andExpect(content().string(not(containsString("すべての取込の隔離を見る"))));
        then(query).should().page(null, 1);
    }

    @Test
    void 行13_ない取込IDは照会の見つからない印で同じ文を示す() throws Exception {
        UUID missing = UUID.randomUUID();
        given(query.page(missing, 1)).willReturn(new UndecidedRecordPage(items(2), 2, 1, 1, null, true));

        list("?ingestion=" + missing)
                .andExpect(content().string(containsString("<p>指定された取込が見つかりません。</p>")))
                .andExpect(content().string(containsString("<p>判断を待つ隔離 2 件（1〜2 件目を示しています）</p>")));
    }

    @Test
    void 行13_取込IDがあれば見つからない文を出さない() throws Exception {
        given(query.page(INGESTION_ID, 1)).willReturn(page(2, 1, INGESTION_ID));

        list("?ingestion=" + INGESTION_ID).andExpect(content().string(not(containsString("指定された取込が見つかりません。"))));
    }

    // --- 行 14: 不正なページ ---

    @ParameterizedTest(name = "page={0}")
    @ValueSource(strings = {"0", "-1", "abc", "", "99999999999"})
    void 行14_0と負と数でないページは1ページ目を照会する(String pageParameter) throws Exception {
        given(query.page(any(), anyInt())).willReturn(page(101, 1, null));

        list("?page=" + pageParameter)
                .andExpect(content().string(containsString("<p>判断を待つ隔離 101 件（1〜100 件目を示しています）</p>")));
        then(query).should().page(null, 1);
    }

    @Test
    void 行14_最後より後のページは照会の寄せた最後のページを示す() throws Exception {
        given(query.page(null, 999)).willReturn(page(10000, 100, null));

        list("?page=999")
                .andExpect(content().string(containsString("<p>判断を待つ隔離 10,000 件（9,901〜10,000 件目を示しています）</p>")))
                .andExpect(content().string(not(containsString("次の 100 件"))));
    }

    // --- 行 17: 注記の文字 ---

    @Test
    void 行17_取込ファイルから来た文字を含む注記は文字として示す() throws Exception {
        UndecidedRecordPage.Item item = new UndecidedRecordPage.Item(
                UUID.randomUUID(),
                INGESTED_AT,
                "開発海運（架空）",
                "VOYAGE",
                null,
                true,
                null,
                null,
                "ファイル: 同じ名前の列があります（<b>id</b>、<script>alert(1)</script>、&amp;）。提供元に確かめてください。");
        given(query.page(null, 1)).willReturn(new UndecidedRecordPage(List.of(item), 1, 1, 1, null, false));

        list("").andExpect(content()
                        .string(containsString("<td>ファイル: 同じ名前の列があります（&lt;b&gt;id&lt;/b&gt;、"
                                + "&lt;script&gt;alert(1)&lt;/script&gt;、&amp;amp;）。提供元に確かめてください。</td>")))
                .andExpect(content().string(not(containsString("<b>id</b>"))))
                .andExpect(content().string(not(containsString("<script>alert"))));
    }

    /**
     * Bolt 39d2 の行 1・3〜5・7・8・17・18・22（S-17 の判断の画面と却下の POST。開始準備の決定 (3)・(5)・(10)・(11)、T-240・T-244）。判断の
     * サービスと照会は mock にし、行き先（404・一覧に戻る・判断の画面）と文言、欄の誤り、戻り先の URL を確かめる。39d の同じ番号の行と名前が
     * 衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d2_判断の画面と却下 {

        private static final UUID RECORD_ID = UUID.fromString("39d20000-0000-0000-0000-0000000000bb");
        private static final String DECISION_URL = LIST_URL + "/" + RECORD_ID;
        private static final String POST_URL = DECISION_URL + "/decision";
        private static final String REJECTED = "隔離を却下しました。";
        private static final String ALREADY_DECIDED = "この隔離は判断済みです。";
        private static final String REASON_REQUIRED = "却下の理由を書いてください。";
        private static final String REASON_TOO_LONG = "却下の理由は 1000 文字までで書いてください。";
        private static final String IRREVOCABLE = "却下すると取り消せません。誤って却下したときは、提供元に同じ原本を送り直してもらってください。";
        private static final String KEYLESS_GUIDE = "形式の誤りは採用できません。直したファイルを取り込み直してください。";
        /** 補助文字（U+20BB7。UTF-16 で 2 単位）。 */
        private static final String SUPPLEMENTARY = new String(Character.toChars(0x20BB7));

        private UndecidedRecordPage.Item mismatch() {
            return new UndecidedRecordPage.Item(
                    RECORD_ID,
                    INGESTED_AT,
                    "開発海運（架空）",
                    "VOYAGE",
                    new PhysicalLineRange(140, 141),
                    false,
                    "DEMO-201",
                    "3",
                    "140 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（<b>最初の受信</b>: 2026-11-15 09:00:00 UTC）。");
        }

        private UndecidedRecordPage.Item keylessItem() {
            return new UndecidedRecordPage.Item(
                    RECORD_ID,
                    INGESTED_AT,
                    "開発海運（架空）",
                    "VOYAGE",
                    new PhysicalLineRange(4, 4),
                    true,
                    null,
                    null,
                    "4 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。提供元に確かめてください。");
        }

        private void givenDecidable(UndecidedRecordPage.Item item) {
            given(decisions.refusalOf(RECORD_ID)).willReturn(java.util.Optional.empty());
            given(query.undecidedQuarantine(RECORD_ID)).willReturn(java.util.Optional.of(item));
        }

        private String decisionPage(String url) throws Exception {
            return mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        }

        private ResultActions postReason(String url, String reason) throws Exception {
            return mockMvc.perform(post(url).param("reason", reason));
        }

        // --- 行 17: 一覧の「判断する」と戻り先 ---

        @Test
        void 行17_一覧の各行に元の取込とページへの戻り先を持つ判断するのリンクがあり読み上げに行の範囲と外部IDを含める() throws Exception {
            UndecidedRecordPage.Item item = mismatch();
            given(query.page(INGESTION_ID, 2))
                    .willReturn(new UndecidedRecordPage(List.of(item), 101, 2, 2, INGESTION_ID, false));

            list("?ingestion=" + INGESTION_ID + "&page=2")
                    .andExpect(
                            content()
                                    .string(
                                            containsString(
                                                    "<a href=\"" + DECISION_URL + "?ingestion=" + INGESTION_ID
                                                            + "&amp;page=2\">判断する<span class=\"visually-hidden\">（140〜141 行目、DEMO-201・3）</span></a>")));
        }

        @Test
        void 行17_絞らない一覧のキーのない隔離の判断するはページだけを持ち外部IDなしと読む() throws Exception {
            given(query.page(null, 1))
                    .willReturn(new UndecidedRecordPage(List.of(keylessItem()), 1, 1, 1, null, false));

            list("").andExpect(content()
                    .string(containsString("<a href=\"" + DECISION_URL
                            + "?page=1\">判断する<span class=\"visually-hidden\">（4 行目、外部 ID なし）</span></a>")));
        }

        @Test
        void 行17_判断の画面の一覧に戻ると却下の送り先は元の取込とページを持つ() throws Exception {
            givenDecidable(mismatch());

            String page = decisionPage(DECISION_URL + "?ingestion=" + INGESTION_ID + "&page=2");

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<a href=\"" + LIST_URL + "?ingestion=" + INGESTION_ID + "&amp;page=2\">一覧に戻る</a>")
                    .contains("action=\"" + POST_URL + "?ingestion=" + INGESTION_ID + "&amp;page=2\"");
        }

        @Test
        void 行17_読めない取込とページの戻り先は捨てて既定の一覧にする() throws Exception {
            givenDecidable(mismatch());
            given(decisions.reject(any(), any())).willReturn(new DecisionOutcome.Rejected(RECORD_ID));

            String page = decisionPage(DECISION_URL + "?ingestion=%3Cscript%3E&page=abc");

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<a href=\"" + LIST_URL + "?page=1\">一覧に戻る</a>")
                    .doesNotContain("script%3E")
                    .doesNotContain("&lt;script");
            postReason(POST_URL + "?ingestion=evil&page=-3", "理由")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                            LIST_URL + "?page=1"));
        }

        // --- 行 18: 判断の画面の中身 ---

        @Test
        void 行18_内容の違う原本の判断の画面は一覧と同じ項目と隔離の理由の全文と取り消せないことを示す() throws Exception {
            givenDecidable(mismatch());

            String page = decisionPage(DECISION_URL);

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<h1>隔離の判断</h1>")
                    .containsPattern(
                            "<dt>取り込んだ日時</dt>\\s*<dd>2026-11-16 10:05 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 01:05）</dd>")
                    .containsPattern("<dt>情報源</dt>\\s*<dd>開発海運（架空）（航海）</dd>")
                    .containsPattern("<dt>行</dt>\\s*<dd>140〜141 行目</dd>")
                    .containsPattern("<dt>種類</dt>\\s*<dd>内容の違う原本</dd>")
                    .containsPattern("<dt>外部 ID・外部版</dt>\\s*<dd>DEMO-201・3</dd>")
                    // 注記には取込ファイルから来た値が入るので文字として示す
                    .contains("<dd>140 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（&lt;b&gt;最初の受信&lt;/b&gt;: "
                            + "2026-11-15 09:00:00 UTC）。</dd>")
                    .doesNotContain("<b>最初の受信</b>")
                    .contains(IRREVOCABLE)
                    .doesNotContain(KEYLESS_GUIDE)
                    .contains("<label for=\"reason\">却下の理由</label>")
                    .contains(">却下する</button>");
        }

        @Test
        void 行18_キーのない隔離の判断の画面は却下だけと直したファイルを取り込み直す案内を示す() throws Exception {
            givenDecidable(keylessItem());

            String page = decisionPage(DECISION_URL);

            org.assertj.core.api.Assertions.assertThat(page)
                    .containsPattern("<dt>種類</dt>\\s*<dd>形式の誤り</dd>")
                    .containsPattern("<dt>外部 ID・外部版</dt>\\s*<dd>なし</dd>")
                    .contains(KEYLESS_GUIDE)
                    .contains(IRREVOCABLE)
                    .contains(">却下する</button>")
                    .doesNotContain("採用する");
        }

        @Test
        void 行18_理由の欄の説明の上限は判断の理由の上限から示し欄は必須と読み上げる() throws Exception {
            givenDecidable(mismatch());

            String page = mockMvc.perform(get(DECISION_URL))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                            .attribute("reasonLimit", ReceivedRecordDecision.REASON_LIMIT))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<p id=\"reason-hint\">必須、" + ReceivedRecordDecision.REASON_LIMIT + " 文字まで</p>")
                    .containsPattern("<textarea[^>]*id=\"reason\"[^>]*aria-required=\"true\"");
        }

        // --- 行 3〜5・22: 判断の画面の行き先（判断できる受信記録の定義は GET と POST で 1 つ） ---

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"NotFound", "NotDecidable"})
        void 行3_行4_行22_読めない受信記録と隔離でない受信記録の判断の画面は404(String refusal) throws Exception {
            given(decisions.refusalOf(RECORD_ID))
                    .willReturn(java.util.Optional.of(
                            "NotFound".equals(refusal)
                                    ? new DecisionOutcome.NotFound()
                                    : new DecisionOutcome.NotDecidable()));

            mockMvc.perform(get(DECISION_URL)).andExpect(status().isNotFound());
            then(query).should(org.mockito.Mockito.never()).undecidedQuarantine(any());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"not-a-uuid", "123", "39d20000-0000-0000-0000-0000000000bb-x"})
        void 行4_UUIDでない受信記録IDは判断の画面もPOSTも400にせず404にしサービスを呼ばない(String id) throws Exception {
            mockMvc.perform(get(LIST_URL + "/" + id)).andExpect(status().isNotFound());
            postReason(LIST_URL + "/" + id + "/decision", "理由").andExpect(status().isNotFound());
            then(decisions).shouldHaveNoInteractions();
        }

        @Test
        void 行5_判断済みの隔離の判断の画面は元の一覧に戻って判断済みと知らせる() throws Exception {
            given(decisions.refusalOf(RECORD_ID))
                    .willReturn(java.util.Optional.of(new DecisionOutcome.AlreadyDecided()));

            mockMvc.perform(get(DECISION_URL + "?ingestion=" + INGESTION_ID + "&page=2"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                            LIST_URL + "?ingestion=" + INGESTION_ID + "&page=2"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attribute("result", ALREADY_DECIDED));
        }

        @Test
        void 行5_行き先を決めた後に判断されて中身が読めないときも判断済みと知らせる() throws Exception {
            given(decisions.refusalOf(RECORD_ID)).willReturn(java.util.Optional.empty());
            given(query.undecidedQuarantine(RECORD_ID)).willReturn(java.util.Optional.empty());

            mockMvc.perform(get(DECISION_URL))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attribute("result", ALREADY_DECIDED));
        }

        @Test
        void 行5_一覧は知らせを状態の領域に示す() throws Exception {
            given(query.page(null, 1)).willReturn(page(0, 1, null));

            mockMvc.perform(get(LIST_URL).flashAttr("result", REJECTED))
                    .andExpect(content().string(containsString("<p role=\"status\">" + REJECTED + "</p>")));
        }

        // --- 行 1・3〜6・9: 却下の POST の結果ごとの行き先（T-240。結果のすべてに行き先がある） ---

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> outcomes() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            new DecisionOutcome.Rejected(RECORD_ID), 302, REJECTED),
                    org.junit.jupiter.params.provider.Arguments.of(
                            new DecisionOutcome.AlreadyDecided(), 302, ALREADY_DECIDED),
                    org.junit.jupiter.params.provider.Arguments.of(new DecisionOutcome.NotFound(), 404, null),
                    org.junit.jupiter.params.provider.Arguments.of(new DecisionOutcome.NotDecidable(), 404, null),
                    org.junit.jupiter.params.provider.Arguments.of(new DecisionOutcome.NotPermitted(), 403, null));
        }

        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("outcomes")
        void 行1_行3から行6_行9_却下の結果ごとに一覧に戻って知らせるか404か403にする(DecisionOutcome outcome, int status, String notice)
                throws Exception {
            given(decisions.reject(any(), any())).willReturn(outcome);

            ResultActions result = postReason(POST_URL + "?ingestion=" + INGESTION_ID + "&page=2", "提供元に確かめた。")
                    .andExpect(status().is(status));

            if (notice != null) {
                result.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                                LIST_URL + "?ingestion=" + INGESTION_ID + "&page=2"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                                .attribute("result", notice));
            }
            then(decisions)
                    .should()
                    .reject(
                            any(),
                            org.mockito.ArgumentMatchers.eq(new RejectReceivedRecordCommand(RECORD_ID, "提供元に確かめた。")));
        }

        @Test
        void 行1_結果の型のすべてを行き先の組で縛る() {
            List<Class<?>> covered = outcomes()
                    .<Class<?>>map(arguments -> arguments.get()[0].getClass())
                    .toList();
            org.assertj.core.api.Assertions.assertThat(covered)
                    .containsExactlyInAnyOrder(DecisionOutcome.class.getPermittedSubclasses());
        }

        // --- 行 7・8: 理由の欄の誤り（T-244） ---

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", " ", "　", "\n"})
        void 行7_理由が空か空白だけなら判断の画面に戻り理由の欄に誤りを示してサービスを呼ばない(String reason) throws Exception {
            givenDecidable(mismatch());

            String page = postReason(POST_URL, reason)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            assertFieldError(page, REASON_REQUIRED);
            then(decisions).should(org.mockito.Mockito.never()).reject(any(), any());
        }

        @Test
        void 行8_補助文字を含む1000文字の理由はそのままサービスに渡す() throws Exception {
            String thousand = SUPPLEMENTARY.repeat(500) + "あ".repeat(500);
            given(decisions.reject(any(), any())).willReturn(new DecisionOutcome.Rejected(RECORD_ID));

            postReason(POST_URL, thousand).andExpect(status().is3xxRedirection());

            then(decisions)
                    .should()
                    .reject(
                            any(),
                            org.mockito.ArgumentMatchers.eq(new RejectReceivedRecordCommand(RECORD_ID, thousand)));
        }

        @Test
        void 行8_1001文字の理由は判断の画面に戻り長さの誤りを示して書いた理由を残す() throws Exception {
            String thousandAndOne = SUPPLEMENTARY.repeat(500) + "あ".repeat(501);
            givenDecidable(mismatch());

            String page = postReason(POST_URL, thousandAndOne)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            assertFieldError(page, REASON_TOO_LONG);
            org.assertj.core.api.Assertions.assertThat(page).contains(thousandAndOne + "</textarea>");
            then(decisions).should(org.mockito.Mockito.never()).reject(any(), any());
        }

        @Test
        void 行7_欄の誤りのあとに判断済みになっていれば一覧に戻って知らせる() throws Exception {
            given(decisions.refusalOf(RECORD_ID))
                    .willReturn(java.util.Optional.of(new DecisionOutcome.AlreadyDecided()));

            postReason(POST_URL, " ")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attribute("result", ALREADY_DECIDED));
        }

        /** エラー要約（理由の欄を指す）と欄の誤り（aria-invalid・aria-describedby で説明と誤りに結ぶ）。 */
        private void assertFieldError(String page, String message) {
            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<title>却下できませんでした - 隔離の判断 - cargo-tracker</title>")
                    .contains("<a href=\"#reason\">" + message + "</a>")
                    .contains("<p id=\"reason-error\" class=\"text-danger\">" + message + "</p>")
                    .containsPattern("<textarea[^>]*id=\"reason\"[^>]*aria-invalid=\"true\"")
                    .containsPattern("<textarea[^>]*aria-describedby=\"reason-hint reason-error\"");
        }
    }
}

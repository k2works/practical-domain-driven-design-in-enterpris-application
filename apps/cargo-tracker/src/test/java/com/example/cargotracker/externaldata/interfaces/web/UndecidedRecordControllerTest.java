package com.example.cargotracker.externaldata.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordPage;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordQueryService;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
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
                                        + "<th scope=\"col\">外部 ID・外部版</th>\\s*<th scope=\"col\">隔離の理由</th>.*"))))
                .andExpect(content()
                        .string(matchesPattern(Pattern.compile("(?s).*<tr>\\s*"
                                + "<td>2026-11-16 10:05 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 01:05）</td>\\s*"
                                + "<td>開発海運（架空）（主要実績）</td>\\s*"
                                // 行を持たないファイルの誤りの「—」は読み上げで「ファイル全体」と読む（Bolt 39d の開発レビュー）
                                + "<td><span aria-hidden=\"true\">—</span><span class=\"visually-hidden\">ファイル全体</span></td>\\s*"
                                + "<td>形式の誤り</td>\\s*"
                                + "<td><span aria-hidden=\"true\">—</span><span class=\"visually-hidden\">なし</span></td>\\s*"
                                + "<td>ファイル: 必要な列がありません（port_calls）。</td>\\s*</tr>\\s*<tr>\\s*"
                                + "<td>2026-11-16 10:05 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 01:05）</td>\\s*"
                                + "<td>開発海運（架空）（航海）</td>\\s*<td>140〜141 行目</td>\\s*<td>内容の違う原本</td>\\s*"
                                + "<td>DEMO-201・3</td>\\s*<td>140 行目: .*</td>\\s*</tr>.*"))));
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
}

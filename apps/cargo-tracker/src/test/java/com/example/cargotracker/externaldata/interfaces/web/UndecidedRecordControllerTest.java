package com.example.cargotracker.externaldata.interfaces.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.externaldata.application.internal.commands.AdoptReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectKeylessQuarantinesCommand;
import com.example.cargotracker.externaldata.application.internal.commands.RejectReceivedRecordCommand;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.DecisionOutcome.NotAdoptableReason;
import com.example.cargotracker.externaldata.application.internal.commandservices.KeylessRejectionOutcome;
import com.example.cargotracker.externaldata.application.internal.commandservices.ReceivedRecordDecisionService;
import com.example.cargotracker.externaldata.application.internal.queryservices.KeylessRejectionTarget;
import com.example.cargotracker.externaldata.application.internal.queryservices.QuarantineValues;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordPage;
import com.example.cargotracker.externaldata.application.internal.queryservices.UndecidedRecordQueryService;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordDecision;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.identity.infrastructure.security.WithAuthenticatedActor;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
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
        return new UndecidedRecordPage(items(shown), total, page, pageCount, filter, false, 0);
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
                .willReturn(new UndecidedRecordPage(List.of(fileError, mismatch), 2, 1, 1, null, false, 0));

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
        given(query.page(null, 1)).willReturn(new UndecidedRecordPage(List.of(keyless(102)), 1, 1, 1, null, false, 0));

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
        given(query.page(missing, 1)).willReturn(new UndecidedRecordPage(items(2), 2, 1, 1, null, true, 0));

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
        given(query.page(null, 1)).willReturn(new UndecidedRecordPage(List.of(item), 1, 1, 1, null, false, 0));

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
            // キーのない隔離は採用できない（Bolt 39d3b で採用の欄を足した。【業務の判断】(a)）
            given(decisions.adoptionRefusalOf(RECORD_ID))
                    .willReturn(
                            item.keyless()
                                    ? Optional.of(new DecisionOutcome.NotAdoptable(
                                            RECORD_ID, DecisionOutcome.NotAdoptableReason.KEYLESS))
                                    : Optional.empty());
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
                    .willReturn(new UndecidedRecordPage(List.of(item), 101, 2, 2, INGESTION_ID, false, 0));

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
                    .willReturn(new UndecidedRecordPage(List.of(keylessItem()), 1, 1, 1, null, false, 0));

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
        void 行1_却下が返す結果の型のすべてを行き先の組で縛る() {
            List<Class<?>> covered = outcomes()
                    .<Class<?>>map(arguments -> arguments.get()[0].getClass())
                    .toList();
            // 採用だけが返す結果（Bolt 39d3b）は Bolt39d3b2_採用できない理由と開いた後の案内 の行 14 が縛る
            List<Class<?>> rejectionOutcomes = Arrays.stream(DecisionOutcome.class.getPermittedSubclasses())
                    .filter(type -> type != DecisionOutcome.AdoptedQuarantine.class
                            && type != DecisionOutcome.NotAdoptable.class)
                    .toList();
            org.assertj.core.api.Assertions.assertThat(covered).containsExactlyInAnyOrderElementsOf(rejectionOutcomes);
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

    /**
     * Bolt 39d2b の行 1〜3・6・7・9・10・16・18・23（S-17 の形式の誤りのまとめての却下。開始準備の決定 (3)・(7)・(8)、T-240・T-244）。
     * 判断のサービスと照会は mock にし、一覧のリンクと知らせとページ送りの位置、まとめての却下の画面の中身と行き先（404・一覧に戻る・画面に
     * 戻る）、欄の誤りを確かめる。39d・39d2 の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d2b_形式の誤りのまとめての却下 {

        private static final String KEYLESS_URL = LIST_URL + "/keyless-rejection";
        private static final String SCREEN_URL = KEYLESS_URL + "?ingestion=" + INGESTION_ID;
        private static final String BACK_URL = LIST_URL + "?ingestion=" + INGESTION_ID + "&page=1";
        private static final String NOTHING_TO_REJECT = "この取込にまとめて却下できる形式の誤りはありません。";
        private static final String REASON_REQUIRED = "却下の理由を書いてください。";
        private static final String REASON_TOO_LONG = "却下の理由は 1000 文字までで書いてください。";
        private static final String EXCLUDES_MISMATCH = "内容の違う原本は含みません。一覧で 1 件ずつ判断してください。";
        private static final String KEYLESS_GUIDE = "形式の誤りは採用できません。直したファイルを取り込み直してください。";
        private static final String IRREVOCABLE = "却下すると取り消せません。誤って却下したときは、提供元に同じ原本を送り直してもらってください。";
        /** 補助文字（U+20BB7。UTF-16 で 2 単位）。 */
        private static final String SUPPLEMENTARY = new String(Character.toChars(0x20BB7));

        private static KeylessRejectionTarget target(int count) {
            return new KeylessRejectionTarget(INGESTION_ID, INGESTED_AT, "開発海運（架空）", "VOYAGE", count);
        }

        private static UndecidedRecordPage filtered(int total, int keylessCount) {
            int shown = Math.min(100, total);
            return new UndecidedRecordPage(
                    items(shown), total, 1, (total + 99) / 100, INGESTION_ID, false, keylessCount);
        }

        private static String linkOf(String count) {
            return "<a href=\"" + KEYLESS_URL + "?ingestion=" + INGESTION_ID + "\">この取込の形式の誤りをまとめて却下する（" + count
                    + " 件）</a>";
        }

        private String body(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
            return mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        }

        private ResultActions postReason(String url, String reason) throws Exception {
            return mockMvc.perform(post(url).param("reason", reason));
        }

        // --- 行 1・2・6・18: 一覧のリンクとページ送りの位置 ---

        @Test
        void 行1_取込で絞った一覧で形式の誤りがあれば件数の文の後にまとめての却下のリンクを出す() throws Exception {
            given(query.page(INGESTION_ID, 1)).willReturn(filtered(3, 2));

            String page = body(get(LIST_URL + "?ingestion=" + INGESTION_ID));

            org.assertj.core.api.Assertions.assertThat(page).contains(linkOf("2"));
            org.assertj.core.api.Assertions.assertThat(page.indexOf("この取込の判断を待つ隔離 3 件"))
                    .isLessThan(page.indexOf(linkOf("2")));
        }

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> withoutLink() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            "絞らない一覧", null, new UndecidedRecordPage(items(2), 2, 1, 1, null, false, 0)),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "取込が見つからない一覧", INGESTION_ID, new UndecidedRecordPage(items(2), 2, 1, 1, null, true, 0)),
                    org.junit.jupiter.params.provider.Arguments.of("形式の誤りが 0 件の取込", INGESTION_ID, filtered(1, 0)),
                    // 照会が絞らない一覧に件数を持たせても、画面は取込で絞っていなければリンクを出さない（守りは照会と画面の 2 か所）
                    org.junit.jupiter.params.provider.Arguments.of(
                            "照会が件数を持たせた絞らない一覧", null, new UndecidedRecordPage(items(2), 2, 1, 1, null, false, 2)),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "照会が件数を持たせた取込が見つからない一覧",
                            INGESTION_ID,
                            new UndecidedRecordPage(items(2), 2, 1, 1, null, true, 2)));
        }

        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("withoutLink")
        void 行2_絞らない一覧と取込が見つからない一覧と形式の誤りが0件の取込にはまとめての却下のリンクを出さない(String name, UUID filter, UndecidedRecordPage result)
                throws Exception {
            given(query.page(filter, 1)).willReturn(result);

            list(filter == null ? "" : "?ingestion=" + filter)
                    .andExpect(content().string(not(containsString("まとめて却下する"))))
                    .andExpect(content().string(not(containsString("keyless-rejection"))));
        }

        @ParameterizedTest(name = "{0} 件")
        @CsvSource({"1, 1", "1000, '1,000'", "10001, '10,001'"})
        void 行6_リンクと画面と知らせの件数を3桁の区切りで示す(int count, String grouped) throws Exception {
            given(query.page(INGESTION_ID, 1)).willReturn(filtered(count, count));
            given(query.keylessRejectionTarget(INGESTION_ID)).willReturn(java.util.Optional.of(target(count)));
            given(decisions.rejectKeyless(any(), any())).willReturn(new KeylessRejectionOutcome.RejectedAll(count));

            list("?ingestion=" + INGESTION_ID).andExpect(content().string(containsString(linkOf(grouped))));
            org.assertj.core.api.Assertions.assertThat(body(get(SCREEN_URL)))
                    .containsPattern("<dt>形式の誤り</dt>\\s*<dd>" + grouped + " 件</dd>");
            postReason(SCREEN_URL, "直したファイルを取り込み直した。")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attribute("result", "形式の誤り " + grouped + " 件を却下しました。"));
        }

        @Test
        void 行18_ページ送りは件数の文とまとめての却下のリンクとすべての取込の隔離を見るの後で表の前に1つだけある() throws Exception {
            UndecidedRecordPage second = new UndecidedRecordPage(items(100), 10001, 2, 101, INGESTION_ID, false, 10000);
            given(query.page(INGESTION_ID, 2)).willReturn(second);

            String page = body(get(LIST_URL + "?ingestion=" + INGESTION_ID + "&page=2"));

            int count = page.indexOf("この取込の判断を待つ隔離 10,001 件");
            int link = page.indexOf(linkOf("10,000"));
            int all = page.indexOf(">すべての取込の隔離を見る</a>");
            String navTag = "<nav aria-label=\"判断を待つ隔離のページ\">";
            int nav = page.indexOf(navTag);
            int table = page.indexOf(TABLE);
            org.assertj.core.api.Assertions.assertThat(List.of(count, link, all, nav, table))
                    .doesNotContain(-1)
                    .isSorted();
            org.assertj.core.api.Assertions.assertThat(page.indexOf("判断を待つ隔離のページ", nav + navTag.length()))
                    .isEqualTo(-1);
        }

        // --- 行 16: 件数が減って 0 件になった一覧 ---

        @Test
        void 行16_照会が0件を返せば取込で絞った0件の文を示し0から0件目を出さない() throws Exception {
            given(query.page(INGESTION_ID, 3))
                    .willReturn(new UndecidedRecordPage(List.of(), 0, 1, 0, INGESTION_ID, false, 0));

            list("?ingestion=" + INGESTION_ID + "&page=3")
                    .andExpect(content().string(containsString("<p>この取込に判断を待つ隔離はありません。</p>")))
                    .andExpect(content().string(not(containsString("0〜0 件目"))))
                    .andExpect(content().string(not(containsString("<table"))));
        }

        // --- 行 3: まとめての却下の画面の中身 ---

        @Test
        void 行3_まとめての却下の画面は取込と情報源と件数と含まないものと取り消せないことと理由の欄を示す() throws Exception {
            given(query.keylessRejectionTarget(INGESTION_ID)).willReturn(java.util.Optional.of(target(2)));

            String page = mockMvc.perform(get(SCREEN_URL))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                            .attribute("reasonLimit", ReceivedRecordDecision.REASON_LIMIT))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<title>形式の誤りのまとめての却下 - cargo-tracker</title>")
                    .contains("<h1>形式の誤りのまとめての却下</h1>")
                    .containsPattern(
                            "<dt>取り込んだ日時</dt>\\s*<dd>2026-11-16 10:05 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 01:05）</dd>")
                    .containsPattern("<dt>情報源</dt>\\s*<dd>開発海運（架空）（航海）</dd>")
                    .containsPattern("<dt>形式の誤り</dt>\\s*<dd>2 件</dd>")
                    .contains("この取込の判断を待つ形式の誤りをすべて、同じ理由で却下します。")
                    .contains(EXCLUDES_MISMATCH)
                    .contains(KEYLESS_GUIDE)
                    .contains("<label for=\"reason\">却下の理由</label>")
                    .contains("<p id=\"reason-hint\">必須、" + ReceivedRecordDecision.REASON_LIMIT + " 文字まで</p>")
                    .containsPattern("<textarea[^>]*id=\"reason\"[^>]*aria-required=\"true\"")
                    .contains(IRREVOCABLE)
                    .contains("action=\"" + SCREEN_URL + "\"")
                    .contains(">まとめて却下する</button>")
                    .contains("<a href=\"" + LIST_URL + "?ingestion=" + INGESTION_ID + "&amp;page=1\">一覧に戻る</a>")
                    .doesNotContain("error-summary");
        }

        // --- 行 9・10: 画面の行き先 ---

        @Test
        void 行9_画面を開いた時点で0件なら取込で絞った一覧に戻ってまとめて却下できる形式の誤りはないと知らせる() throws Exception {
            given(query.keylessRejectionTarget(INGESTION_ID)).willReturn(java.util.Optional.of(target(0)));

            mockMvc.perform(get(SCREEN_URL))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(BACK_URL))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attribute("result", NOTHING_TO_REJECT));
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(
                strings = {"", "?ingestion=", "?ingestion=not-a-uuid", "?ingestion=39d2b000-0000-0000-0000-0000000000cc"
                })
        void 行10_取込の指定がないかUUIDでないかない取込ならGETもPOSTも404で判断の画面に入らない(String parameter) throws Exception {
            given(query.keylessRejectionTarget(any())).willReturn(java.util.Optional.empty());
            given(decisions.rejectKeyless(any(), any())).willReturn(new KeylessRejectionOutcome.NotFound());

            mockMvc.perform(get(KEYLESS_URL + parameter)).andExpect(status().isNotFound());
            postReason(KEYLESS_URL + parameter, "理由").andExpect(status().isNotFound());
            // keyless-rejection は判断の画面の /{受信記録 ID} より先に選ばれる（受信記録 ID として読まない）
            then(decisions).should(org.mockito.Mockito.never()).refusalOf(any());
            then(query).should(org.mockito.Mockito.never()).undecidedQuarantine(any());
        }

        // --- 行 1・4・9〜11: POST の結果ごとの行き先（T-240。結果のすべてに行き先がある） ---

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> outcomes() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            new KeylessRejectionOutcome.RejectedAll(2), 302, "形式の誤り 2 件を却下しました。"),
                    org.junit.jupiter.params.provider.Arguments.of(
                            new KeylessRejectionOutcome.NothingToReject(), 302, NOTHING_TO_REJECT),
                    org.junit.jupiter.params.provider.Arguments.of(new KeylessRejectionOutcome.NotFound(), 404, null),
                    org.junit.jupiter.params.provider.Arguments.of(
                            new KeylessRejectionOutcome.NotPermitted(), 403, null));
        }

        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("outcomes")
        void 行4_行9_行10_行11_まとめての却下の結果ごとに取込で絞った一覧に戻って知らせるか404か403にする(
                KeylessRejectionOutcome outcome, int status, String notice) throws Exception {
            given(decisions.rejectKeyless(any(), any())).willReturn(outcome);

            ResultActions result =
                    postReason(SCREEN_URL, "列の数を直したファイルを取り込み直した。").andExpect(status().is(status));

            if (notice != null) {
                result.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                                BACK_URL))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                                .attribute("result", notice));
            }
            then(decisions)
                    .should()
                    .rejectKeyless(
                            any(),
                            org.mockito.ArgumentMatchers.eq(
                                    new RejectKeylessQuarantinesCommand(INGESTION_ID, "列の数を直したファイルを取り込み直した。")));
        }

        @Test
        void 行1_まとめての却下の結果の型のすべてを行き先の組で縛る() {
            List<Class<?>> covered = outcomes()
                    .<Class<?>>map(arguments -> arguments.get()[0].getClass())
                    .toList();
            org.assertj.core.api.Assertions.assertThat(covered)
                    .containsExactlyInAnyOrder(KeylessRejectionOutcome.class.getPermittedSubclasses());
        }

        @Test
        void 行4_一覧はまとめての却下の知らせを状態の領域に示す() throws Exception {
            given(query.page(INGESTION_ID, 1)).willReturn(filtered(1, 0));

            mockMvc.perform(get(BACK_URL).flashAttr("result", "形式の誤り 2 件を却下しました。"))
                    .andExpect(content().string(containsString("<p role=\"status\">形式の誤り 2 件を却下しました。</p>")));
        }

        // --- 行 7・23: 理由の欄の誤り（T-244） ---

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidReasons() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of("空", "", REASON_REQUIRED),
                    org.junit.jupiter.params.provider.Arguments.of("半角の空白だけ", " ", REASON_REQUIRED),
                    org.junit.jupiter.params.provider.Arguments.of("全角の空白だけ", "　", REASON_REQUIRED),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "補助文字を含む 1001 文字", SUPPLEMENTARY.repeat(500) + "あ".repeat(501), REASON_TOO_LONG));
        }

        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("invalidReasons")
        void 行7_理由が空か空白だけか1001文字ならまとめての却下の画面に戻り欄の誤りを示してサービスを呼ばない(String name, String reason, String message)
                throws Exception {
            given(query.keylessRejectionTarget(INGESTION_ID)).willReturn(java.util.Optional.of(target(2)));

            String page = mockMvc.perform(post(SCREEN_URL).param("reason", reason))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<title>却下できませんでした - 形式の誤りのまとめての却下 - cargo-tracker</title>")
                    .contains("<a href=\"#reason\">" + message + "</a>")
                    .contains("<p id=\"reason-error\" class=\"text-danger\">" + message + "</p>")
                    .containsPattern("<textarea[^>]*id=\"reason\"[^>]*aria-invalid=\"true\"")
                    .containsPattern("<textarea[^>]*aria-describedby=\"reason-hint reason-error\"")
                    .containsPattern("<dt>形式の誤り</dt>\\s*<dd>2 件</dd>");
            then(decisions).should(org.mockito.Mockito.never()).rejectKeyless(any(), any());
        }

        @Test
        void 行7_補助文字を含む1000文字の理由はそのままサービスに渡す() throws Exception {
            String thousand = SUPPLEMENTARY.repeat(500) + "あ".repeat(500);
            given(decisions.rejectKeyless(any(), any())).willReturn(new KeylessRejectionOutcome.RejectedAll(2));

            postReason(SCREEN_URL, thousand).andExpect(status().is3xxRedirection());

            then(decisions)
                    .should()
                    .rejectKeyless(
                            any(),
                            org.mockito.ArgumentMatchers.eq(
                                    new RejectKeylessQuarantinesCommand(INGESTION_ID, thousand)));
        }

        @Test
        void 行23_理由の誤りのときに対象が0件になっていれば取込で絞った一覧に戻って知らせる() throws Exception {
            given(query.keylessRejectionTarget(INGESTION_ID)).willReturn(java.util.Optional.of(target(0)));

            postReason(SCREEN_URL, " ")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(BACK_URL))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attribute("result", NOTHING_TO_REJECT));
            then(query).should().keylessRejectionTarget(INGESTION_ID);
            then(decisions).should(org.mockito.Mockito.never()).rejectKeyless(any(), any());
        }

        @Test
        void 行23_理由の誤りのときに取込がなくなっていれば404() throws Exception {
            given(query.keylessRejectionTarget(INGESTION_ID)).willReturn(java.util.Optional.empty());

            postReason(SCREEN_URL, "").andExpect(status().isNotFound());
            then(query).should().keylessRejectionTarget(INGESTION_ID);
            then(decisions).should(org.mockito.Mockito.never()).rejectKeyless(any(), any());
        }
    }

    /**
     * Bolt 39d3 の行 9〜12・20〜22（判断の画面の航海の値の領域。開始準備の決定 (6)・(7)、【業務の判断】(i)・(j)）。照会は mock にし、値の
     * 照会の結果を領域と文に変える振る舞いだけを見る。値を記録する前の隔離（行 10）とこの外部版の採用値がない隔離（行 11）は UI で作れない
     * ので、ここで確かめる（T-243）。主要実績の値と値を読めない文は {@link Bolt39d3c_判断の画面の主要実績と読めない値}。行 22 の文は Bolt 39d3c で
     * 「取得した日時」に語をそろえた。
     */
    @Nested
    class Bolt39d3_判断の画面の値 {

        private static final UUID RECORD_ID = UUID.fromString("39d30000-0000-0000-0000-0000000000cc");
        private static final String DECISION_URL = LIST_URL + "/" + RECORD_ID;
        private static final String RECEIVED_SECTION =
                "<section aria-labelledby=\"received-values-heading\">\\s*<h2 id=\"received-values-heading\">受け取った値</h2>";
        private static final String ADOPTED_SECTION =
                "<section aria-labelledby=\"adopted-values-heading\">\\s*<h2 id=\"adopted-values-heading\">この外部版の採用値</h2>";
        private static final String SAME_VALUES = "受け取った値はこの外部版の採用値と同じです。原本の書き方（引用符・空白など）だけが違います。";
        private static final String SAME_VALUES_BUT_ACQUIRED_AT = "受け取った値はこの外部版の採用値と同じで、取得した日時だけが違います。";
        private static final String NOT_RECORDED = "この隔離は、受け取った値を記録する前に取り込みました。値は原本のファイルで確かめてください。";
        private static final String NO_ADOPTED = "この外部版の採用値はありません。";
        private static final Instant RECEIVED_ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final Instant ADOPTED_ACQUIRED_AT = Instant.parse("2026-11-15T08:30:00Z");
        private static final Instant PUBLISHED_AT = Instant.parse("2026-11-15T09:00:00Z");

        private UndecidedRecordPage.Item mismatch(String recordType) {
            return new UndecidedRecordPage.Item(
                    RECORD_ID,
                    INGESTED_AT,
                    "開発海運（架空）",
                    recordType,
                    new PhysicalLineRange(140, 140),
                    false,
                    "DEMO-201",
                    "3",
                    "140 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-15 09:00:00 UTC）。");
        }

        /** 東京を出発し、ロサンゼルスに到着する寄港 2 つの航海（ロサンゼルスの到着の時刻だけを変えられる。T-230）。 */
        private static VoyageValues voyage(String voyageNumber, String losAngelesArrival) {
            return new VoyageValues(
                    voyageNumber,
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-18T23:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"), new UtcInstant(Instant.parse(losAngelesArrival)), null)));
        }

        private static QuarantineValues.Adopted adopted(VoyageValues values, Instant acquiredAt) {
            return new QuarantineValues.Adopted(values, "3", PUBLISHED_AT, acquiredAt);
        }

        private String decisionPageWith(UndecidedRecordPage.Item item, QuarantineValues values) throws Exception {
            given(decisions.refusalOf(RECORD_ID)).willReturn(java.util.Optional.empty());
            given(query.undecidedQuarantine(RECORD_ID)).willReturn(java.util.Optional.of(item));
            given(query.quarantineValues(item)).willReturn(java.util.Optional.ofNullable(values));
            return mockMvc.perform(get(DECISION_URL))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        }

        private static int indexOf(String page, String regex) {
            java.util.regex.Matcher matcher = Pattern.compile(regex).matcher(page);
            org.assertj.core.api.Assertions.assertThat(matcher.find()).as(regex).isTrue();
            return matcher.start();
        }

        // --- 行 9: 値の領域の中身と順 ---

        @Test
        void 行9_隔離の理由の後に受け取った値とこの外部版の採用値をそれぞれの取得した日時と航海番号と寄港の番号付きの並びで示し却下の欄が続く() throws Exception {
            String page = decisionPageWith(
                    mismatch("VOYAGE"),
                    new QuarantineValues(
                            voyage("DEMO-201", "2026-12-01T22:00:00Z"),
                            RECEIVED_ACQUIRED_AT,
                            adopted(voyage("DEMO-201", "2026-12-01T14:00:00Z"), ADOPTED_ACQUIRED_AT)));

            String received = RECEIVED_SECTION
                    + "\\s*<p>取得した日時 2026-11-16 09:30 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 00:30）</p>"
                    + "\\s*<dl class=\"text-break\">\\s*<dt>航海番号</dt>\\s*<dd>DEMO-201</dd>\\s*</dl>"
                    + "\\s*<ol class=\"text-break\">"
                    + "\\s*<li>港 JPTYO・到着 —・出発 2026-11-19 08:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-18 23:00）</li>"
                    + "\\s*<li>港 USLAX・到着 2026-12-02 07:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-12-01 22:00）・出発 —</li>"
                    + "\\s*</ol>\\s*</section>";
            String adopted = ADOPTED_SECTION
                    + "\\s*<p>情報版 3・公開した日時 2026-11-15 18:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-15 09:00）・取得した日時"
                    + " 2026-11-15 17:30 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-15 08:30）</p>"
                    + "\\s*<dl class=\"text-break\">\\s*<dt>航海番号</dt>\\s*<dd>DEMO-201</dd>\\s*</dl>"
                    + "\\s*<ol class=\"text-break\">"
                    + "\\s*<li>港 JPTYO・到着 —・出発 2026-11-19 08:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-18 23:00）</li>"
                    + "\\s*<li>港 USLAX・到着 2026-12-01 23:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-12-01 14:00）・出発 —</li>"
                    + "\\s*</ol>\\s*</section>";
            org.assertj.core.api.Assertions.assertThat(page)
                    .containsPattern(received)
                    .containsPattern(adopted);
            int note = indexOf(page, "<dt>隔離の理由</dt>");
            int receivedAt = indexOf(page, RECEIVED_SECTION);
            int adoptedAt = indexOf(page, ADOPTED_SECTION);
            int form = indexOf(page, "<label for=\"reason\">却下の理由</label>");
            org.assertj.core.api.Assertions.assertThat(List.of(note, receivedAt, adoptedAt, form))
                    .isSorted();
            org.assertj.core.api.Assertions.assertThat(page)
                    .doesNotContain(SAME_VALUES)
                    .doesNotContain(SAME_VALUES_BUT_ACQUIRED_AT);
        }

        @Test
        void 行9_取込ファイルから来た航海番号は文字として示す() throws Exception {
            String voyageNumber = "<b>DEMO-201</b>";

            String page = decisionPageWith(
                    mismatch("VOYAGE"),
                    new QuarantineValues(
                            voyage(voyageNumber, "2026-12-01T22:00:00Z"),
                            RECEIVED_ACQUIRED_AT,
                            adopted(voyage("DEMO-201", "2026-12-01T14:00:00Z"), ADOPTED_ACQUIRED_AT)));

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<dd>&lt;b&gt;DEMO-201&lt;/b&gt;</dd>")
                    .doesNotContain(voyageNumber);
        }

        // --- 行 10・11: 値のない隔離、この外部版の採用値がない隔離（T-243） ---

        @Test
        void 行10_値を記録する前に取り込んだ隔離は受け取った値の領域にその旨を示しこの外部版の採用値は示す() throws Exception {
            String page = decisionPageWith(
                    mismatch("VOYAGE"),
                    new QuarantineValues(
                            null,
                            RECEIVED_ACQUIRED_AT,
                            adopted(voyage("DEMO-201", "2026-12-01T14:00:00Z"), ADOPTED_ACQUIRED_AT)));

            org.assertj.core.api.Assertions.assertThat(page)
                    .containsPattern(RECEIVED_SECTION + "\\s*<p>" + NOT_RECORDED + "</p>\\s*</section>")
                    .containsPattern(ADOPTED_SECTION + "\\s*<p>情報版 3・公開した日時 ")
                    .contains("<li>港 USLAX・到着 2026-12-01 23:00 Asia/Tokyo（UTC+09:00）（UTC 2026-12-01 14:00）・出発 —</li>")
                    .doesNotContain("取得した日時 2026-11-16 09:30")
                    .doesNotContain(SAME_VALUES)
                    .doesNotContain(SAME_VALUES_BUT_ACQUIRED_AT);
        }

        @Test
        void 行11_この外部版の採用値がない隔離はその領域にないことを示し同じ値の文を出さない() throws Exception {
            String page = decisionPageWith(
                    mismatch("VOYAGE"),
                    new QuarantineValues(voyage("DEMO-201", "2026-12-01T22:00:00Z"), RECEIVED_ACQUIRED_AT, null));

            org.assertj.core.api.Assertions.assertThat(page)
                    .containsPattern(RECEIVED_SECTION + "\\s*<p>取得した日時 2026-11-16 09:30 ")
                    .containsPattern(ADOPTED_SECTION + "\\s*<p>" + NO_ADOPTED + "</p>\\s*</section>")
                    .doesNotContain("情報版 ")
                    .doesNotContain(SAME_VALUES)
                    .doesNotContain(SAME_VALUES_BUT_ACQUIRED_AT);
        }

        // --- 行 12・20: 値の領域を出さない隔離と、判断の画面の行き先 ---

        @ParameterizedTest(name = "{0}")
        @CsvSource({"キーのない隔離（形式の誤り）, VOYAGE, true"})
        void 行12_値の照会が空なら値の領域を出さず判断の画面と却下の欄は今のまま(String name, String recordType, boolean keyless) throws Exception {
            UndecidedRecordPage.Item item = new UndecidedRecordPage.Item(
                    RECORD_ID,
                    INGESTED_AT,
                    "開発海運（架空）",
                    recordType,
                    new PhysicalLineRange(4, 4),
                    keyless,
                    keyless ? null : "MS-0001",
                    keyless ? null : "1",
                    "4 行目: 注記");
            // キーのない隔離の案内は採用できない理由の文（Bolt 39d3b2）
            given(decisions.adoptionRefusalOf(RECORD_ID))
                    .willReturn(Optional.of(new DecisionOutcome.NotAdoptable(RECORD_ID, NotAdoptableReason.KEYLESS)));

            String page = decisionPageWith(item, null);

            org.assertj.core.api.Assertions.assertThat(page)
                    .contains("<h1>隔離の判断</h1>")
                    .doesNotContain("受け取った値")
                    .doesNotContain("この外部版の採用値")
                    .contains("<label for=\"reason\">却下の理由</label>")
                    .contains("<p><a href=\"" + LIST_URL + "?page=1\">一覧に戻る</a></p>");
            org.assertj.core.api.Assertions.assertThat(page.contains("形式の誤りは採用できません。"))
                    .isEqualTo(keyless);
        }

        @Test
        void 行20_判断の画面の行き先は判断のサービスが決め値の照会を引かない() throws Exception {
            given(decisions.refusalOf(RECORD_ID)).willReturn(java.util.Optional.of(new DecisionOutcome.NotDecidable()));

            mockMvc.perform(get(DECISION_URL)).andExpect(status().isNotFound());

            then(query).should(org.mockito.Mockito.never()).quarantineValues(any());
        }

        // --- 行 21・22: 同じ値の 2 つの文（V7・V8。T-244） ---

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> sameValueCases() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            "値も取得時刻も同じ", "2026-12-01T22:00:00Z", RECEIVED_ACQUIRED_AT, SAME_VALUES),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "値は同じで取得時刻が違う", "2026-12-01T22:00:00Z", ADOPTED_ACQUIRED_AT, SAME_VALUES_BUT_ACQUIRED_AT),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "値が違い取得時刻は同じ", "2026-12-01T14:00:00Z", RECEIVED_ACQUIRED_AT, null),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "値も取得時刻も違う", "2026-12-01T14:00:00Z", ADOPTED_ACQUIRED_AT, null));
        }

        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("sameValueCases")
        void 行21_行22_値と取得時刻の組で同じ値の文を選び2つの領域の前に示す(
                String name, String adoptedLosAngelesArrival, Instant adoptedAcquiredAt, String expected)
                throws Exception {
            String page = decisionPageWith(
                    mismatch("VOYAGE"),
                    new QuarantineValues(
                            voyage("DEMO-201", "2026-12-01T22:00:00Z"),
                            RECEIVED_ACQUIRED_AT,
                            adopted(voyage("DEMO-201", adoptedLosAngelesArrival), adoptedAcquiredAt)));

            for (String sentence : List.of(SAME_VALUES, SAME_VALUES_BUT_ACQUIRED_AT)) {
                org.assertj.core.api.Assertions.assertThat(page.contains("<p>" + sentence + "</p>"))
                        .as(sentence)
                        .isEqualTo(sentence.equals(expected));
            }
            if (expected != null) {
                org.assertj.core.api.Assertions.assertThat(indexOf(page, Pattern.quote(expected)))
                        .isLessThan(indexOf(page, RECEIVED_SECTION));
            }
        }
    }

    /**
     * Bolt 39d3c の行 14・23・24（判断の画面の主要実績の値と読めない値。開始準備の決定 (4)・(5)、【業務の判断】(k)）。照会は mock にし、値の照会の
     * 結果を領域と文に変える振る舞いだけを見る。読めない値は UI で作れないので、ここで確かめる（T-243）。
     */
    @Nested
    class Bolt39d3c_判断の画面の主要実績と読めない値 {

        private static final UUID RECORD_ID = UUID.fromString("39d3c000-0000-0000-0000-0000000000cc");
        private static final String DECISION_URL = LIST_URL + "/" + RECORD_ID;
        private static final String RECEIVED_SECTION =
                "<section aria-labelledby=\"received-values-heading\">\\s*<h2 id=\"received-values-heading\">受け取った値</h2>";
        private static final String ADOPTED_SECTION =
                "<section aria-labelledby=\"adopted-values-heading\">\\s*<h2 id=\"adopted-values-heading\">この外部版の採用値</h2>";
        private static final String RECEIVED_META =
                "<p>取得した日時 2026-11-16 09:30 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-16 00:30）</p>";
        private static final String ADOPTED_META =
                "<p>情報版 2・公開した日時 2026-11-15 18:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-15 09:00）・取得した日時"
                        + " 2026-11-15 17:30 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-15 08:30）</p>";
        private static final String ILLEGIBLE = "値を読めませんでした。";
        private static final String NOT_RECORDED = "この隔離は、受け取った値を記録する前に取り込みました。値は原本のファイルで確かめてください。";
        private static final String NO_ADOPTED = "この外部版の採用値はありません。";
        private static final String SAME_VALUES = "受け取った値はこの外部版の採用値と同じです。原本の書き方（引用符・空白など）だけが違います。";
        private static final String SAME_VALUES_BUT_ACQUIRED_AT = "受け取った値はこの外部版の採用値と同じで、取得した日時だけが違います。";
        private static final Instant RECEIVED_ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final Instant ADOPTED_ACQUIRED_AT = Instant.parse("2026-11-15T08:30:00Z");
        private static final Instant PUBLISHED_AT = Instant.parse("2026-11-15T09:00:00Z");

        private UndecidedRecordPage.Item mismatch(String recordType) {
            return new UndecidedRecordPage.Item(
                    RECORD_ID,
                    INGESTED_AT,
                    "開発港運（架空）",
                    recordType,
                    new PhysicalLineRange(3, 3),
                    false,
                    "MS-0201",
                    "2",
                    "3 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました（最初の受信: 2026-11-15 09:00:00 UTC）。");
        }

        /** 東京の出発（発生時刻だけを変えられる。T-230）。 */
        private static com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues milestone(
                String occurredAt) {
            return new com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues(
                    new com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue(
                            "CTABCDEFGH2345"),
                    com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue.DEPARTURE,
                    new Location("JPTYO"),
                    new UtcInstant(Instant.parse(occurredAt)));
        }

        private static VoyageValues voyage(String losAngelesArrival) {
            return new VoyageValues(
                    "DEMO-201",
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-18T23:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"), new UtcInstant(Instant.parse(losAngelesArrival)), null)));
        }

        private static com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues valuesOf(
                String recordType) {
            return "MILESTONE".equals(recordType) ? milestone("2026-11-19T00:00:00Z") : voyage("2026-12-01T14:00:00Z");
        }

        private static QuarantineValues.Adopted adopted(
                com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues values,
                Instant acquiredAt) {
            return new QuarantineValues.Adopted(values, "2", PUBLISHED_AT, acquiredAt);
        }

        private static QuarantineValues.Adopted illegibleAdopted() {
            return new QuarantineValues.Adopted(null, true, "2", PUBLISHED_AT, ADOPTED_ACQUIRED_AT);
        }

        private String decisionPageWith(UndecidedRecordPage.Item item, QuarantineValues values) throws Exception {
            given(decisions.refusalOf(RECORD_ID)).willReturn(java.util.Optional.empty());
            given(query.undecidedQuarantine(RECORD_ID)).willReturn(java.util.Optional.of(item));
            given(query.quarantineValues(item)).willReturn(java.util.Optional.ofNullable(values));
            return mockMvc.perform(get(DECISION_URL))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        }

        private static String milestoneList(String occurredAt) {
            return "\\s*<dl class=\"text-break\">"
                    + "\\s*<dt>追跡番号</dt>\\s*<dd>CTABCDEFGH2345</dd>"
                    + "\\s*<dt>実績の種類</dt>\\s*<dd>出発</dd>"
                    + "\\s*<dt>場所</dt>\\s*<dd>JPTYO</dd>"
                    + "\\s*<dt>発生時刻</dt>\\s*<dd>" + occurredAt + "</dd>"
                    + "\\s*</dl>\\s*</section>";
        }

        // --- 行 14: 主要実績の値の領域 ---

        @Test
        void 行14_主要実績は追跡番号と実績の種類と場所と発生時刻を取得した日時の後に示し寄港の並びを出さない() throws Exception {
            String page = decisionPageWith(
                    mismatch("MILESTONE"),
                    new QuarantineValues(
                            milestone("2026-11-19T01:00:00Z"),
                            RECEIVED_ACQUIRED_AT,
                            adopted(milestone("2026-11-19T00:00:00Z"), ADOPTED_ACQUIRED_AT)));

            org.assertj.core.api.Assertions.assertThat(page)
                    .containsPattern(RECEIVED_SECTION + "\\s*" + RECEIVED_META
                            + milestoneList("2026-11-19 10:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-19 01:00）"))
                    .containsPattern(ADOPTED_SECTION + "\\s*" + ADOPTED_META
                            + milestoneList("2026-11-19 09:00 Asia/Tokyo（UTC\\+09:00）（UTC 2026-11-19 00:00）"))
                    .doesNotContain("<ol")
                    .doesNotContain(SAME_VALUES)
                    .doesNotContain(SAME_VALUES_BUT_ACQUIRED_AT)
                    .contains("<label for=\"reason\">却下の理由</label>");
        }

        // --- 行 23: 読めない値（T-243・T-244） ---

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> illegibleCases() {
            String receivedIllegible =
                    RECEIVED_SECTION + "\\s*" + RECEIVED_META + "\\s*<p>" + ILLEGIBLE + "</p>\\s*</section>";
            String adoptedIllegible =
                    ADOPTED_SECTION + "\\s*" + ADOPTED_META + "\\s*<p>" + ILLEGIBLE + "</p>\\s*</section>";
            String receivedNotRecorded = RECEIVED_SECTION + "\\s*<p>" + NOT_RECORDED + "</p>\\s*</section>";
            String adoptedNone = ADOPTED_SECTION + "\\s*<p>" + NO_ADOPTED + "</p>\\s*</section>";
            String receivedShown = RECEIVED_SECTION + "\\s*" + RECEIVED_META + "\\s*<dl class=\"text-break\">";
            String adoptedShown = ADOPTED_SECTION + "\\s*" + ADOPTED_META + "\\s*<dl class=\"text-break\">";
            java.util.List<org.junit.jupiter.params.provider.Arguments> cases = new java.util.ArrayList<>();
            for (String type : List.of("VOYAGE", "MILESTONE")) {
                cases.add(org.junit.jupiter.params.provider.Arguments.of(
                        type + " (a) 受け取った値が読めない",
                        type,
                        new QuarantineValues(
                                null, true, RECEIVED_ACQUIRED_AT, adopted(valuesOf(type), ADOPTED_ACQUIRED_AT)),
                        receivedIllegible,
                        adoptedShown));
                cases.add(org.junit.jupiter.params.provider.Arguments.of(
                        type + " (b) 採用値が読めない",
                        type,
                        new QuarantineValues(valuesOf(type), false, RECEIVED_ACQUIRED_AT, illegibleAdopted()),
                        receivedShown,
                        adoptedIllegible));
                cases.add(org.junit.jupiter.params.provider.Arguments.of(
                        type + " (a)・(b) 両側が読めない",
                        type,
                        new QuarantineValues(null, true, RECEIVED_ACQUIRED_AT, illegibleAdopted()),
                        receivedIllegible,
                        adoptedIllegible));
            }
            cases.add(org.junit.jupiter.params.provider.Arguments.of(
                    "(c) 知らない原本種別",
                    "CONTAINER",
                    new QuarantineValues(
                            null, true, RECEIVED_ACQUIRED_AT, adopted(valuesOf("VOYAGE"), ADOPTED_ACQUIRED_AT)),
                    receivedIllegible,
                    adoptedShown));
            cases.add(org.junit.jupiter.params.provider.Arguments.of(
                    "値の列がない × 知らない原本種別",
                    "CONTAINER",
                    new QuarantineValues(
                            null, false, RECEIVED_ACQUIRED_AT, adopted(valuesOf("VOYAGE"), ADOPTED_ACQUIRED_AT)),
                    receivedNotRecorded,
                    adoptedShown));
            cases.add(org.junit.jupiter.params.provider.Arguments.of(
                    "値の列がない × 採用値が読めない",
                    "MILESTONE",
                    new QuarantineValues(null, false, RECEIVED_ACQUIRED_AT, illegibleAdopted()),
                    receivedNotRecorded,
                    adoptedIllegible));
            cases.add(org.junit.jupiter.params.provider.Arguments.of(
                    "受け取った値が読めない × 採用値がない",
                    "MILESTONE",
                    new QuarantineValues(null, true, RECEIVED_ACQUIRED_AT, null),
                    receivedIllegible,
                    adoptedNone));
            return cases.stream();
        }

        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("illegibleCases")
        void 行23_読めない側は取得した日時の後に値を読めない文を示し読める側は値を示して却下の欄を残す(
                String name, String recordType, QuarantineValues values, String receivedPattern, String adoptedPattern)
                throws Exception {
            String page = decisionPageWith(mismatch(recordType), values);

            org.assertj.core.api.Assertions.assertThat(page)
                    .containsPattern(receivedPattern)
                    .containsPattern(adoptedPattern)
                    .doesNotContain(SAME_VALUES)
                    .doesNotContain(SAME_VALUES_BUT_ACQUIRED_AT)
                    .contains("<label for=\"reason\">却下の理由</label>")
                    .contains("却下すると取り消せません。");
        }

        // --- 行 24: 主要実績の同じ値の 2 つの文 ---

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> sameMilestoneCases() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            "値も取得時刻も同じ", "2026-11-19T01:00:00Z", RECEIVED_ACQUIRED_AT, SAME_VALUES),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "値は同じで取得時刻が違う", "2026-11-19T01:00:00Z", ADOPTED_ACQUIRED_AT, SAME_VALUES_BUT_ACQUIRED_AT),
                    org.junit.jupiter.params.provider.Arguments.of(
                            "値が違い取得時刻は同じ", "2026-11-19T00:00:00Z", RECEIVED_ACQUIRED_AT, null));
        }

        @ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("sameMilestoneCases")
        void 行24_主要実績も値と取得時刻の組で同じ値の文を選び2つの領域の前に示す(
                String name, String adoptedOccurredAt, Instant adoptedAcquiredAt, String expected) throws Exception {
            String page = decisionPageWith(
                    mismatch("MILESTONE"),
                    new QuarantineValues(
                            milestone("2026-11-19T01:00:00Z"),
                            RECEIVED_ACQUIRED_AT,
                            adopted(milestone(adoptedOccurredAt), adoptedAcquiredAt)));

            for (String sentence : List.of(SAME_VALUES, SAME_VALUES_BUT_ACQUIRED_AT)) {
                org.assertj.core.api.Assertions.assertThat(page.contains("<p>" + sentence + "</p>"))
                        .as(sentence)
                        .isEqualTo(sentence.equals(expected));
            }
            if (expected != null) {
                org.assertj.core.api.Assertions.assertThat(page.indexOf(expected))
                        .isLessThan(page.indexOf("<h2 id=\"received-values-heading\">"));
            }
        }
    }

    /**
     * Bolt 39d3c の行 18（種類の表示名の写し。開始準備の決定 (5)、T-240・T-244）。画面の層の {@code QuarantineValueViews} の単体（新しいテストの
     * クラスを作らず、同じパッケージのこのクラスに置く）。名前は用語集の実績の種類の名前（docs/manual/用語集.md）を書き並べ、追跡の表示名を
     * 参照しない。
     */
    @Nested
    class Bolt39d3c_種類の表示名の写し {

        @ParameterizedTest(name = "{0} は {1}")
        @CsvSource({
            "PICKUP, 集荷",
            "RECEIPT_AT_ORIGIN, 搬入",
            "DEPARTURE, 出発",
            "TRANSSHIPMENT, 積替",
            "ARRIVAL, 到着",
            "DELIVERY, 引渡し"
        })
        void 行18_実績の種類の表示名は用語集の名前(
                com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue kind, String name) {
            org.assertj.core.api.Assertions.assertThat(QuarantineValueViews.kindName(kind))
                    .isEqualTo(name);
        }

        @Test
        void 行18_書き並べた組の数は実績の種類の値の数と同じ() throws Exception {
            java.lang.reflect.Method method = getClass()
                    .getDeclaredMethod(
                            "行18_実績の種類の表示名は用語集の名前",
                            com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue.class,
                            String.class);

            org.assertj.core.api.Assertions.assertThat(
                            method.getAnnotation(CsvSource.class).value())
                    .hasSize(com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue.values()
                            .length);
        }
    }

    /**
     * Bolt 39d3b の行 12〜14（S-17 の判断の画面の「却下する」「採用する」の 2 つの見出しの欄と採用の POST。開始準備の決定 (8)・(9)、T-240・
     * T-244）。判断のサービスと照会は mock にし、採用の欄の有無が判断のサービスの採用できるかの照会（{@code adoptionRefusalOf}）だけで決まり
     * 照会の値によらないこと、欄の id と順、採用の欄の誤り、採用の POST の行き先を確かめる。認可と CSRF はセキュリティの統合のテスト（行 22）。
     */
    @Nested
    class Bolt39d3b_判断の画面の採用 {

        private static final UUID RECORD_ID = UUID.fromString("39d3b000-0000-0000-0000-0000000000dd");
        private static final String DECISION_URL = LIST_URL + "/" + RECORD_ID;
        private static final String ADOPTION_URL = DECISION_URL + "/adoption";
        private static final String REASON_TOO_LONG = "採用の理由は 1000 文字までで書いてください。";
        private static final String ADOPTION_IRREVOCABLE = "採用すると取り消せません。誤って採用したときは、提供元に新しい外部版で正しい原本を送ってもらってください。";
        private static final String KEYLESS_GUIDE = "形式の誤りは採用できません。直したファイルを取り込み直してください。";
        private static final String ADOPTED_SECTION =
                "<section aria-labelledby=\"adopted-values-heading\">\\s*<h2 id=\"adopted-values-heading\">この外部版の採用値</h2>";
        private static final String REJECTION_SECTION =
                "<section aria-labelledby=\"rejection-heading\">\\s*<h2 id=\"rejection-heading\">却下する</h2>";
        private static final String ADOPTION_SECTION =
                "<section aria-labelledby=\"adoption-heading\">\\s*<h2 id=\"adoption-heading\">採用する</h2>";
        private static final String BACK_LINK = ">一覧に戻る</a>";
        /** 補助文字（U+20BB7。UTF-16 で 2 単位）。 */
        private static final String SUPPLEMENTARY = new String(Character.toChars(0x20BB7));

        private static final Instant ACQUIRED_AT = Instant.parse("2026-11-16T00:30:00Z");
        private static final Instant PUBLISHED_AT = Instant.parse("2026-11-15T09:00:00Z");

        private static UndecidedRecordPage.Item item(boolean keyless) {
            return new UndecidedRecordPage.Item(
                    RECORD_ID,
                    INGESTED_AT,
                    "開発海運（架空）",
                    "VOYAGE",
                    new PhysicalLineRange(140, 140),
                    keyless,
                    keyless ? null : "DEMO-201",
                    keyless ? null : "3",
                    "140 行目: 理由");
        }

        private static DecisionOutcome.NotAdoptable valueNotRecorded() {
            return new DecisionOutcome.NotAdoptable(RECORD_ID, DecisionOutcome.NotAdoptableReason.VALUE_NOT_RECORDED);
        }

        private static VoyageValues voyage(String losAngelesArrival) {
            return new VoyageValues(
                    "DEMO-201",
                    List.of(
                            new PortCallValue(
                                    new Location("JPTYO"), null, new UtcInstant(Instant.parse("2026-11-18T23:00:00Z"))),
                            new PortCallValue(
                                    new Location("USLAX"), new UtcInstant(Instant.parse(losAngelesArrival)), null)));
        }

        private static QuarantineValues readable() {
            return new QuarantineValues(
                    voyage("2026-12-01T22:00:00Z"),
                    ACQUIRED_AT,
                    new QuarantineValues.Adopted(voyage("2026-12-01T14:00:00Z"), "3", PUBLISHED_AT, ACQUIRED_AT));
        }

        private void givenDecision(
                UndecidedRecordPage.Item item, QuarantineValues values, Optional<DecisionOutcome> adoptionRefusal) {
            given(decisions.refusalOf(RECORD_ID)).willReturn(Optional.empty());
            given(decisions.adoptionRefusalOf(RECORD_ID)).willReturn(adoptionRefusal);
            given(query.undecidedQuarantine(RECORD_ID)).willReturn(Optional.of(item));
            given(query.quarantineValues(item)).willReturn(Optional.ofNullable(values));
        }

        private String body(ResultActions result) throws Exception {
            return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        }

        private String decisionPage() throws Exception {
            return body(mockMvc.perform(get(DECISION_URL)).andExpect(status().isOk()));
        }

        private ResultActions postAdoption(String url, String reason) throws Exception {
            return mockMvc.perform(post(url).param("reason", reason));
        }

        private static int indexOf(String page, String regex) {
            Matcher matcher = Pattern.compile(regex).matcher(page);
            Assertions.assertThat(matcher.find()).as(regex).isTrue();
            return matcher.start();
        }

        private static int occurrences(String page, String text) {
            return page.split(Pattern.quote(text), -1).length - 1;
        }

        // --- 行 12: 判断の画面の 2 つの欄 ---

        @Test
        void 行12_採用できる隔離は値の領域の後に却下するの欄と採用するの欄がこの順に並び欄のidは重ならない() throws Exception {
            givenDecision(item(false), readable(), Optional.empty());

            String page = decisionPage();

            Assertions.assertThat(List.of(
                            indexOf(page, ADOPTED_SECTION),
                            indexOf(page, REJECTION_SECTION),
                            indexOf(page, "<label for=\"reason\">却下の理由</label>"),
                            indexOf(page, ADOPTION_SECTION),
                            indexOf(page, "<label for=\"adoption-reason\">採用の理由</label>"),
                            indexOf(page, BACK_LINK)))
                    .isSorted();
            Assertions.assertThat(page)
                    .contains("<p id=\"adoption-reason-hint\">任意、" + ReceivedRecordDecision.REASON_LIMIT + " 文字まで</p>")
                    .containsPattern("<textarea[^>]*id=\"adoption-reason\"[^>]*name=\"reason\"")
                    .containsPattern(
                            "<textarea[^>]*id=\"adoption-reason\"[^>]*aria-describedby=\"adoption-reason-hint\"")
                    .doesNotContainPattern("<textarea[^>]*id=\"adoption-reason\"[^>]*aria-required")
                    .contains("<p id=\"adoption-irrevocable\">" + ADOPTION_IRREVOCABLE + "</p>")
                    .contains(
                            "<button type=\"submit\" class=\"btn btn-primary\" aria-describedby=\"adoption-irrevocable\">"
                                    + "採用する</button>")
                    .contains("action=\"" + ADOPTION_URL + "?page=1\"")
                    .doesNotContain(KEYLESS_GUIDE);
            for (String id : List.of(
                    "reason",
                    "reason-hint",
                    "irrevocable",
                    "adoption-reason",
                    "adoption-reason-hint",
                    "adoption-irrevocable",
                    "rejection-heading",
                    "adoption-heading")) {
                Assertions.assertThat(occurrences(page, "id=\"" + id + "\""))
                        .as(id)
                        .isEqualTo(1);
            }
        }

        static Stream<Arguments> valueStates() {
            QuarantineValues.Adopted adopted =
                    new QuarantineValues.Adopted(voyage("2026-12-01T14:00:00Z"), "3", PUBLISHED_AT, ACQUIRED_AT);
            QuarantineValues.Adopted illegibleAdopted =
                    new QuarantineValues.Adopted(null, true, "3", PUBLISHED_AT, ACQUIRED_AT);
            List<Arguments> states = List.of(
                    Arguments.of("受け取った値を読めた", readable()),
                    Arguments.of("受け取った値を読めない", new QuarantineValues(null, true, ACQUIRED_AT, adopted)),
                    Arguments.of("値の列なし", new QuarantineValues(null, ACQUIRED_AT, adopted)),
                    Arguments.of(
                            "この外部版の採用値がない", new QuarantineValues(voyage("2026-12-01T22:00:00Z"), ACQUIRED_AT, null)),
                    Arguments.of(
                            "採用値の側だけ読めない",
                            new QuarantineValues(voyage("2026-12-01T22:00:00Z"), ACQUIRED_AT, illegibleAdopted)));
            return states.stream()
                    .flatMap(state -> Stream.of(true, false)
                            .map(adoptable -> Arguments.of(state.get()[0], state.get()[1], adoptable)));
        }

        @ParameterizedTest(name = "{0}・採用できる {2}")
        @MethodSource("valueStates")
        void 行12_採用の欄の有無は採用できるかの照会の結果だけで決まり照会の値によらない(String name, QuarantineValues values, boolean adoptable)
                throws Exception {
            givenDecision(item(false), values, adoptable ? Optional.empty() : Optional.of(valueNotRecorded()));

            String page = decisionPage();

            Assertions.assertThat(
                            Pattern.compile(ADOPTION_SECTION).matcher(page).find())
                    .as(name)
                    .isEqualTo(adoptable);
            Assertions.assertThat(page.contains("id=\"adoption-reason\"")).isEqualTo(adoptable);
            Assertions.assertThat(page)
                    .containsPattern(REJECTION_SECTION)
                    .contains("<label for=\"reason\">却下の理由</label>")
                    .doesNotContain(KEYLESS_GUIDE);
            then(decisions).should().adoptionRefusalOf(RECORD_ID);
        }

        @Test
        void 行12_キーのない隔離は却下するの欄とその後の今の文だけを示し採用の欄を出さない() throws Exception {
            givenDecision(
                    item(true),
                    null,
                    Optional.of(
                            new DecisionOutcome.NotAdoptable(RECORD_ID, DecisionOutcome.NotAdoptableReason.KEYLESS)));

            String page = decisionPage();

            Assertions.assertThat(page).doesNotContainPattern(ADOPTION_SECTION).doesNotContain("adoption-reason");
            Assertions.assertThat(List.of(
                            indexOf(page, REJECTION_SECTION),
                            indexOf(page, ">却下する</button>"),
                            indexOf(page, "<p id=\"adoption-refusal\" tabindex=\"-1\">" + KEYLESS_GUIDE + "</p>"),
                            indexOf(page, BACK_LINK)))
                    .isSorted();
        }

        // --- 行 13: 採用の欄の誤り ---

        @Test
        void 行13_前後の空白を除いて1001文字の採用の理由は判断の画面に戻り採用の欄に誤りを示して書いた理由を残しサービスを呼ばない() throws Exception {
            String thousandAndOne = " " + SUPPLEMENTARY.repeat(500) + "あ".repeat(501) + " ";
            givenDecision(item(false), readable(), Optional.empty());

            String page = body(postAdoption(ADOPTION_URL, thousandAndOne).andExpect(status().isOk()));

            Assertions.assertThat(page)
                    .contains("<title>採用できませんでした - 隔離の判断 - cargo-tracker</title>")
                    .contains("<h2 id=\"error-summary-title\">採用できませんでした</h2>")
                    .contains("<a href=\"#adoption-reason\">" + REASON_TOO_LONG + "</a>")
                    .contains("<p id=\"adoption-reason-error\" class=\"text-danger\">" + REASON_TOO_LONG + "</p>")
                    .containsPattern("<textarea[^>]*id=\"adoption-reason\"[^>]*aria-invalid=\"true\"")
                    .containsPattern(
                            "<textarea[^>]*id=\"adoption-reason\"[^>]*aria-describedby=\"adoption-reason-hint adoption-reason-error\"")
                    .contains(thousandAndOne + "</textarea>")
                    // 却下の欄に誤りは出ず、空で描く
                    .doesNotContain("id=\"reason-error\"")
                    .doesNotContain("却下できませんでした")
                    .containsPattern(
                            "<textarea[^>]*id=\"reason\"[^>]*aria-describedby=\"reason-hint\"[^>]*></textarea>");
            then(decisions).should(Mockito.never()).adopt(any(), any());
        }

        @Test
        void 行13_採用できない隔離へ上限を超えた理由を送ると欄のない画面のエラー要約を出さず判断の画面へ戻してサービスを呼ばない() throws Exception {
            String thousandAndOne = "あ".repeat(ReceivedRecordDecision.REASON_LIMIT + 1);
            givenDecision(item(false), readable(), Optional.of(valueNotRecorded()));

            postAdoption(ADOPTION_URL, thousandAndOne)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(DECISION_URL + "?page=1"));

            then(decisions).should(Mockito.never()).adopt(any(), any());
        }

        @Test
        void 行13_却下の欄の誤りで戻った画面にも採用の欄を空で描く() throws Exception {
            givenDecision(item(false), readable(), Optional.empty());

            String page = body(mockMvc.perform(post(DECISION_URL + "/decision").param("reason", " "))
                    .andExpect(status().isOk()));

            Assertions.assertThat(page)
                    .contains("<title>却下できませんでした - 隔離の判断 - cargo-tracker</title>")
                    .containsPattern(ADOPTION_SECTION)
                    .doesNotContain("id=\"adoption-reason-error\"")
                    .containsPattern(
                            "<textarea[^>]*id=\"adoption-reason\"[^>]*aria-describedby=\"adoption-reason-hint\"[^>]*></textarea>");
        }

        static Stream<Arguments> acceptedReasons() {
            String thousand = SUPPLEMENTARY.repeat(500) + "あ".repeat(500);
            return Stream.of(
                    Arguments.of("ちょうど1000文字", thousand),
                    Arguments.of("前後に空白を足した1000文字", "  " + thousand + "\n"),
                    Arguments.of("空", ""),
                    Arguments.of("空白だけ", " 　"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("acceptedReasons")
        void 行13_上限までの理由と空の理由は書いたままサービスに渡して採用する(String name, String reason) throws Exception {
            given(decisions.adopt(any(), any())).willReturn(new DecisionOutcome.AdoptedQuarantine(RECORD_ID));

            postAdoption(ADOPTION_URL, reason).andExpect(status().is3xxRedirection());

            then(decisions).should().adopt(any(), eq(new AdoptReceivedRecordCommand(RECORD_ID, reason)));
        }

        // --- 行 14: 採用の POST の行き先は Bolt39d3b2_採用できない理由と開いた後の案内 の行 14 が縛る（判断の画面へ戻す結果を足した） ---

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"not-a-uuid", "123"})
        void 行14_UUIDでない受信記録IDの採用のPOSTは404にしサービスを呼ばない(String id) throws Exception {
            postAdoption(LIST_URL + "/" + id + "/adoption", "理由").andExpect(status().isNotFound());
            then(decisions).shouldHaveNoInteractions();
        }

        @Test
        void 行14_画面で採用の欄を出さない照会の結果を採用のPOSTが返すと判断の画面へ戻り画面と同じ結果から決まる() throws Exception {
            givenDecision(item(false), readable(), Optional.of(valueNotRecorded()));
            given(decisions.adopt(any(), any())).willReturn(valueNotRecorded());

            Assertions.assertThat(decisionPage()).doesNotContainPattern(ADOPTION_SECTION);
            postAdoption(ADOPTION_URL, "理由")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(DECISION_URL + "?page=1"));
        }
    }

    /**
     * Bolt 39d3b2 の行 9〜14（S-17 の採用できない理由の文と、開いた後に採用できなくなったときの案内。開始準備の決定 (3)・(5)、【前の決定の変更】(2)、
     * T-240・T-244）。判断のサービスと照会は mock にし、文は採用できるかの照会の理由だけで決まり、「却下する」の欄の後、「一覧に戻る」の前の 1 つの段落に
     * 一字ずつ表のとおり示されること、採用できなくなった POST は判断の画面へ PRG で戻り、理由を確かめ直した画面にだけエラー要約が出ることを確かめる。
     * 39d3b の同じ番号の行と名前が衝突しないよう入れ子にする。
     */
    @Nested
    class Bolt39d3b2_採用できない理由と開いた後の案内 {

        private static final UUID RECORD_ID = UUID.fromString("39d3b200-0000-0000-0000-0000000000ee");
        private static final String DECISION_URL = LIST_URL + "/" + RECORD_ID;
        private static final String ADOPTION_URL = DECISION_URL + "/adoption";
        private static final String FLASH = "adoptionRefused";
        private static final String ADOPTION_HEADING = "<h2 id=\"adoption-heading\">採用する</h2>";
        private static final String REJECTION_SECTION = "<h2 id=\"rejection-heading\">却下する</h2>";
        private static final String REFUSED_TITLE = "<title>採用できませんでした - 隔離の判断 - cargo-tracker</title>";
        private static final String PLAIN_TITLE = "<title>隔離の判断 - cargo-tracker</title>";
        private static final String REFUSAL_ID = "id=\"adoption-refusal\"";
        private static final String SUMMARY_ID = "id=\"error-summary\"";

        private static final String KEYLESS_TEXT = "形式の誤りは採用できません。直したファイルを取り込み直してください。";
        private static final String VALUE_NOT_RECORDED_TEXT =
                "受け取った値を記録する前に取り込んだ隔離は採用できません。受け取った値を使うときは、提供元に新しい外部版で送り直してもらってください。";
        private static final String VALUE_UNREADABLE_TEXT =
                "受け取った値を読めないので採用できません。受け取った値を使うときは、提供元に新しい外部版で送り直してもらってください。";
        private static final String SOURCE_NOT_APPROVED_TEXT =
                "情報源が承認済みではないため採用できません。受け取った値を使うときは、運用の手順で情報源を承認し直してから、この隔離を開き直してください。";
        private static final String TRACKING_NUMBER_DIFFERS_TEXT =
                "追跡番号がこの外部版の採用値と違うので採用できません。正しい原本を使うときは、提供元に新しい外部版で送り直してもらってください。";

        private static UndecidedRecordPage.Item item() {
            return new UndecidedRecordPage.Item(
                    RECORD_ID,
                    INGESTED_AT,
                    "開発港運（架空）",
                    "MILESTONE",
                    new PhysicalLineRange(3, 3),
                    false,
                    "MS-0201",
                    "2",
                    "3 行目: 理由");
        }

        private static DecisionOutcome.NotAdoptable refused(NotAdoptableReason reason) {
            return new DecisionOutcome.NotAdoptable(RECORD_ID, reason);
        }

        private void givenDecision(Optional<DecisionOutcome> adoptionRefusal) {
            given(decisions.refusalOf(RECORD_ID)).willReturn(Optional.empty());
            given(decisions.adoptionRefusalOf(RECORD_ID)).willReturn(adoptionRefusal);
            given(query.undecidedQuarantine(RECORD_ID)).willReturn(Optional.of(item()));
            given(query.quarantineValues(any())).willReturn(Optional.empty());
        }

        private static String body(ResultActions result) throws Exception {
            return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        }

        private String decisionPage() throws Exception {
            return body(mockMvc.perform(get(DECISION_URL)).andExpect(status().isOk()));
        }

        private String decisionPageAfterRefusal() throws Exception {
            return body(
                    mockMvc.perform(get(DECISION_URL).flashAttr(FLASH, true)).andExpect(status().isOk()));
        }

        private static int occurrences(String page, String text) {
            return page.split(Pattern.quote(text), -1).length - 1;
        }

        static Stream<Arguments> reasonTexts() {
            return Stream.of(
                    Arguments.of(NotAdoptableReason.KEYLESS, KEYLESS_TEXT),
                    Arguments.of(NotAdoptableReason.VALUE_NOT_RECORDED, VALUE_NOT_RECORDED_TEXT),
                    Arguments.of(NotAdoptableReason.VALUE_UNREADABLE, VALUE_UNREADABLE_TEXT),
                    Arguments.of(NotAdoptableReason.SOURCE_NOT_APPROVED, SOURCE_NOT_APPROVED_TEXT),
                    Arguments.of(NotAdoptableReason.TRACKING_NUMBER_DIFFERS, TRACKING_NUMBER_DIFFERS_TEXT));
        }

        // --- 行 9: 判断の画面の理由の文 ---

        @ParameterizedTest(name = "{0}")
        @MethodSource("reasonTexts")
        void 行9_採用できない理由ごとの文を却下するの欄の後一覧に戻るの前の1つの段落に示し採用の欄を出さない(NotAdoptableReason reason, String text)
                throws Exception {
            givenDecision(Optional.of(refused(reason)));

            String page = decisionPage();

            String paragraph = "<p " + REFUSAL_ID + " tabindex=\"-1\">" + text + "</p>";
            Assertions.assertThat(page)
                    .doesNotContain(ADOPTION_HEADING)
                    .doesNotContain("adoption-reason")
                    .doesNotContain(SUMMARY_ID)
                    .contains(PLAIN_TITLE)
                    .contains(paragraph)
                    .contains("action=\"" + DECISION_URL + "/decision?page=1\"")
                    .contains("<label for=\"reason\">却下の理由</label>");
            Assertions.assertThat(occurrences(page, REFUSAL_ID)).isEqualTo(1);
            Assertions.assertThat(List.of(
                            page.indexOf(REJECTION_SECTION),
                            page.indexOf(">却下する</button>"),
                            page.indexOf(paragraph),
                            page.indexOf(">一覧に戻る</a>")))
                    .doesNotContain(-1)
                    .isSorted();
        }

        @Test
        void 行9_すべての理由に文がある() {
            List<Object> covered =
                    reasonTexts().map(arguments -> arguments.get()[0]).toList();
            Assertions.assertThat(covered).containsExactlyInAnyOrder((Object[]) NotAdoptableReason.values());
        }

        // --- 行 10: 文を出さないとき ---

        static Stream<Arguments> noRefusalText() {
            return Stream.of(
                    Arguments.of("採用できる", Optional.empty(), true),
                    Arguments.of("行き先を決めた後に判断された", Optional.of(new DecisionOutcome.AlreadyDecided()), false),
                    Arguments.of("読み直すとない", Optional.of(new DecisionOutcome.NotFound()), false),
                    Arguments.of("読み直すと隔離でない", Optional.of(new DecisionOutcome.NotDecidable()), false));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("noRefusalText")
        void 行10_採用できない理由がなければ文の段落を出さず採用の欄は照会の結果だけで決まる(
                String name, Optional<DecisionOutcome> adoptionRefusal, boolean adoptable) throws Exception {
            givenDecision(adoptionRefusal);

            String page = decisionPage();

            Assertions.assertThat(page).doesNotContain(REFUSAL_ID).doesNotContain(SUMMARY_ID);
            Assertions.assertThat(page.contains(ADOPTION_HEADING)).as(name).isEqualTo(adoptable);
        }

        // --- 行 11: 開いた後に採用できなくなった POST ---

        @Test
        void 行11_採用できなくなったPOSTは元の取込とページを持つ判断の画面へ戻り印のフラッシュが付く() throws Exception {
            given(decisions.adopt(any(), any())).willReturn(refused(NotAdoptableReason.SOURCE_NOT_APPROVED));

            mockMvc.perform(post(ADOPTION_URL + "?ingestion=" + INGESTION_ID + "&page=2")
                            .param("reason", "提供元に確かめた。"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(DECISION_URL + "?ingestion=" + INGESTION_ID + "&page=2"))
                    .andExpect(flash().attribute(FLASH, true))
                    .andExpect(flash().attribute("result", (Object) null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("reasonTexts")
        void 行11_戻った判断の画面はエラー要約に理由の文と段落へのリンクを示し採用の欄を出さず却下の欄は使える(NotAdoptableReason reason, String text)
                throws Exception {
            givenDecision(Optional.of(refused(reason)));

            String page = decisionPageAfterRefusal();

            Assertions.assertThat(page)
                    .contains(REFUSED_TITLE)
                    .containsPattern(
                            "<div id=\"error-summary\" tabindex=\"-1\" role=\"alert\"\\s+aria-labelledby=\"error-summary-title\">")
                    .contains("<h2 id=\"error-summary-title\">採用できませんでした</h2>")
                    .contains("<a href=\"#adoption-refusal\">" + text + "</a>")
                    .contains("<p " + REFUSAL_ID + " tabindex=\"-1\">" + text + "</p>")
                    .doesNotContain(ADOPTION_HEADING)
                    .contains("action=\"" + DECISION_URL + "/decision?page=1\"");
            Assertions.assertThat(occurrences(page, SUMMARY_ID)).isEqualTo(1);
        }

        // --- 行 12: 1001 文字の理由を採用できない隔離へ送る ---

        @Test
        void 行12_1001文字の理由を採用できない隔離へ送るとサービスの採用を呼ばず判断の画面へ戻る() throws Exception {
            String thousandAndOne = "あ".repeat(ReceivedRecordDecision.REASON_LIMIT + 1);
            given(decisions.adoptionRefusalOf(RECORD_ID))
                    .willReturn(Optional.of(refused(NotAdoptableReason.SOURCE_NOT_APPROVED)));

            mockMvc.perform(post(ADOPTION_URL + "?ingestion=" + INGESTION_ID + "&page=2")
                            .param("reason", thousandAndOne))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(DECISION_URL + "?ingestion=" + INGESTION_ID + "&page=2"))
                    .andExpect(flash().attribute(FLASH, true));

            then(decisions).should(Mockito.never()).adopt(any(), any());
        }

        // --- 行 13: 着地で事情が変わった ---

        @Test
        void 行13_戻った画面の照会が空なら承認が戻ったのでエラー要約を出さず採用の欄を出す() throws Exception {
            givenDecision(Optional.empty());

            String page = decisionPageAfterRefusal();

            Assertions.assertThat(page)
                    .doesNotContain(SUMMARY_ID)
                    .doesNotContain(REFUSAL_ID)
                    .doesNotContain("採用できませんでした")
                    .contains(PLAIN_TITLE)
                    .contains(ADOPTION_HEADING);
        }

        @Test
        void 行13_戻った画面の前にほかの人が判断すると一覧に戻って判断済みと知らせる() throws Exception {
            given(decisions.refusalOf(RECORD_ID)).willReturn(Optional.of(new DecisionOutcome.AlreadyDecided()));

            mockMvc.perform(get(DECISION_URL + "?ingestion=" + INGESTION_ID + "&page=2")
                            .flashAttr(FLASH, true))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(LIST_URL + "?ingestion=" + INGESTION_ID + "&page=2"))
                    .andExpect(flash().attribute("result", "この隔離は判断済みです。"));
        }

        // --- 行 14: 採用の POST の行き先（T-240。結果のすべてに行き先がある） ---

        static Stream<Arguments> outcomes() {
            String list = LIST_URL + "?ingestion=" + INGESTION_ID + "&page=2";
            String decision = DECISION_URL + "?ingestion=" + INGESTION_ID + "&page=2";
            return Stream.of(
                    Arguments.of(new DecisionOutcome.AdoptedQuarantine(RECORD_ID), 302, list, "result", "隔離を採用しました。"),
                    Arguments.of(new DecisionOutcome.Rejected(RECORD_ID), 302, list, "result", "隔離を却下しました。"),
                    Arguments.of(new DecisionOutcome.AlreadyDecided(), 302, list, "result", "この隔離は判断済みです。"),
                    Arguments.of(refused(NotAdoptableReason.TRACKING_NUMBER_DIFFERS), 302, decision, FLASH, true),
                    Arguments.of(new DecisionOutcome.NotFound(), 404, null, null, null),
                    Arguments.of(new DecisionOutcome.NotDecidable(), 404, null, null, null),
                    Arguments.of(new DecisionOutcome.NotPermitted(), 403, null, null, null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("outcomes")
        void 行14_採用の結果ごとに一覧か判断の画面に戻って知らせるか404か403にする(
                DecisionOutcome outcome, int status, String location, String flashName, Object flashValue)
                throws Exception {
            given(decisions.adopt(any(), any())).willReturn(outcome);

            ResultActions result = mockMvc.perform(post(ADOPTION_URL + "?ingestion=" + INGESTION_ID + "&page=2")
                            .param("reason", "提供元に確かめた。"))
                    .andExpect(status().is(status));

            if (location != null) {
                result.andExpect(redirectedUrl(location)).andExpect(flash().attribute(flashName, flashValue));
            }
            then(decisions).should().adopt(any(), eq(new AdoptReceivedRecordCommand(RECORD_ID, "提供元に確かめた。")));
        }

        @Test
        void 行14_結果の型のすべてを採用の行き先の組で縛る() {
            List<Class<?>> covered = outcomes()
                    .<Class<?>>map(arguments -> arguments.get()[0].getClass())
                    .toList();
            Assertions.assertThat(covered).containsExactlyInAnyOrder(DecisionOutcome.class.getPermittedSubclasses());
        }
    }
}

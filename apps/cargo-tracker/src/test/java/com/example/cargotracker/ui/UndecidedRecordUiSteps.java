package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * データ責任者の S-17 隔離・差異の確認（判断を待つ隔離の一覧。US-14 AC4。Bolt 39d）の画面の層のステップ定義。取り込みと情報源は S-16 の
 * ステップ定義（{@link ExternalDataIngestionUiSteps}）が作り、この定義は結果の画面の入口から先と一覧の読み方を確かめる。一覧の列は
 * 取り込んだ日時・情報源・行・種類・外部 ID・外部版・隔離の理由の順（Bolt 39d 計画の開始準備の決定 (3)）。
 */
public class UndecidedRecordUiSteps {

    private static final String LIST_URL = "/staff/external-data/undecided-records";
    private static final String HEADING = "隔離・差異の確認";
    private static final String TABLE_CAPTION = "判断を待つ隔離・差異";
    private static final String ENTRY_LINK = "この取込の隔離・差異をすべて確認する";
    private static final Pattern RESULT_URL = Pattern.compile(".*/staff/external-data/ingestions/([0-9a-f-]{36})$");

    private static final int SOURCE = 1;
    private static final int LINES = 2;
    private static final int KIND = 3;
    private static final int KEY = 4;
    private static final int NOTE = 5;

    private final BrowserSession browser;
    private final ExternalDataIngestionUiSteps ingestion;
    private final String baseUrl;

    public UndecidedRecordUiSteps(
            BrowserSession browser,
            ExternalDataIngestionUiSteps ingestion,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.ingestion = ingestion;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    /** Bolt 39d の行 11。S-16 の結果の画面の隔離の理由の領域の最後のリンクへ Tab で進めて押す。 */
    @もし("キー操作だけで取込の結果からこの取込の隔離・差異をすべて確認するを開く")
    public void この取込の隔離と差異をすべて確認するを開く() {
        page().locator("body").focus();
        browser.tabUntilFocused(entryLink());
        page().keyboard().press("Enter");
        page().waitForURL(url -> url.contains(LIST_URL + "?ingestion="));
        browser.checkAccessibility();
    }

    /** Bolt 39h3 の行 9。取込で絞らない一覧を開く（取込の結果の画面を経ずに、元の取込の結果に戻らない片付けの入口）。 */
    @もし("判断を待つ隔離・差異の確認を絞らずに開く")
    public void 絞らずに開く() {
        browser.navigate(baseUrl + LIST_URL);
        assertThat(heading()).isVisible();
        browser.checkAccessibility();
    }

    /** Bolt 39h3 の行 9（決定 (5)）。この情報源の内容の違う原本の行に、取り込んだ日時と情報源を読み上げに足した「この取込で絞る」がある。 */
    @ならば("絞らない一覧の {int} 行目の内容の違う原本の行にこの取込で絞るのリンクが示される")
    public void 絞るリンクが示される(int line) {
        Locator link = filterLink(line);
        assertThat(link).isVisible();
        assertThat(link)
                .hasText(Pattern.compile(
                        "^この取込で絞る（.+Asia/Tokyo.+、" + PlaywrightText.literal(ingestion.sourceLabel()) + "）$"));
        browser.checkAccessibility();
    }

    /** Bolt 39h3 の行 9。行の「この取込で絞る」へ Tab で進めて押し、その取込で絞った一覧を開く。 */
    @もし("キー操作だけで絞らない一覧の {int} 行目の内容の違う原本のこの取込で絞るを開く")
    public void 絞るを開く(int line) {
        Locator link = filterLink(line);
        page().locator("body").focus();
        browser.tabUntilFocused(link);
        page().keyboard().press("Enter");
        page().waitForURL(url -> url.contains(LIST_URL + "?ingestion="));
        browser.checkAccessibility();
    }

    private Locator filterLink(int line) {
        Locator row = rows().filter(new Locator.FilterOptions()
                        .setHas(page().locator("td:nth-child(2):text-is('" + ingestion.sourceLabel() + "')")))
                .filter(new Locator.FilterOptions()
                        .setHas(page().locator("td:nth-child(" + (LINES + 1) + "):text-is('" + line + " 行目')")))
                .filter(new Locator.FilterOptions()
                        .setHas(page().locator("td:nth-child(" + (KIND + 1) + "):text-is('内容の違う原本')")));
        assertThat(row).hasCount(1);
        return row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(Pattern.compile("^この取込で絞る")));
    }

    /** Bolt 39h3 の行 9。取込で絞った一覧は行ごとの「この取込で絞る」を出さない。 */
    @かつ("絞った一覧にこの取込で絞るのリンクは示されない")
    public void 絞った一覧に絞るリンクは示されない() {
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("^この取込で絞る"))))
                .hasCount(0);
    }

    /** Bolt 39d の行 11。隔離が 0 件の結果の画面は押しても行き先のないリンクを出さない。 */
    @かつ("取込の結果にこの取込の隔離・差異をすべて確認するリンクは示されない")
    public void この取込の隔離と差異をすべて確認するリンクは示されない() {
        assertThat(entryLink()).hasCount(0);
    }

    /** Bolt 39d の行 10。結果の画面の URL の取込 ID で絞った一覧を開く（隔離のない取込には入口のリンクがない）。 */
    @もし("取込の結果の取込で絞った隔離・差異の確認を開く")
    public void 取込で絞った一覧を開く() {
        java.util.regex.Matcher matcher = RESULT_URL.matcher(page().url());
        org.assertj.core.api.Assertions.assertThat(matcher.matches())
                .as("取込の結果の画面 %s", page().url())
                .isTrue();
        page().navigate(baseUrl + LIST_URL + "?ingestion=" + matcher.group(1));
        browser.checkAccessibility();
    }

    @ならば("隔離・差異の確認の画面が開く")
    public void 隔離差異の確認の画面が開く() {
        assertThat(heading()).isVisible();
        browser.checkAccessibility();
    }

    @ならば("隔離・差異の確認に {string} が示される")
    public void 件数の文が示される(String text) {
        assertThat(heading()).isVisible();
        assertThat(page().getByText(text, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        browser.checkAccessibility();
    }

    /** Bolt 39d の行 10。0 件の文だけを示し、表を出さない。 */
    @ならば("隔離・差異の確認に {string} が示され、判断を待つ隔離の一覧は示されない")
    public void 件数0の文が示される(String text) {
        assertThat(heading()).isVisible();
        assertThat(page().getByText(text, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(table()).hasCount(0);
        browser.checkAccessibility();
    }

    /** Bolt 39d の行 1・2。形式の誤り（キーのない隔離）は外部 ID・外部版を「—」と読み上げ用の「なし」で示す。 */
    @かつ("判断を待つ隔離の一覧に 4 行目の形式の誤りと 6 行目の内容の違う原本がこの順に示される")
    public void 二つの種類が行の順に示される() {
        assertThat(rows()).hasCount(2);
        Locator formatError = rows().nth(0);
        assertThat(cell(formatError, 0)).containsText("Asia/Tokyo");
        assertThat(cell(formatError, SOURCE)).hasText(ingestion.sourceLabel());
        assertThat(cell(formatError, LINES)).hasText("4 行目");
        assertThat(cell(formatError, KIND)).hasText("形式の誤り");
        assertThat(cell(formatError, KEY)).hasText("—なし");
        assertThat(cell(formatError, NOTE)).hasText("4 行目: 列の数がヘッダーと違います（ヘッダー 4 列、この行 5 列）。提供元に確かめてください。");
        Locator mismatch = rows().nth(1);
        assertThat(cell(mismatch, SOURCE)).hasText(ingestion.sourceLabel());
        assertThat(cell(mismatch, LINES)).hasText("6 行目");
        assertThat(cell(mismatch, KIND)).hasText("内容の違う原本");
        assertThat(cell(mismatch, KEY)).hasText(Pattern.compile("^V2612E-[0-9a-f]{8}・1$"));
        assertThat(cell(mismatch, NOTE)).hasText(contentMismatchNote(6));
        browser.checkAccessibility();
    }

    @かつ("判断を待つ隔離の一覧に {int} 件が示され、先頭は {int} 行目の内容の違う原本である")
    public void 先頭は内容の違う原本(int count, int line) {
        firstRowIs(count, line, "内容の違う原本");
    }

    @かつ("判断を待つ隔離の一覧に {int} 件が示され、先頭は {int} 行目の形式の誤りである")
    public void 先頭は形式の誤り(int count, int line) {
        firstRowIs(count, line, "形式の誤り");
    }

    private void firstRowIs(int count, int line, String kind) {
        assertThat(rows()).hasCount(count);
        assertThat(cell(rows().first(), LINES)).hasText(line + " 行目");
        assertThat(cell(rows().first(), KIND)).hasText(kind);
        browser.checkAccessibility();
    }

    /**
     * Bolt 39d の行 5、Bolt 39d2b の行 18。ページ送りのリンクへ先頭から Tab で進めて押す。ページ送りは表の前（件数の文・まとめての却下の
     * リンク・「すべての取込の隔離・差異を見る」の後）にあるので、1 ページ 100 個の「判断する」を越えずに届く（Bolt 39d2 で Shift+Tab で画面の
     * 終わりから戻る書き方にしたのを戻した）。
     */
    @もし("キー操作だけで判断を待つ隔離の {string} を開く")
    public void ページ送りのリンクを開く(String name) {
        page().locator("body").focus();
        browser.tabUntilFocused(pageLink(name));
        page().keyboard().press("Enter");
        page().waitForURL(url -> url.contains("page="));
        browser.checkAccessibility();
    }

    @かつ("判断を待つ隔離の {string} へのリンクが示される")
    public void ページ送りのリンクが示される(String name) {
        assertThat(pageLink(name)).isVisible();
    }

    private Locator heading() {
        return page().getByRole(
                        AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName(HEADING).setExact(true));
    }

    private Locator entryLink() {
        return page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName(ENTRY_LINK).setExact(true));
    }

    private Locator pageLink(String name) {
        return page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator table() {
        return page().getByRole(
                        AriaRole.TABLE,
                        new Page.GetByRoleOptions().setName(TABLE_CAPTION).setExact(true));
    }

    private Locator rows() {
        return table().locator("tbody tr");
    }

    private static Locator cell(Locator row, int index) {
        return row.locator("td").nth(index);
    }

    /** 内容の違う原本の注記（IngestionService の注記の文）。 */
    private static Pattern contentMismatchNote(int lineNumber) {
        return Pattern.compile("^" + lineNumber + " 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました。$");
    }
}

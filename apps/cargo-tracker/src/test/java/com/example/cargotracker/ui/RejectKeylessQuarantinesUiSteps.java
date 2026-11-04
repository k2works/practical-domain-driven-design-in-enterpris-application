package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.regex.Pattern;

/**
 * データ責任者の S-17 隔離・差異の確認の、1 つの取込の形式の誤りのまとめての却下（US-14 AC4、UC-15 2a。Bolt 39d2b、【業務の判断】(g)・(h)）の
 * 画面の層のステップ定義。取り込みは S-16 のステップ定義（{@link ExternalDataIngestionUiSteps}）、一覧の入口と件数の文は S-17 の一覧の
 * ステップ定義（{@link UndecidedRecordUiSteps}）、却下の後の知らせは判断の画面のステップ定義（{@link RejectQuarantineUiSteps}）が受け持ち、
 * この定義は取込で絞った一覧の「この取込の形式の誤りをまとめて却下する（N 件）」から先（まとめての却下の画面の中身、理由の欄、リンクの有無）を
 * 確かめる。
 */
public class RejectKeylessQuarantinesUiSteps {

    private static final Pattern LIST_URL = Pattern.compile(".*/staff/external-data/undecided-records\\?.*");
    private static final Pattern KEYLESS_REJECTION_URL =
            Pattern.compile(".*/staff/external-data/undecided-records/keyless-rejection\\?ingestion=[0-9a-f-]{36}$");
    private static final String HEADING = "形式の誤りのまとめての却下";
    private static final String REASON_LABEL = "却下の理由";
    private static final String BUTTON = "まとめて却下する";
    /** 一覧のまとめての却下のリンクの名前（件数は 3 桁の区切り）。 */
    private static final Pattern LINK_NAME = Pattern.compile("^この取込の形式の誤りをまとめて却下する（[0-9,]+ 件）$");

    private static final String EXCLUDES_MISMATCH = "内容の違う原本は含みません。一覧で 1 件ずつ判断してください。";
    private static final String KEYLESS_GUIDE = "形式の誤りは採用できません。直したファイルを取り込み直してください。";
    private static final String IRREVOCABLE = "却下すると取り消せません。誤って却下したときは、提供元に同じ原本を送り直してもらってください。";

    private final BrowserSession browser;
    private final ExternalDataIngestionUiSteps ingestion;

    public RejectKeylessQuarantinesUiSteps(BrowserSession browser, ExternalDataIngestionUiSteps ingestion) {
        this.browser = browser;
        this.ingestion = ingestion;
    }

    private Page page() {
        return browser.page();
    }

    /** Bolt 39d2b の行 1。件数の文の後に、件数を名前に含むまとめての却下のリンクがある。 */
    @かつ("隔離・差異の確認に {string} のリンクが示される")
    public void まとめての却下のリンクが示される(String name) {
        assertThat(link(name)).isVisible();
        browser.checkAccessibility();
    }

    /** Bolt 39d2b の行 2・22。形式の誤りが 0 件の取込の一覧は、押しても行き先のないリンクを出さない。 */
    @かつ("形式の誤りをまとめて却下するリンクは示されない")
    public void まとめての却下のリンクは示されない() {
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LINK_NAME)))
                .hasCount(0);
    }

    /** Bolt 39d2b の行 1・18。リンクへ先頭から Tab で進めて押す（ページ送りと同じく表の前にある）。 */
    @もし("キー操作だけで {string} を開く")
    public void まとめての却下を開く(String name) {
        page().locator("body").focus();
        browser.tabUntilFocused(link(name));
        page().keyboard().press("Enter");
        page().waitForURL(KEYLESS_REJECTION_URL);
        browser.checkAccessibility();
    }

    /** Bolt 39d2b の行 3。取込の情報源と件数、内容の違う原本を含まないこと、取り込み直しの案内、取り消せないことの文。 */
    @ならば("形式の誤りのまとめての却下の画面に取込の情報源と {string} と内容の違う原本を含まないことと却下は取り消せないことが示される")
    public void まとめての却下の画面の中身が示される(String count) {
        assertThat(heading()).isVisible();
        assertThat(definition("取り込んだ日時")).containsText("Asia/Tokyo");
        assertThat(definition("情報源")).hasText(ingestion.sourceLabel());
        assertThat(definition("形式の誤り")).hasText(count.substring("形式の誤り ".length()));
        assertThat(text(EXCLUDES_MISMATCH)).isVisible();
        assertThat(text(KEYLESS_GUIDE)).isVisible();
        assertThat(text(IRREVOCABLE)).isVisible();
        assertThat(button()).isVisible();
        browser.checkAccessibility();
    }

    /** 理由の欄へ Tab で進めて書き、「まとめて却下する」へ Tab で進めて押す。 */
    @もし("キー操作だけでまとめての却下の理由に {string} と書いてまとめて却下する")
    public void 理由を書いてまとめて却下する(String reason) {
        page().locator("body").focus();
        browser.tabUntilFocused(reasonField());
        page().keyboard().insertText(reason);
        submit();
    }

    @もし("キー操作だけでまとめての却下の理由に何も書かずにまとめて却下する")
    public void 何も書かずにまとめて却下する() {
        page().locator("body").focus();
        submit();
    }

    private void submit() {
        browser.tabUntilFocused(button());
        page().keyboard().press("Enter");
        page().waitForLoadState();
    }

    /** Bolt 39d2b の行 7。エラー要約のリンクは理由の欄を指し、欄は誤りを説明に結ぶ（判断の画面と同じ部品）。 */
    @ならば("まとめての却下のエラー要約にフォーカスが移り、理由の欄に {string} が示される")
    public void 理由の欄の誤りが示される(String message) {
        assertThat(heading()).isVisible();
        Locator summary = page().locator("#error-summary");
        assertThat(summary).isFocused();
        assertThat(summary.getByRole(AriaRole.LINK)).hasText(new String[] {message});
        assertThat(summary.getByRole(AriaRole.LINK)).hasAttribute("href", "#reason");
        assertThat(page().locator("#reason-error")).hasText(message);
        assertThat(reasonField()).hasAttribute("aria-invalid", "true");
        assertThat(reasonField()).hasAttribute("aria-describedby", Pattern.compile(".*\\breason-error\\b.*"));
        browser.checkAccessibility();
    }

    /** Bolt 39d2b の行 3。まとめての却下の画面の「一覧に戻る」は取込で絞った一覧へ戻る。 */
    @もし("キー操作だけでまとめての却下の画面から一覧に戻る")
    public void 一覧に戻る() {
        page().locator("body").focus();
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("一覧に戻る").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL(LIST_URL);
        browser.checkAccessibility();
    }

    private Locator link(String name) {
        return page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator heading() {
        return page().getByRole(
                        AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName(HEADING).setExact(true));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    private Locator text(String value) {
        return page().getByText(value, new Page.GetByTextOptions().setExact(true));
    }

    private Locator reasonField() {
        return page().getByRole(
                        AriaRole.TEXTBOX,
                        new Page.GetByRoleOptions().setName(REASON_LABEL).setExact(true));
    }

    private Locator button() {
        return page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(BUTTON).setExact(true));
    }
}

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
 * データ責任者の S-17 隔離・差異の確認の判断の画面と却下（US-14 AC4、UC-15 3b。Bolt 39d2）の画面の層のステップ定義。取り込みは S-16 の
 * ステップ定義（{@link ExternalDataIngestionUiSteps}）、一覧の入口と件数の文は S-17 の一覧のステップ定義（{@link UndecidedRecordUiSteps}）が
 * 受け持ち、この定義は一覧の「判断する」から先（判断の画面の中身、却下の理由の欄、却下の後の知らせ）を確かめる。
 */
public class RejectQuarantineUiSteps {

    private static final Pattern LIST_URL = Pattern.compile(".*/staff/external-data/undecided-records\\?.*");
    private static final Pattern DECISION_URL =
            Pattern.compile(".*/staff/external-data/undecided-records/[0-9a-f-]{36}(\\?.*)?$");
    private static final String TABLE_CAPTION = "判断を待つ隔離";
    private static final String DECISION_HEADING = "隔離の判断";
    private static final String LIST_HEADING = "隔離・差異の確認";
    private static final String REASON_LABEL = "却下の理由";
    private static final String IRREVOCABLE = "却下すると取り消せません。誤って却下したときは、提供元に同じ原本を送り直してもらってください。";
    private static final String KEYLESS_GUIDE = "形式の誤りは採用できません。直したファイルを取り込み直してください。";

    private static final int LINES = 2;
    private static final int KIND = 3;

    private final BrowserSession browser;
    private final ExternalDataIngestionUiSteps ingestion;

    public RejectQuarantineUiSteps(BrowserSession browser, ExternalDataIngestionUiSteps ingestion) {
        this.browser = browser;
        this.ingestion = ingestion;
    }

    private Page page() {
        return browser.page();
    }

    /** Bolt 39d2 の行 17。行の範囲と種類で一覧の行を選び、その行の「判断する」へ Tab で進めて押す。 */
    @もし("キー操作だけで判断を待つ隔離の {int} 行目の内容の違う原本の判断するを開く")
    public void 内容の違う原本の判断するを開く(int line) {
        openDecision(line, "内容の違う原本");
    }

    @かつ("キー操作だけで判断を待つ隔離の {int} 行目の形式の誤りの判断するを開く")
    public void 形式の誤りの判断するを開く(int line) {
        openDecision(line, "形式の誤り");
    }

    private void openDecision(int line, String kind) {
        Locator row = page().getByRole(
                        AriaRole.TABLE,
                        new Page.GetByRoleOptions().setName(TABLE_CAPTION).setExact(true))
                .locator("tbody tr")
                .filter(new Locator.FilterOptions()
                        .setHas(page().locator("td:nth-child(" + (LINES + 1) + "):text-is('" + line + " 行目')")))
                .filter(new Locator.FilterOptions()
                        .setHas(page().locator("td:nth-child(" + (KIND + 1) + "):text-is('" + kind + "')")));
        assertThat(row).hasCount(1);
        // 読み上げは「判断する」に行の範囲と外部 ID を足す（同じ名前のリンクが並ばない）
        Locator link = row.getByRole(
                AriaRole.LINK, new Locator.GetByRoleOptions().setName(Pattern.compile("^判断する.*" + line + " 行目")));
        page().locator("body").focus();
        browser.tabUntilFocused(link);
        page().keyboard().press("Enter");
        page().waitForURL(DECISION_URL);
        browser.checkAccessibility();
    }

    /** Bolt 39d2 の行 18（内容の違う原本）。一覧と同じ項目と隔離の理由の全文、却下は取り消せないことの文。 */
    @ならば("隔離の判断の画面に {int} 行目の内容の違う原本の中身と隔離の理由の全文と却下は取り消せないことが示される")
    public void 内容の違う原本の中身が示される(int line) {
        assertThat(heading(DECISION_HEADING)).isVisible();
        assertThat(definition("取り込んだ日時")).containsText("Asia/Tokyo");
        assertThat(definition("情報源")).hasText(ingestion.sourceLabel());
        assertThat(definition("行")).hasText(line + " 行目");
        assertThat(definition("種類")).hasText("内容の違う原本");
        assertThat(definition("外部 ID・外部版")).hasText(Pattern.compile("^V2611E-[0-9a-f]{8}・1$"));
        assertThat(definition("隔離の理由")).hasText(Pattern.compile("^" + line + " 行目: 同じ外部 ID・外部版で内容の違う原本を受け取りました。$"));
        assertThat(page().getByText(IRREVOCABLE, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(page().getByText(KEYLESS_GUIDE)).hasCount(0);
        browser.checkAccessibility();
    }

    /** Bolt 39d2 の行 18（キーのない隔離。【業務の判断】(a)）。 */
    @ならば("隔離の判断の画面に形式の誤りは採用できず直したファイルを取り込み直す案内が示される")
    public void 形式の誤りの案内が示される() {
        assertThat(heading(DECISION_HEADING)).isVisible();
        assertThat(definition("種類")).hasText("形式の誤り");
        assertThat(page().getByText(KEYLESS_GUIDE, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(page().getByText(IRREVOCABLE, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(button()).isVisible();
        browser.checkAccessibility();
    }

    /** 理由の欄へ Tab で進めて書き、「却下する」へ Tab で進めて押す。 */
    @もし("キー操作だけで却下の理由に {string} と書いて却下する")
    public void 理由を書いて却下する(String reason) {
        page().locator("body").focus();
        browser.tabUntilFocused(reasonField());
        page().keyboard().insertText(reason);
        submit();
    }

    @もし("キー操作だけで却下の理由に何も書かずに却下する")
    public void 何も書かずに却下する() {
        page().locator("body").focus();
        submit();
    }

    private void submit() {
        browser.tabUntilFocused(button());
        page().keyboard().press("Enter");
        page().waitForLoadState();
    }

    @ならば("隔離・差異の確認に知らせ {string} が示される")
    public void 知らせが示される(String notice) {
        page().waitForURL(LIST_URL);
        assertThat(heading(LIST_HEADING)).isVisible();
        assertThat(page().getByRole(AriaRole.STATUS)).hasText(notice);
        browser.checkAccessibility();
    }

    /** Bolt 39d2 の行 7。エラー要約のリンクは理由の欄を指し、欄は誤りを説明に結ぶ。 */
    @ならば("却下のエラー要約にフォーカスが移り、理由の欄に {string} が示される")
    public void 理由の欄の誤りが示される(String message) {
        assertThat(heading(DECISION_HEADING)).isVisible();
        Locator summary = page().locator("#error-summary");
        assertThat(summary).isFocused();
        assertThat(summary.getByRole(AriaRole.LINK)).hasText(new String[] {message});
        assertThat(summary.getByRole(AriaRole.LINK)).hasAttribute("href", "#reason");
        assertThat(page().locator("#reason-error")).hasText(message);
        assertThat(reasonField()).hasAttribute("aria-invalid", "true");
        assertThat(reasonField()).hasAttribute("aria-describedby", Pattern.compile(".*\\breason-error\\b.*"));
        browser.checkAccessibility();
    }

    /** Bolt 39d2 の行 17。判断の画面の「一覧に戻る」は元の取込とページの一覧へ戻る。 */
    @もし("キー操作だけで判断の画面から一覧に戻る")
    public void 一覧に戻る() {
        page().locator("body").focus();
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("一覧に戻る").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL(LIST_URL);
        browser.checkAccessibility();
    }

    private Locator heading(String name) {
        return page().getByRole(
                        AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    private Locator reasonField() {
        return page().getByRole(
                        AriaRole.TEXTBOX,
                        new Page.GetByRoleOptions().setName(REASON_LABEL).setExact(true));
    }

    private Locator button() {
        return page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("却下する").setExact(true));
    }
}

package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.List;
import org.assertj.core.api.Assertions;

/**
 * データ責任者の S-17 の判断の画面の採用（US-14 AC4、UC-15 の主成功シナリオ 5。Bolt 39d3b）の画面の層のステップ定義。取り込みは S-16 の
 * ステップ定義（{@link ExternalDataIngestionUiSteps}）、一覧の「判断する」と知らせは却下のステップ定義（{@link RejectQuarantineUiSteps}）、値の
 * 領域は値の見比べのステップ定義（{@link CompareQuarantineValuesUiSteps}）が受け持ち、この定義は「却下する」「採用する」の 2 つの見出しの欄と
 * 採用の理由の欄と採用を確かめる。
 */
public class AdoptQuarantineUiSteps {

    private static final String REJECTION_HEADING = "却下する";
    private static final String ADOPTION_HEADING = "採用する";
    private static final String ADOPTED_VALUES_HEADING = "この外部版の採用値";
    private static final String REASON_LABEL = "採用の理由";
    private static final String IRREVOCABLE = "採用すると取り消せません。誤って採用したときは、提供元に新しい外部版で正しい原本を送ってもらってください。";
    private static final String KEYLESS_GUIDE = "形式の誤りは採用できません。直したファイルを取り込み直してください。";
    private static final String TRACKING_NUMBER_DIFFERS_GUIDE =
            "追跡番号がこの外部版の採用値と違うので採用できません。追跡番号を直した実績は、追跡管理者に正しい追跡記録へ手入力してもらってください（新しい外部版で送り直しても差異になります）。";
    private static final String SOURCE_NOT_APPROVED_GUIDE =
            "情報源が承認済みではないため採用できません。受け取った値を使うときは、運用の手順で情報源を承認し直してから、この隔離を開き直してください。";
    private static final String REFUSED_TITLE = "採用できませんでした - 隔離の判断 - cargo-tracker";
    private static final String ERROR_SUMMARY = "#error-summary";
    private static final String REFUSAL_PARAGRAPH = "#adoption-refusal";

    private final BrowserSession browser;

    public AdoptQuarantineUiSteps(BrowserSession browser) {
        this.browser = browser;
    }

    private Page page() {
        return browser.page();
    }

    /** Bolt 39d3b の行 12・15。値の領域の後に「却下する」（先）と「採用する」（後）の見出しの欄が並び、採用は取り消せないことを示す。 */
    @かつ("隔離の判断の画面の値の後に却下するの欄と採用するの欄がこの順に見出しを付けて示される")
    public void 却下と採用の欄がこの順に示される() {
        List<String> headings = page().locator("main h2").allTextContents();
        Assertions.assertThat(headings)
                .containsSubsequence(ADOPTED_VALUES_HEADING, REJECTION_HEADING, ADOPTION_HEADING);
        assertThat(reasonField()).isVisible();
        assertThat(reasonField()).not().hasAttribute("aria-required", "true");
        assertThat(page().getByText(IRREVOCABLE, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(button()).isVisible();
        browser.checkAccessibility();
    }

    /** 採用の理由の欄へ Tab で進めて書き、「採用する」へ Tab で進めて押す。 */
    @もし("キー操作だけで採用の理由に {string} と書いて採用する")
    public void 理由を書いて採用する(String reason) {
        page().locator("body").focus();
        browser.tabUntilFocused(reasonField());
        page().keyboard().insertText(reason);
        submit();
    }

    /** Bolt 39d3b の行 13（採用の理由は任意。【業務の判断】(d)）。 */
    @もし("キー操作だけで採用の理由を書かずに採用する")
    public void 理由を書かずに採用する() {
        page().locator("body").focus();
        submit();
    }

    private void submit() {
        browser.tabUntilFocused(button());
        page().keyboard().press("Enter");
        page().waitForLoadState();
    }

    /** Bolt 39d3b の行 15（キーのない隔離は採用の欄を出さない。【業務の判断】(a)）。今の文は「却下する」の欄の後に置く。 */
    @ならば("隔離の判断の画面に採用するの欄は示されず、却下するの欄の後に形式の誤りは採用できない案内が示される")
    public void 採用の欄は示されない() {
        採用の欄がなく却下の欄の後に案内が示される(KEYLESS_GUIDE);
    }

    /** Bolt 39d3b2 の行 16（追跡番号が違う主要実績の隔離は採用の欄を出さない。【業務の判断】(m)）。 */
    @ならば("隔離の判断の画面に採用するの欄は示されず、却下するの欄の後に追跡番号が違うので採用できない案内が示される")
    public void 追跡番号が違う隔離に採用の欄は示されない() {
        採用の欄がなく却下の欄の後に案内が示される(TRACKING_NUMBER_DIFFERS_GUIDE);
    }

    /** Bolt 39d3b2 の行 11（エラー要約へのフォーカスは transport-request-form.js が移す。ブラウザーでしか確かめられない）。 */
    @ならば("採用されずに判断の画面に戻り、エラー要約にフォーカスが移って情報源が承認済みでない理由が示される")
    public void エラー要約にフォーカスが移って理由が示される() {
        assertThat(page()).hasTitle(REFUSED_TITLE);
        assertThat(page().locator(ERROR_SUMMARY)).isFocused();
        assertThat(refusalLink()).hasText(SOURCE_NOT_APPROVED_GUIDE);
        assertThat(heading(ADOPTION_HEADING)).hasCount(0);
        assertThat(heading(REJECTION_HEADING)).isVisible();
        browser.checkAccessibility();
    }

    @もし("キー操作だけでエラー要約の理由の文のリンクを開く")
    public void エラー要約のリンクを開く() {
        browser.tabUntilFocused(refusalLink());
        page().keyboard().press("Enter");
    }

    /** Bolt 39d3b2 の行 11（段落の tabindex="-1" がブラウザーの標準のフラグメント移動の受け口）。 */
    @ならば("却下するの欄の後の理由の文にフォーカスが移る")
    public void 理由の文にフォーカスが移る() {
        Locator paragraph = page().locator(REFUSAL_PARAGRAPH);
        assertThat(paragraph).isFocused();
        assertThat(paragraph).hasText(SOURCE_NOT_APPROVED_GUIDE);
        browser.checkAccessibility();
    }

    private Locator refusalLink() {
        return page().locator(ERROR_SUMMARY + " a[href='" + REFUSAL_PARAGRAPH + "']");
    }

    private void 採用の欄がなく却下の欄の後に案内が示される(String guide) {
        assertThat(heading(REJECTION_HEADING)).isVisible();
        assertThat(heading(ADOPTION_HEADING)).hasCount(0);
        assertThat(reasonField()).hasCount(0);
        assertThat(button()).hasCount(0);
        assertThat(page().locator(
                                "section:has(> h2:text-is('" + REJECTION_HEADING + "')) ~ p:text-is('" + guide + "')"))
                .hasCount(1);
        browser.checkAccessibility();
    }

    private Locator heading(String name) {
        return page().getByRole(
                        AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator reasonField() {
        return page().getByRole(
                        AriaRole.TEXTBOX,
                        new Page.GetByRoleOptions().setName(REASON_LABEL).setExact(true));
    }

    private Locator button() {
        return page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(ADOPTION_HEADING).setExact(true));
    }
}

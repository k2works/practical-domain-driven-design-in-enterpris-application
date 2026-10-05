package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 荷主の再提出（C-02 見積依頼の一覧、C-04 見積依頼の詳細、C-03 の編集）の画面の層のステップ定義。
 * 対象の見積依頼は、同じシナリオで荷主が提出したもの（{@link UiScenarioState}）とする。
 */
public class ResubmitTransportRequestUiSteps {

    private static final String ORIGIN = "出発地（UN/LOCODE）";
    private static final String DESTINATION = "目的地（UN/LOCODE）";
    private static final String EDIT_LINK = "編集して出し直す";
    private static final String RESUBMIT = "出し直す";
    private static final String COMMERCIAL_INVOICE = "商業送り状（任意）";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public ResubmitTransportRequestUiSteps(
            BrowserSession browser,
            UiScenarioState state,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.state = state;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private String number() {
        return state.transportRequestNumber();
    }

    private Locator field(String label) {
        return page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }

    private Locator link(String name) {
        return page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name));
    }

    private Locator resubmitButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(RESUBMIT));
    }

    private Locator nextButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("次へ"));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    @前提("荷主が提出した見積依頼の編集画面を開いている")
    public void 編集画面を開いている() {
        page().navigate(baseUrl + "/customer/transport-requests/" + number() + "/edit");
        browser.checkAccessibility();
    }

    @ならば("見積依頼の一覧に提出した見積依頼が {string} で表示される")
    public void 一覧に状態とともに表示される(String status) {
        Locator row = page().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHas(link(number())));
        assertThat(row).hasCount(1);
        assertThat(row).containsText(status);
    }

    @もし("見積依頼の一覧から提出した見積依頼を開く")
    public void 一覧から開く() {
        link(number()).click();
        page().waitForURL(SubmitTransportRequestUiSteps.DETAIL_URL);
        browser.checkAccessibility();
    }

    @ならば("見積依頼の詳細に差戻しの理由 {string} と不足事項 {string} が表示される")
    public void 差戻しの理由と不足事項が表示される(String reason, String missingItems) {
        assertThat(definition("差戻しの理由")).hasText(reason);
        assertThat(definition("不足事項")).hasText(missingItems);
    }

    @もし("編集を開き、キー操作だけで目的地を {string} に直して出し直す")
    public void キー操作だけで直して出し直す(String destination) {
        page().locator("body").focus();
        tabUntilFocused(link(EDIT_LINK));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/transport-requests/" + number() + "/edit");
        browser.checkAccessibility();
        page().locator("body").focus();
        tabUntilFocused(field(DESTINATION));
        page().keyboard().press("ControlOrMeta+A");
        page().keyboard().type(destination);
        // 段階入力（Bolt 8）: 見えている「次へ」を Enter で押して確認の段階まで進む
        for (int i = 0; i < 3; i++) {
            tabUntilFocused(nextButton());
            page().keyboard().press("Enter");
        }
        tabUntilFocused(resubmitButton());
        page().keyboard().press("Enter");
        page().waitForURL(SubmitTransportRequestUiSteps.DETAIL_URL);
        browser.checkAccessibility();
    }

    @ならば("編集画面の書類の段階に、ファイルを選ぶとその種類の書類を差し替えることが示される")
    public void 差し替えることが示される() {
        assertThat(page().locator("#step-documents")).containsText("ファイルを選んだ種類は、前の版の書類を差し替えます。選ばなかった種類は引き継ぎます");
    }

    @もし("商業送り状に {string} を選んで出し直す")
    public void 商業送り状を選んで出し直す(String fileName) {
        for (int i = 0; i < 2; i++) {
            nextButton().click();
        }
        field(COMMERCIAL_INVOICE).setInputFiles(RequiredDocumentUiSteps.uiFile(fileName));
        nextButton().click();
        resubmitButton().click();
        page().waitForURL(SubmitTransportRequestUiSteps.DETAIL_URL);
        browser.checkAccessibility();
    }

    @もし("出発地を {string} に、目的地を空にして出し直す")
    public void 目的地を空にして出し直す(String origin) {
        field(ORIGIN).fill(origin);
        field(DESTINATION).fill("");
        for (int i = 0; i < 3; i++) {
            nextButton().click();
        }
        page().waitForResponse(
                        response -> "POST".equals(response.request().method()),
                        () -> resubmitButton().click());
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("見積依頼の詳細に {string} と状態 {string} が表示される")
    public void 詳細に結果と状態が表示される(String result, String status) {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(number() + " " + result);
        assertThat(definition("状態")).hasText(status);
    }

    /** Tab を押してフォーカスを進め、目的の要素に届いたら止まる（届かなければ失敗する）。 */
    private void tabUntilFocused(Locator target) {
        for (int i = 0; i < 30; i++) {
            page().keyboard().press("Tab");
            if ((Boolean) target.evaluate("element => element === document.activeElement")) {
                return;
            }
        }
        assertThat(target).isFocused();
    }
}

package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 営業担当者の審査（S-02 受付一覧、S-03 審査）の画面の層のステップ定義。画面の文言とキー操作で操作する。
 * 審査する見積依頼は、同じシナリオで荷主が提出したもの（{@link UiScenarioState}）とする。
 */
public class ReviewTransportRequestUiSteps {

    private static final String RATIONALE = "根拠";
    private static final String REASON = "理由";
    private static final String MISSING_ITEMS = "不足事項（任意）";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public ReviewTransportRequestUiSteps(
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

    private void open(String path) {
        browser.navigate(baseUrl + path);
        browser.checkAccessibility();
    }

    private String number() {
        return state.transportRequestNumber();
    }

    private Locator field(String label) {
        return page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }

    private Locator button(String name) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    @もし("営業担当者が{word}を開く")
    public void 営業担当者が画面を開く(String screen) {
        switch (screen) {
            case "受付一覧" -> open("/staff/transport-requests");
            case "提出した見積依頼の審査画面" -> open("/staff/transport-requests/" + number());
            case "提出した見積依頼の見積り画面" -> open("/staff/transport-requests/" + number() + "/quotations/1");
            case "提出した見積依頼の再見積り画面" -> open("/staff/transport-requests/" + number() + "/quotations/1/requotation");
            default -> throw new IllegalArgumentException("シナリオの画面名: " + screen);
        }
    }

    @前提("営業担当者が提出した見積依頼を理由 {string} と不足事項 {string} で差し戻している")
    public void 差し戻している(String reason, String missingItems) {
        open("/staff/transport-requests/" + number());
        field(REASON).fill(reason);
        field(MISSING_ITEMS).fill(missingItems);
        page().waitForResponse(
                        response -> "POST".equals(response.request().method()),
                        () -> button("差し戻す").click());
        page().waitForURL("**/staff/transport-requests");
    }

    @ならば("受付一覧に提出した見積依頼が表示される")
    public void 受付一覧に表示される() {
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(number())))
                .isVisible();
    }

    @ならば("受付一覧の審査中に提出した見積依頼が表示されない")
    public void 受付一覧の審査中に表示されない() {
        assertThat(page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("審査中の見積依頼（最初の提出時刻の古い順）"))
                        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(number())))
                .hasCount(0);
    }

    @もし("受付一覧から提出した見積依頼を開く")
    public void 受付一覧から開く() {
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(number()))
                .click();
        page().waitForURL("**/staff/transport-requests/" + number());
        browser.checkAccessibility();
    }

    @もし("根拠 {string} をキー操作だけで入力して審査を確定する")
    public void キー操作だけで審査を確定する(String rationale) {
        page().locator("body").focus();
        tabUntilFocused(field(RATIONALE));
        page().keyboard().type(rationale);
        page().keyboard().press("Tab");
        assertThat(button("審査を確定する")).isFocused();
        // 確定すると見積りの作成（S-04）へリダイレクトする（PRG。Bolt 10）。移り終わってから検査する
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/transport-requests/" + number() + "/quotations/new");
        browser.checkAccessibility();
    }

    @もし("不足事項 {string} だけを入力して差し戻す")
    public void 理由なしで差し戻す(String missingItems) {
        field(MISSING_ITEMS).fill(missingItems);
        page().waitForResponse(
                        response -> "POST".equals(response.request().method()),
                        () -> button("差し戻す").click());
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("審査画面のエラー要約にフォーカスが移り、理由の誤りが示される")
    public void エラー要約に理由の誤りが示される() {
        Locator summary = page().locator("#error-summary");
        assertThat(summary).isFocused();
        assertThat(summary.getByRole(
                        AriaRole.LINK, new Locator.GetByRoleOptions().setName(Pattern.compile("^" + REASON))))
                .isVisible();
    }

    @ならば("入力した不足事項 {string} が残っている")
    public void 不足事項が残っている(String missingItems) {
        assertThat(field(MISSING_ITEMS)).hasValue(missingItems);
    }

    /** Tab を押してフォーカスを進め、目的の要素に届いたら止まる（届かなければ失敗する）。 */
    private void tabUntilFocused(Locator target) {
        for (int i = 0; i < 20; i++) {
            page().keyboard().press("Tab");
            if ((Boolean) target.evaluate("element => element === document.activeElement")) {
                return;
            }
        }
        assertThat(target).isFocused();
    }
}

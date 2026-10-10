package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 荷主担当者の C-10 追跡の照会の画面の層のステップ定義（US-09 AC1。Bolt 27）。追跡記録は、同じシナリオで営業担当者が確定した予約の
 * 追跡番号（{@link UiScenarioState}）のものとする。「一覧の先頭」は、シナリオを逐次に実行し、追跡の開始時刻が実時間で増えることを
 * 前提にしている（S-11 と同じ）。
 */
public class CustomerTrackingUiSteps {

    private static final String LIST_PATH = "/customer/tracking-records";
    private static final String LIST_CAPTION = "追跡中の貨物（本予約の確定の新しい順）";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public CustomerTrackingUiSteps(
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

    private Locator list() {
        return page().getByRole(AriaRole.TABLE, new Page.GetByRoleOptions().setName(LIST_CAPTION));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    /** 荷主担当者でログインし直してから、ナビの「追跡の照会」の行き先を開く。 */
    @もし("荷主担当者が追跡の照会を開く")
    public void 荷主担当者が追跡の照会を開く() {
        browser.navigate(baseUrl + LIST_PATH);
        page().waitForURL("**" + LIST_PATH);
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡の照会")))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("追跡の照会の一覧の先頭に確定した予約の追跡番号が現在状態 {string} とともに示される")
    public void 一覧の先頭に示される(String status) {
        Locator firstRow = list().locator("tbody tr").first();
        assertThat(firstRow.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())))
                .isVisible();
        assertThat(firstRow.locator("td").nth(1)).hasText(status);
        browser.checkAccessibility();
    }

    @もし("キー操作だけで追跡の照会の一覧の先頭の貨物を開く")
    public void 一覧の先頭の貨物を開く() {
        page().locator("body").focus();
        tabUntilFocused(list().locator("tbody tr")
                .first()
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())));
        page().keyboard().press("Enter");
        page().waitForURL("**" + LIST_PATH + "/" + state.trackingNumber());
    }

    @ならば("照会の結果に現在状態 {string} と予定と主要実績 {string} と出典 {string} が示される")
    public void 照会の結果が示される(String status, String milestone, String source) {
        assertThat(page().getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName("追跡の照会 " + state.trackingNumber())))
                .isVisible();
        assertThat(definition("現在の状態")).hasText(status);
        Locator legs = page().getByRole(
                        AriaRole.LIST, new Page.GetByRoleOptions().setName("予定").setExact(true));
        assertThat(legs.getByRole(AriaRole.LISTITEM).first()).containsText("区間 1");
        Locator milestones = page().getByRole(
                        AriaRole.LIST,
                        new Page.GetByRoleOptions().setName("主要実績").setExact(true));
        assertThat(milestones.getByRole(AriaRole.LISTITEM).first()).containsText(milestone);
        assertThat(milestones.getByRole(AriaRole.LISTITEM).first()).containsText(source);
        browser.checkAccessibility();
    }

    @もし("キー操作だけで照会の結果から追跡の照会へ戻る")
    public void 照会の結果から戻る() {
        page().locator("body").focus();
        tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("追跡の照会へ戻る").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**" + LIST_PATH);
    }

    @ならば("追跡番号が見つからない案内が示される")
    public void 見つからない案内が示される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡番号が見つかりません")))
                .isVisible();
    }

    @もし("キー操作だけで見つからない案内から追跡の照会へ戻る")
    public void 見つからない案内から戻る() {
        照会の結果から戻る();
    }

    @もし("キー操作だけで追跡番号 {string} を入れて照会する")
    public void 追跡番号を入れて照会する(String trackingNumber) {
        page().locator("body").focus();
        tabUntilFocused(page().getByLabel("追跡番号", new Page.GetByLabelOptions().setExact(true)));
        page().keyboard().type(trackingNumber);
        tabUntilFocused(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("照会する")));
        // 送信の後の移動の途中で検査しないよう、行き先（入力の誤りは一覧の URL に入力を付けたもの、正しければ結果）に移り終わってから
        // 検査する（Bolt 26c の axe の注入の失敗と同じ）
        page().keyboard().press("Enter");
        page().waitForURL(
                        url -> url.contains("trackingNumber=") || url.matches(".*/customer/tracking-records/[^/?]+$"));
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("追跡の照会のエラー要約にフォーカスが移り、{string} の誤りが示される")
    public void エラー要約が示される(String field) {
        Locator summary = page().locator("#error-summary");
        assertThat(summary).isFocused();
        assertThat(summary).containsText(field);
    }

    @ならば("追跡番号の入力に {string} が残っている")
    public void 入力が残っている(String trackingNumber) {
        assertThat(page().getByLabel("追跡番号", new Page.GetByLabelOptions().setExact(true)))
                .hasValue(trackingNumber);
    }

    private void tabUntilFocused(Locator target) {
        for (int i = 0; i < 80; i++) {
            page().keyboard().press("Tab");
            if ((Boolean) target.evaluate("element => element === document.activeElement")) {
                return;
            }
        }
        assertThat(target).isFocused();
    }
}

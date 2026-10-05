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
 * 見積りの作成と提示（S-04）と、荷主の見積依頼の詳細（C-04）の見積りの節の画面の層のステップ定義（US-03）。
 * 見積りを作る見積依頼は、同じシナリオで荷主が提出したもの（{@link UiScenarioState}）とする。
 * 有効期限と経路方針の日時は、実際の時計で動くため十分に先の日時にする。
 */
public class QuotationUiSteps {

    private static final String CURRENCY = "通貨";
    private static final String EXPIRES_AT = "有効期限（日本時間）";
    private static final String VIA = "主な経由地（UN/LOCODE、カンマ区切り、任意）";
    private static final String DEPARTURE_AT = "概算の出発日時（日本時間）";
    private static final String ARRIVAL_AT = "概算の到着日時（日本時間）";
    private static final String CALCULATE = "算出する";
    private static final String PRESENT = "社内承認して提示する";
    private static final String[][] STANDARD_LINES = {{"海上運賃", "3200.00", "年間契約 2026-A"}, {"燃料調整金", "530.00", ""}};

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public QuotationUiSteps(
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

    private String quotationsPath() {
        return "/staff/transport-requests/" + number() + "/quotations";
    }

    private Locator field(String label) {
        return page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }

    private Locator button(String name) {
        return page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    private static String line(int lineNo, String item) {
        return "明細 " + lineNo + " の" + item;
    }

    @前提("営業担当者が提出した見積依頼の審査を確定している")
    public void 審査を確定している() {
        page().navigate(baseUrl + "/staff/transport-requests/" + number());
        field("根拠").fill("契約条件を確認した");
        page().waitForResponse(
                        response -> "POST".equals(response.request().method()),
                        () -> button("審査を確定する").click());
        page().waitForURL("**" + quotationsPath() + "/new");
    }

    @前提("営業担当者が提出した見積依頼の見積りを算出して提示している")
    public void 見積りを算出して提示している() {
        審査を確定している();
        for (int i = 0; i < STANDARD_LINES.length; i++) {
            field(line(i + 1, "内容")).fill(STANDARD_LINES[i][0]);
            field(line(i + 1, "金額")).fill(STANDARD_LINES[i][1]);
            field(line(i + 1, "参照した契約条件（任意）")).fill(STANDARD_LINES[i][2]);
        }
        page().getByRole(
                        AriaRole.RADIO,
                        new Page.GetByRoleOptions().setName("USD").setExact(true))
                .check();
        fillSchedule();
        button(CALCULATE).click();
        page().waitForURL("**" + quotationsPath() + "/1");
        button(PRESENT).click();
        page().waitForURL("**/staff/transport-requests");
    }

    @ならば("見積りの作成画面に提出した見積依頼の審査を確定したことが表示される")
    public void 見積りの作成画面に審査の確定が表示される() {
        page().waitForURL("**" + quotationsPath() + "/new");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("見積りの作成");
        // Playwright の正規表現はブラウザで評価され、Java の Pattern.quote が効かないため文字列で比べる
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(number() + " 版 1 の審査を確定しました");
    }

    @もし("キー操作だけで標準の料金明細と通貨と有効期限と経路方針を入れて算出する")
    public void キー操作だけで算出する() {
        page().locator("body").focus();
        tabUntilFocused(field(line(1, "内容")));
        for (int i = 0; i < STANDARD_LINES.length; i++) {
            if (i > 0) {
                tabTo(field(line(i + 1, "内容")));
            }
            page().keyboard().type(STANDARD_LINES[i][0]);
            tabTo(field(line(i + 1, "金額")));
            page().keyboard().type(STANDARD_LINES[i][1]);
            tabTo(field(line(i + 1, "参照した契約条件（任意）")));
            page().keyboard().type(STANDARD_LINES[i][2]);
        }
        tabUntilGroup(CURRENCY);
        page().keyboard().press("Space");
        tabTo(field(EXPIRES_AT));
        page().keyboard().type("2099-10-08 18:00");
        tabTo(field(VIA));
        page().keyboard().type("SGSIN");
        tabTo(field(DEPARTURE_AT));
        page().keyboard().type("2099-10-10 09:00");
        tabTo(field(ARRIVAL_AT));
        page().keyboard().type("2099-10-30 18:00");
        tabTo(button(CALCULATE));
        page().keyboard().press("Enter");
        page().waitForURL("**" + quotationsPath() + "/1");
        browser.checkAccessibility();
    }

    @ならば("算出した見積り {int} が承認待ちで、合計 {string} とともに表示される")
    public void 算出した見積りが表示される(int quotationNo, String total) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("見積り " + number() + " 見積 " + quotationNo);
        assertThat(definition("状態")).hasText("承認待ち");
        assertThat(definition("合計")).hasText(total);
    }

    @もし("キー操作だけで社内承認して提示する")
    public void キー操作だけで提示する() {
        page().locator("body").focus();
        tabUntilFocused(button(PRESENT));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/transport-requests");
        browser.checkAccessibility();
    }

    @ならば("受付一覧に提出した見積依頼の見積り {int} を提示したことが表示される")
    public void 提示したことが表示される(int quotationNo) {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(number() + " 見積 " + quotationNo + " を提示しました");
    }

    @ならば("見積依頼の詳細の見積りの節に料金明細 {string} と {string} と合計 {string} が表示される")
    public void 詳細に料金明細と合計が表示される(String first, String second, String total) {
        Locator quotation = quotationRegion();
        assertThat(quotation).containsText(first);
        assertThat(quotation).containsText(second);
        assertThat(quotation.locator("dt:text-is('合計') + dd")).hasText(total);
    }

    @ならば("見積りの節に有効期限 {string} と経由地 {string} と詳細な経路は経路設計者の承認後に確定する旨が表示される")
    public void 詳細に有効期限と経路方針が表示される(String expiresAt, String via) {
        Locator quotation = quotationRegion();
        assertThat(quotation.locator("dt:text-is('有効期限') + dd")).hasText(expiresAt);
        assertThat(quotation.locator("dt:text-is('主な経由地') + dd")).hasText(via);
        assertThat(quotation).containsText("詳細な経路は、経路設計者の承認後に確定します");
    }

    @もし("受付一覧の見積り作成中から提出した見積依頼を開く")
    public void 見積り作成中から開く() {
        page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("見積り作成中の見積依頼"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(number()))
                .click();
        page().waitForURL("**" + quotationsPath() + "/new");
        browser.checkAccessibility();
    }

    @ならば("提出した見積依頼の見積りの作成画面が表示される")
    public void 見積りの作成画面が表示される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("見積りの作成");
        assertThat(page().locator("body")).containsText(number() + " 版 1");
    }

    @もし("料金明細を入れずに有効期限 {string} を入れて算出する")
    public void 料金明細を入れずに算出する(String expiresAt) {
        field(EXPIRES_AT).fill(expiresAt);
        page().waitForResponse(
                        response -> "POST".equals(response.request().method()),
                        () -> button(CALCULATE).click());
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("エラー要約にフォーカスが移り、{string} が示される")
    public void エラー要約が示される(String message) {
        Locator summary = page().locator("#error-summary");
        assertThat(summary).isFocused();
        assertThat(summary).containsText(message);
    }

    @ならば("入力した有効期限 {string} が残っている")
    public void 有効期限が残っている(String expiresAt) {
        assertThat(field(EXPIRES_AT)).hasValue(expiresAt);
    }

    private Locator quotationRegion() {
        return page().getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName("見積り").setExact(true));
    }

    private void fillSchedule() {
        field(EXPIRES_AT).fill("2099-10-08 18:00");
        field(VIA).fill("SGSIN");
        field(DEPARTURE_AT).fill("2099-10-10 09:00");
        field(ARRIVAL_AT).fill("2099-10-30 18:00");
    }

    /** Tab で次の要素へ移り、その要素が目的の要素であることを確かめる（フォーカスの順序の確認）。 */
    private void tabTo(Locator target) {
        page().keyboard().press("Tab");
        assertThat(target).isFocused();
    }

    /** Tab を押してフォーカスを進め、目的の要素に届いたら止まる（届かなければ失敗する）。 */
    private void tabUntilFocused(Locator target) {
        for (int i = 0; i < 60; i++) {
            page().keyboard().press("Tab");
            if ((Boolean) target.evaluate("element => element === document.activeElement")) {
                return;
            }
        }
        assertThat(target).isFocused();
    }

    /** ラジオボタンのグループに届くまで Tab を進める（届かなければ失敗する）。 */
    private void tabUntilGroup(String legend) {
        Locator focused = page().getByRole(
                        AriaRole.GROUP,
                        new Page.GetByRoleOptions().setName(legend).setExact(true))
                .locator("input:focus");
        for (int i = 0; i < 60 && focused.count() == 0; i++) {
            page().keyboard().press("Tab");
        }
        assertThat(focused).hasCount(1);
    }
}

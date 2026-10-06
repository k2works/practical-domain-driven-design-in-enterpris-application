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
 * 荷主の見積りへの回答（C-05）と、回答の後の見積依頼の詳細（C-04）・受付一覧（S-02）・見積り（S-04）の画面の層の
 * ステップ定義（US-24 AC1。Bolt 12）。見積依頼は、同じシナリオで荷主が提出したもの（{@link UiScenarioState}）とする。
 */
public class RouteDesignUiSteps {

    private static final String RESPOND = "見積りに回答する";
    private static final String PROCEED = "この条件で詳細経路設計へ進む";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public RouteDesignUiSteps(
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

    private Locator link(String name) {
        return page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator button(String name) {
        return page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    @もし("キー操作だけで見積りへの回答を開き、詳細経路設計へ進む")
    public void キー操作だけで詳細経路設計へ進む() {
        page().locator("body").focus();
        tabUntilFocused(link(RESPOND));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/transport-requests/" + number() + "/quotations/1/response");
        browser.checkAccessibility();
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("見積りへの回答 " + number() + " 見積 1");
        assertThat(page().locator("main")).containsText("詳細な経路と日程は経路設計者の承認後に確定します");
        assertThat(page().locator("main")).containsText("辞退や条件の相談は担当営業にご連絡ください");
        page().locator("body").focus();
        tabUntilFocused(button(PROCEED));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/transport-requests/" + number());
        browser.checkAccessibility();
    }

    @ならば("見積依頼の詳細に見積り {int} で詳細経路設計を依頼したことが表示され、回答の入口はない")
    public void 依頼したことが表示される(int quotationNo) {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(number() + " 見積 " + quotationNo + " で詳細経路設計を依頼しました");
        assertThat(quotationRegion()).containsText("見積 " + quotationNo + "（詳細経路設計を依頼済み）");
        assertThat(link(RESPOND)).hasCount(0);
    }

    /** 経路設計中への変更は DE-16 を受けた輸送要求の更新（非同期）を待つため、状態が変わるまで開き直す。 */
    @ならば("見積依頼の詳細の状態が {string} になる")
    public void 状態が変わる(String status) {
        for (int i = 0; i < 20 && !status.equals(definition("状態").textContent()); i++) {
            page().waitForTimeout(500);
            page().reload();
        }
        assertThat(definition("状態")).hasText(status);
        assertThat(page().locator("main")).containsText("経路設計者が詳細な経路を設計しています");
        browser.checkAccessibility();
    }

    /** 経路設計中の表は DE-16 を受けた輸送要求の状態の更新（非同期）を待つため、行が出るまで開き直す。 */
    @もし("営業担当者が受付一覧の経路設計中から提出した見積依頼の見積り {int} を開く")
    public void 経路設計中から開く(int quotationNo) {
        Locator row = page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("経路設計中の見積依頼（依頼時刻の古い順）"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(number() + " 見積 " + quotationNo));
        for (int i = 0; i < 20; i++) {
            browser.navigate(baseUrl + "/staff/transport-requests");
            if (row.count() > 0) {
                break;
            }
            page().waitForTimeout(500);
        }
        if (row.count() == 0) {
            throw new AssertionError("受付一覧の経路設計中に " + number() + " 見積 " + quotationNo + " が出ない（DE-16 の受け取りを待ちきれない）");
        }
        browser.checkAccessibility();
        row.click();
        page().waitForURL("**/staff/transport-requests/" + number() + "/quotations/" + quotationNo);
        browser.checkAccessibility();
    }

    @ならば("見積り {int} は {string} で読み取り専用と示され、提示と再見積りの操作はない")
    public void 詳細設計依頼済みが表示される(int quotationNo, String status) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("見積り " + number() + " 見積 " + quotationNo);
        assertThat(definition("状態")).hasText(status);
        assertThat(page().locator("main")).containsText("に詳細経路設計を依頼しました");
        assertThat(page().locator("main")).containsText("読み取り専用");
        assertThat(button("社内承認して提示する")).hasCount(0);
        assertThat(link("再見積りする")).hasCount(0);
    }

    @ならば("見積依頼の詳細に {string} と示され、回答の入口はない")
    public void 理由が示される(String reason) {
        page().waitForURL("**/customer/transport-requests/" + number());
        assertThat(page().getByRole(AriaRole.ALERT).first()).containsText(reason);
        assertThat(link(RESPOND)).hasCount(0);
        browser.checkAccessibility();
    }

    private Locator quotationRegion() {
        return page().getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName("見積り").setExact(true));
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
}

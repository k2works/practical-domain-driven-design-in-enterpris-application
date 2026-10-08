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
 * 荷主の見積りと経路の承認（C-17）と、承認の前後の見積依頼の詳細（C-04）の画面の層のステップ定義（US-24 AC4。Bolt 20）。
 * 見積依頼は、同じシナリオで荷主が提出したもの（{@link UiScenarioState}）とする。
 */
public class ShipperApprovalUiSteps {

    private static final String TO_APPROVAL = "見積りと経路を確かめて承認へ";
    private static final String APPROVE = "この見積りと経路で承認する";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public ShipperApprovalUiSteps(
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

    private String detailUrl() {
        return baseUrl + "/customer/transport-requests/" + state.transportRequestNumber();
    }

    private Locator link(String name) {
        return page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    /** 割当ては DE-05 を受けた経路設計の listener が見積りの公開 API を呼んで行う（非同期）ため、承認の入口が出るまで開き直す。 */
    @もし("荷主が見積依頼の詳細で確定した経路が届くのを待つ")
    public void 確定した経路が届くのを待つ() {
        browser.navigate(detailUrl());
        for (int i = 0; i < 20 && link(TO_APPROVAL).count() == 0; i++) {
            page().waitForTimeout(500);
            page().reload();
        }
        assertThat(link(TO_APPROVAL)).hasCount(1);
        assertThat(page().locator("main")).containsText("確定した経路（RC-");
        browser.checkAccessibility();
    }

    @もし("キー操作だけで見積りと経路の承認を開き、承認する")
    public void キー操作だけで承認する() {
        page().locator("body").focus();
        tabUntilFocused(link(TO_APPROVAL));
        page().keyboard().press("Enter");
        page().waitForURL("**/quotations/1/approval");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("見積りと経路の承認 " + state.transportRequestNumber() + " 見積 1");
        assertThat(page().locator("main")).containsText("この見積りと経路で承認しますか");
        assertThat(page().locator("main")).containsText("承認は本予約の確定ではありません");
        browser.checkAccessibility();
        page().locator("body").focus();
        tabUntilFocused(page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(APPROVE).setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/transport-requests/" + state.transportRequestNumber());
    }

    @もし("荷主が承認の画面で見積りと経路を確かめる")
    public void 承認を開く() {
        browser.navigate(detailUrl() + "/quotations/1/approval");
        assertThat(page().locator("main")).containsText("この見積りと経路で承認しますか");
        browser.checkAccessibility();
    }

    /** 予約待ちへの変更は DE-04 を受けた輸送要求の更新（非同期）を待つため、状態が変わるまで開き直す。 */
    @ならば("見積りと経路を承認したと示され、見積依頼の詳細の状態が {string} になる")
    public void 承認したと示される(String status) {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("の見積りと経路を承認しました");
        assertThat(link(TO_APPROVAL)).hasCount(0);
        browser.checkAccessibility();
        for (int i = 0; i < 20 && !status.equals(definition("状態").textContent()); i++) {
            page().waitForTimeout(500);
            page().reload();
        }
        assertThat(definition("状態")).hasText(status);
        assertThat(page().locator("main")).containsText("担当営業が本予約を確定します");
        browser.checkAccessibility();
    }

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

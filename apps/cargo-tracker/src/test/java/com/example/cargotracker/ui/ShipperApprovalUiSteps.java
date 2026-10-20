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
    private static final String REAPPROVAL_NOTICE = "承認いただいた経路のまま、料金と有効期限を出し直した見積りです。";

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
        キー操作だけで承認の画面を開いて承認する(1, false);
    }

    /** 再見積りした見積り（経路を引き継いだ荷主承認待ち）をもう一度承認する（D-85。Bolt 35d）。 */
    @もし("キー操作だけで見積り {int} の見積りと経路の承認を開き、もう一度承認する")
    public void キー操作だけでもう一度承認する(int quotationNo) {
        キー操作だけで承認の画面を開いて承認する(quotationNo, true);
    }

    private void キー操作だけで承認の画面を開いて承認する(int quotationNo, boolean reapproval) {
        page().locator("body").focus();
        browser.tabUntilFocused(link(TO_APPROVAL));
        page().keyboard().press("Enter");
        page().waitForURL("**/quotations/" + quotationNo + "/approval");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("見積りと経路の承認 " + state.transportRequestNumber() + " 見積 " + quotationNo);
        Locator reapprovalNotice = page().getByText(REAPPROVAL_NOTICE);
        assertThat(reapprovalNotice).hasCount(reapproval ? 1 : 0);
        assertThat(page().locator("main")).containsText("この見積りと経路で承認しますか");
        assertThat(page().locator("main")).containsText("承認は本予約の確定ではありません");
        browser.checkAccessibility();
        page().locator("body").focus();
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(APPROVE).setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/transport-requests/" + state.transportRequestNumber());
    }

    /** 承認の後に失効した見積りは、新しい見積りの依頼を求めず、担当営業が用意すると示す（D-85。Bolt 35d）。 */
    @ならば("荷主の見積依頼の詳細に、承認の後に見積り {int} の有効期限を過ぎ担当営業が新しい見積りを用意すると示される")
    public void 承認の後に失効したと示される(int quotationNo) {
        browser.navigate(detailUrl());
        Locator main = page().locator("main");
        assertThat(page().getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions().setLevel(3).setName("見積 " + quotationNo + "（失効）")))
                .hasCount(1);
        assertThat(main).containsText("承認の後に有効期限");
        assertThat(main).containsText("承認の後に見積りの有効期限を過ぎました。担当営業が新しい見積りを用意しています。");
        assertThat(main).containsText("を過ぎました。用意できたら、もう一度承認をお願いします。");
        assertThat(main).not().containsText("担当営業にご依頼ください");
        assertThat(main).not().containsText("本予約の扱いは担当営業からご連絡します");
        assertThat(main).not().containsText("詳細経路設計を依頼しました");
        assertThat(definition("状態")).hasText("見積り作成中（A 社の対応待ち）");
        browser.checkAccessibility();
    }

    /**
     * 再見積りした見積りを提示すると、輸送要求を荷主承認待ちに戻す DE-24（非同期）の受け取りを待たずに、一覧と詳細が最新の見積りから
     * もう一度の承認を求める（D-85。Bolt 35d）。
     */
    @ならば("荷主の見積依頼の一覧と詳細に、見積り {int} をもう一度承認するよう示される")
    public void もう一度承認するよう示される(int quotationNo) {
        browser.navigate(baseUrl + "/customer/transport-requests");
        Locator row = page().getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHas(link(state.transportRequestNumber())));
        assertThat(row).containsText("見積りと経路の承認待ち（お客様の対応待ち）");
        browser.checkAccessibility();
        browser.navigate(detailUrl());
        Locator main = page().locator("main");
        assertThat(definition("状態")).hasText("見積りと経路の承認待ち（お客様の対応待ち）");
        assertThat(main).containsText("料金と有効期限を出し直した見積りが届きました。もう一度承認してください。");
        assertThat(page().getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions().setLevel(3).setName("見積 " + quotationNo + "（見積りと経路の承認待ち）")))
                .hasCount(1);
        assertThat(main.getByText(REAPPROVAL_NOTICE)).hasCount(1);
        assertThat(main).not().containsText("詳細経路設計を依頼しました");
        assertThat(link(TO_APPROVAL)).hasCount(1);
        browser.checkAccessibility();
    }

    @もし("荷主が承認の画面で見積りと経路を確かめる")
    public void 承認を開く() {
        browser.navigate(detailUrl() + "/quotations/1/approval");
        assertThat(page().locator("main")).containsText("この見積りと経路で承認しますか");
        browser.checkAccessibility();
    }

    /**
     * 荷主の画面の状態は、輸送要求が荷主承認待ち・予約待ちなら最新の見積りから選ぶ（D-85。Bolt 35d）ので、DE-04 を受けた輸送要求の更新
     * （非同期）を待たずに予約待ちと出る。輸送要求の遷移は業務ルール層と統合テストで確かめる。
     */
    @ならば("見積りと経路を承認したと示され、見積依頼の詳細の状態が {string} になる")
    public void 承認したと示される(String status) {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("の見積りと経路を承認しました");
        assertThat(link(TO_APPROVAL)).hasCount(0);
        browser.checkAccessibility();
        assertThat(definition("状態")).hasText(status);
        assertThat(page().locator("main")).containsText("担当営業が本予約を確定します");
        browser.checkAccessibility();
    }
}

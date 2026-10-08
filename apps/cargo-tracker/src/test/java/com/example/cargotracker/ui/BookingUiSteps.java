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
 * 本予約の確定（S-09）・予約の詳細（S-24）と、受付一覧（S-02）の予約の確定待ちの表の画面の層のステップ定義（US-04 AC1・AC2。
 * Bolt 23b）。見積依頼は、同じシナリオで荷主が提出したもの（{@link UiScenarioState}）とする。
 */
public class BookingUiSteps {

    private static final String AWAITING_BOOKING = "予約の確定待ちの見積り（有効期限の近い順）";
    private static final String CONFIRM = "本予約を確定する";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public BookingUiSteps(
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

    private String subject(int quotationNo) {
        return state.transportRequestNumber() + " 見積 " + quotationNo;
    }

    private Locator awaitingBooking() {
        return page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName(AWAITING_BOOKING));
    }

    private Locator awaitingRow(int quotationNo) {
        return awaitingBooking().locator("tr", new Locator.LocatorOptions().setHasText(subject(quotationNo)));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    @もし("営業担当者が受付一覧の予約の確定待ちから提出した見積依頼の見積り {int} の本予約の確定を開く")
    public void 予約の確定待ちから開く(int quotationNo) {
        browser.navigate(baseUrl + "/staff/transport-requests");
        assertThat(awaitingRow(quotationNo)).hasCount(1);
        browser.checkAccessibility();
        awaitingRow(quotationNo)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(subject(quotationNo) + " の本予約の確定へ"))
                .click();
        page().waitForURL("**/staff/bookings/new?**");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("本予約の確定 " + subject(quotationNo));
        assertThat(page().locator("main")).containsText("この表示は開いた時刻での確認です");
        assertThat(page().locator("main")).containsText("本予約を確定しますか");
        browser.checkAccessibility();
    }

    @もし("キー操作だけで契約条件と照合したことを示して本予約を確定する")
    public void キー操作だけで確定する() {
        page().locator("body").focus();
        tabUntilFocused(page().getByLabel("契約条件と照合した（営業担当者の確認）"));
        page().keyboard().press("Space");
        assertThat(page().getByLabel("契約条件と照合した（営業担当者の確認）")).isChecked();
        tabUntilFocused(page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(CONFIRM).setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL(url -> !url.contains("/staff/bookings/new"));
    }

    @ならば("予約の詳細に本予約を確定したことと追跡番号が示され、追跡の開始が {string} である")
    public void 予約の詳細が示される(String trackingStart) {
        page().waitForURL("**/staff/bookings/CT*");
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(subject(1) + " で本予約を確定しました。追跡番号は CT");
        assertThat(definition("追跡番号")).hasText(java.util.regex.Pattern.compile("CT[A-HJKMNP-Z2-9]{12}"));
        assertThat(definition("業務番号")).hasText(state.transportRequestNumber());
        assertThat(definition("状態")).hasText("確定済み");
        assertThat(definition("追跡の開始")).hasText(trackingStart);
        browser.checkAccessibility();
    }

    /** 予約の確定待ちの表から行がなくなるのは DE-07 を受けた輸送要求の更新（非同期）の後なので、行がなくなるまで開き直す。 */
    @ならば("受付一覧の予約の確定待ちから提出した見積依頼の見積り {int} がなくなる")
    public void 予約の確定待ちからなくなる(int quotationNo) {
        for (int i = 0; i < 20; i++) {
            browser.navigate(baseUrl + "/staff/transport-requests");
            if (awaitingRow(quotationNo).count() == 0) {
                break;
            }
            page().waitForTimeout(500);
        }
        assertThat(awaitingRow(quotationNo)).hasCount(0);
        browser.checkAccessibility();
    }

    @ならば("受付一覧に {string} と示され、見積り {int} は {string} で本予約の確定の入口がない")
    public void 受付一覧に理由が示される(String message, int quotationNo, String status) {
        page().waitForURL("**/staff/transport-requests");
        assertThat(page().getByRole(AriaRole.ALERT)).containsText(subject(quotationNo) + " " + message);
        assertThat(awaitingRow(quotationNo)).containsText(status);
        assertThat(awaitingRow(quotationNo)
                        .getByRole(
                                AriaRole.LINK,
                                new Locator.GetByRoleOptions().setName(subject(quotationNo) + " の本予約の確定へ")))
                .hasCount(0);
        assertThat(awaitingRow(quotationNo)
                        .getByRole(
                                AriaRole.LINK,
                                new Locator.GetByRoleOptions().setName(subject(quotationNo) + " の再見積りへ")))
                .hasCount(1);
        browser.checkAccessibility();
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

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
 * 荷主担当者の C-06 予約一覧の画面の層のステップ定義（Bolt 27b）。予約は、同じシナリオで営業担当者が確定した予約（追跡番号と業務番号は
 * {@link UiScenarioState}）とする。「一覧の先頭」は、シナリオを逐次に実行し、確定時刻が実時間で増えることを前提にしている（S-10 と同じ）。
 */
public class CustomerBookingUiSteps {

    private static final String LIST_PATH = "/customer/bookings";
    private static final String LIST_CAPTION = "自社の確定した予約（確定時刻の新しい順）";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public CustomerBookingUiSteps(
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

    private Locator firstRow() {
        return page().getByRole(AriaRole.TABLE, new Page.GetByRoleOptions().setName(LIST_CAPTION))
                .locator("tbody tr")
                .first();
    }

    /** 荷主担当者でログインし直してから、荷主のナビの「予約」の行き先を開く。 */
    @もし("荷主担当者が予約一覧を開く")
    public void 荷主担当者が予約一覧を開く() {
        browser.navigate(baseUrl + LIST_PATH);
        page().waitForURL("**" + LIST_PATH);
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("予約一覧")))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("荷主の予約一覧の先頭に確定した予約が見積り {int} と追跡 {string} とともに示される")
    public void 荷主の予約一覧の先頭に示される(int quotationNo, String tracking) {
        Locator row = firstRow();
        assertThat(row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())))
                .isVisible();
        assertThat(row.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.transportRequestNumber())))
                .isVisible();
        assertThat(row.locator("td").nth(1)).containsText("見積 " + quotationNo);
        assertThat(row.locator("td").nth(3)).hasText(tracking);
        browser.checkAccessibility();
    }

    @もし("キー操作だけで荷主の予約一覧の先頭の追跡番号を開く")
    public void 先頭の追跡番号を開く() {
        page().locator("body").focus();
        tabUntilFocused(
                firstRow().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/tracking-records/" + state.trackingNumber());
    }

    @ならば("確定した予約の追跡の照会の結果が示される")
    public void 追跡の照会の結果が示される() {
        assertThat(page().getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName("追跡の照会 " + state.trackingNumber())))
                .isVisible();
        browser.checkAccessibility();
    }

    private void tabUntilFocused(Locator target) {
        for (int i = 0; i < 80; i++) {
            page().keyboard().press("Tab");
            if ((Boolean) target.evaluate("element => element === document.activeElement")) {
                return;
            }
        }
        throw new AssertionError("Tab で目的の要素に移れなかった");
    }
}

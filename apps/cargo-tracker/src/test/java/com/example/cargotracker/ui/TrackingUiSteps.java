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
 * 追跡管理者の S-11 追跡一覧・S-12 追跡の詳細の画面の層のステップ定義（Bolt 26。US-12 の前提）。追跡記録は、同じシナリオで営業担当者が
 * 確定した予約の追跡番号（{@link UiScenarioState}）のものとする。「一覧の先頭」は、シナリオを逐次に実行し、追跡の開始時刻が実時間で
 * 増えることを前提にしている（Bolt 25b の予約一覧と同じ）。
 */
public class TrackingUiSteps {

    private static final String LIST_CAPTION = "追跡記録（追跡の開始時刻の新しい順）";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public TrackingUiSteps(
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

    private Locator trackingList() {
        return page().getByRole(AriaRole.TABLE, new Page.GetByRoleOptions().setName(LIST_CAPTION));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    /** 追跡管理者でログインし直してから、ホーム（{@code /}）を開く。ホームは追跡管理者を S-11 へ移す。 */
    @もし("追跡管理者がホームを開く")
    public void 追跡管理者がホームを開く() {
        browser.navigate(baseUrl + "/staff/tracking-records");
        page().navigate(baseUrl + "/");
        page().waitForURL("**/staff/tracking-records");
    }

    @ならば("追跡一覧の先頭に確定した予約の追跡番号が現在状態 {string} とともに示される")
    public void 追跡一覧の先頭に示される(String status) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡一覧")))
                .isVisible();
        Locator firstRow = trackingList().locator("tbody tr").first();
        assertThat(firstRow.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())))
                .isVisible();
        assertThat(firstRow.locator("td").nth(1)).hasText(status);
        browser.checkAccessibility();
    }

    @もし("キー操作だけで追跡一覧の先頭の追跡の詳細を開く")
    public void 追跡一覧から追跡の詳細を開く() {
        page().locator("body").focus();
        tabUntilFocused(trackingList()
                .locator("tbody tr")
                .first()
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/tracking-records/" + state.trackingNumber());
    }

    @ならば("追跡の詳細に現在状態 {string} と予定区間と主要実績がまだないことが示される")
    public void 追跡の詳細が示される(String status) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡の詳細")))
                .isVisible();
        assertThat(definition("追跡番号")).hasText(state.trackingNumber());
        assertThat(definition("現在状態")).hasText(status);
        Locator legs = page().getByRole(AriaRole.LIST, new Page.GetByRoleOptions().setName("予定区間"));
        assertThat(legs.getByRole(AriaRole.LISTITEM).first()).containsText("区間 1");
        assertThat(page().getByText("主要実績はまだありません。")).isVisible();
        browser.checkAccessibility();
    }

    @もし("キー操作だけで追跡の詳細から追跡一覧へ戻る")
    public void 追跡の詳細から追跡一覧へ戻る() {
        page().locator("body").focus();
        tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("追跡一覧").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/tracking-records");
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

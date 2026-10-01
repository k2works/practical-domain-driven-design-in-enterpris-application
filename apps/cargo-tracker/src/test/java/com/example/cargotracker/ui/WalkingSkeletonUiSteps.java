package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * ウォーキングスケルトンの画面の層のステップ定義。利用者と同じく、画面の文言とキー操作で操作する。
 */
public class WalkingSkeletonUiSteps {

    private static final double KPI_LIST_TIMEOUT_MILLIS = 10_000;

    private final BrowserSession browser;
    private final int port;
    private String transportRequestId;

    public WalkingSkeletonUiSteps(BrowserSession browser, @LocalServerPort int port) {
        this.browser = browser;
        this.port = port;
    }

    private Page page() {
        return browser.page();
    }

    private void open(String path) {
        page().navigate("http://localhost:" + port + path);
        browser.checkAccessibility();
    }

    @前提("荷主が見積依頼の作成画面を開いている")
    @もし("荷主が見積依頼の作成画面を開く")
    public void 荷主が見積依頼の作成画面を開く() {
        open("/customer/transport-requests/new");
    }

    @もし("出発地 {string}、目的地 {string} をキー操作だけで入力して提出する")
    public void キー操作だけで入力して提出する(String origin, String destination) {
        page().keyboard().press("Tab");
        page().keyboard().type(origin);
        page().keyboard().press("Tab");
        page().keyboard().type(destination);
        page().keyboard().press("Tab");
        page().keyboard().press("Enter");
        page().waitForURL("**/submitted");
        browser.checkAccessibility();
    }

    @ならば("完了画面に見積依頼（輸送要求）ID と状態 {string} が表示される")
    public void 完了画面に表示される(String status) {
        assertThat(page().getByText("見積依頼（輸送要求）ID")).isVisible();
        assertThat(page().getByText(status, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        transportRequestId = page().locator("dd").first().textContent().strip();
    }

    @かつ("社内の KPI 計測記録の一覧にその輸送要求が表示される")
    public void KPI計測記録の一覧に表示される() {
        open("/staff/kpi-observations");
        // DE-01 の購読は非同期のため、一覧に出るまで読み直す
        page().waitForCondition(
                        () -> {
                            if (page().getByText(transportRequestId).count() > 0) {
                                return true;
                            }
                            page().reload();
                            return false;
                        },
                        new Page.WaitForConditionOptions().setTimeout(KPI_LIST_TIMEOUT_MILLIS));
        assertThat(page().getByText(transportRequestId))
                .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(KPI_LIST_TIMEOUT_MILLIS));
        browser.checkAccessibility();
    }

    @かつ("表示したすべての画面にアクセシビリティの違反がない")
    public void アクセシビリティの違反がない() {
        assertThat(browser.accessibilityViolations()).isEmpty();
    }

    @前提("画面の幅が {int} CSS px である")
    public void 画面の幅を設定する(int width) {
        page().setViewportSize(width, 800);
    }

    @ならば("横スクロールが出ない")
    public void 横スクロールが出ない() {
        Object overflow =
                page().evaluate("() => document.documentElement.scrollWidth > document.documentElement.clientWidth");
        assertThat(overflow).isEqualTo(false);
    }
}

package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Page;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * ウォーキングスケルトンの画面の層のステップ定義。利用者と同じく、画面の文言とキー操作で操作する。
 * {@code ui.base-url} を与えるとその URL（例: ステージング）を操作し、なければ起動したアプリを操作する。
 */
public class WalkingSkeletonUiSteps {

    private static final Duration KPI_LIST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration KPI_LIST_POLL_INTERVAL = Duration.ofMillis(500);
    private static final int VIEWPORT_HEIGHT = 800;

    private final BrowserSession browser;
    private final String baseUrl;
    private String transportRequestId;

    public WalkingSkeletonUiSteps(
            BrowserSession browser, @LocalServerPort int port, @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private void open(String path) {
        page().navigate(baseUrl + path);
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
        assertThat(page().getByLabel("出発地（UN/LOCODE）")).isFocused();
        page().keyboard().type(origin);
        page().keyboard().press("Tab");
        assertThat(page().getByLabel("目的地（UN/LOCODE）")).isFocused();
        page().keyboard().type(destination);
        page().keyboard().press("Tab");
        assertThat(page().getByRole(
                                com.microsoft.playwright.options.AriaRole.BUTTON,
                                new Page.GetByRoleOptions().setName("提出する")))
                .isFocused();
        page().keyboard().press("Enter");
        page().waitForURL("**/submitted");
        browser.checkAccessibility();
    }

    @ならば("完了画面に見積依頼（輸送要求）ID と状態 {string} が表示される")
    public void 完了画面に表示される(String status) {
        assertThat(page().getByText(status, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        transportRequestId =
                page().locator("dt:text-is('見積依頼（輸送要求）ID') + dd").textContent().strip();
        Assertions.assertThat(transportRequestId).isNotBlank();
    }

    @かつ("社内の KPI 計測記録の一覧にその輸送要求が表示される")
    public void KPI計測記録の一覧に表示される() {
        open("/staff/kpi-observations");
        // DE-01 の購読は非同期のため、一覧に出るまで間隔を置いて読み直す。
        // Playwright とシナリオの範囲の部品はスレッドに結び付くため、同じスレッドで評価する
        await().atMost(KPI_LIST_TIMEOUT)
                .pollInterval(KPI_LIST_POLL_INTERVAL)
                .pollInSameThread()
                .until(() -> {
                    if (page().getByText(transportRequestId).count() > 0) {
                        return true;
                    }
                    page().reload();
                    return false;
                });
        browser.checkAccessibility();
    }

    @かつ("表示したすべての画面にアクセシビリティの違反がない")
    public void アクセシビリティの違反がない() {
        Assertions.assertThat(browser.accessibilityViolations()).isEmpty();
    }

    @前提("画面の幅が {int} CSS px である")
    public void 画面の幅を設定する(int width) {
        page().setViewportSize(width, VIEWPORT_HEIGHT);
    }

    @ならば("横スクロールが出ない")
    public void 横スクロールが出ない() {
        Object overflow =
                page().evaluate("() => document.documentElement.scrollWidth > document.documentElement.clientWidth");
        Assertions.assertThat(overflow).isEqualTo(false);
    }
}

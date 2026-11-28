package com.example.cargotracker.ui;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import io.cucumber.java.ja.もし;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * ユーザーマニュアルの画面のキャプチャ（{@code @manual}。{@code ./gradlew manualScreenshots}）のステップ定義。画面を役割と URL で開き、
 * {@code docs/manual/assets/<名前>.png} に撮る。撮影の場所（システムプロパティ）がないとき（ほかのタスクで動いたとき）は撮らない。
 * URL の {@code {業務番号}}・{@code {追跡番号}} は、同じシナリオで作った見積依頼と予約の値に置き換える。
 */
public class ManualScreenshotSteps {

    static final String SCREENSHOT_DIR_PROPERTY = "cargotracker.manual.screenshot-dir";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public ManualScreenshotSteps(
            BrowserSession browser,
            UiScenarioState state,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.state = state;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    /** 画面を開く。荷主の画面は荷主担当者、社内の画面はその画面の役割でログインしてから開く（{@link BrowserSession#navigate}）。 */
    @もし("画面 {string} を開く")
    public void 画面を開く(String path) {
        browser.navigate(baseUrl + resolve(path));
        browser.page().waitForLoadState(LoadState.NETWORKIDLE);
    }

    /** いまの利用者のまま画面を開く（役割にない画面の「権限がありません」を撮るため）。 */
    @もし("ログインしたまま画面 {string} を開く")
    public void ログインしたまま画面を開く(String path) {
        browser.page().navigate(baseUrl + resolve(path));
        browser.page().waitForLoadState(LoadState.NETWORKIDLE);
    }

    @もし("画面を {string} として撮る")
    public void 画面を撮る(String name) {
        String dir = System.getProperty(SCREENSHOT_DIR_PROPERTY, "");
        if (dir.isBlank()) {
            return;
        }
        Page page = browser.page();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Path.of(dir, name + ".png"))
                .setFullPage(true)
                .setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED));
    }

    private String resolve(String path) {
        String resolved = path;
        if (resolved.contains("{業務番号}")) {
            resolved = resolved.replace("{業務番号}", state.transportRequestNumber());
        }
        if (resolved.contains("{追跡番号}")) {
            resolved = resolved.replace("{追跡番号}", state.trackingNumber());
        }
        return resolved;
    }
}

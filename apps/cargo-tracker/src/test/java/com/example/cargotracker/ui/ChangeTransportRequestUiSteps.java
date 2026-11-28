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
 * 本予約の前の輸送条件の変更（US-05 AC1。Bolt 62-1）の画面の層のステップ。C-04 から C-03 の変更の画面を開き、送る前の案内を確かめる。
 */
public class ChangeTransportRequestUiSteps {

    private static final String CHANGE_LINK = "条件を変更する";
    private static final String SEND = "条件を変更して送る";
    private static final String DESTINATION = "目的地（UN/LOCODE）";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public ChangeTransportRequestUiSteps(
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

    private Locator button(String name) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    private Locator link(String name) {
        return page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name));
    }

    @もし("条件の変更を開き、見積りが使えなくなる案内を確かめて、キー操作だけで目的地を {string} に変更して送る")
    public void キー操作だけで条件を変更する(String destination) {
        page().locator("body").focus();
        browser.tabUntilFocused(link(CHANGE_LINK));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/transport-requests/" + number() + "/edit");
        browser.checkAccessibility();
        // 送る前の確認の領域（BR-13）
        assertThat(page().locator("#version-notice"))
                .containsText("送ると版 2 になり、担当者の審査からやり直します。これまでの見積りと経路の承認は使えなくなります。");
        Locator field = page().getByLabel(DESTINATION, new Page.GetByLabelOptions().setExact(true));
        page().locator("body").focus();
        browser.tabUntilFocused(field);
        page().keyboard().press("ControlOrMeta+A");
        page().keyboard().type(destination);
        // 段階入力（Bolt 8）: 見えている「次へ」を Enter で押して確認の段階まで進む
        for (int i = 0; i < 3; i++) {
            browser.tabUntilFocused(button("次へ"));
            page().keyboard().press("Enter");
        }
        browser.tabUntilFocused(button(SEND));
        page().keyboard().press("Enter");
        page().waitForURL(SubmitTransportRequestUiSteps.DETAIL_URL);
        browser.checkAccessibility();
    }

    @ならば("見積依頼の詳細に見積り 1 は輸送条件を変更したため使えないと表示され、回答の入口はない")
    public void 見積りは使えないと表示される() {
        assertThat(page().locator("main")).containsText("見積 1（条件の変更で無効）");
        assertThat(page().locator("main")).containsText("輸送条件を変更したため、この見積りは使えません（読み取り専用）。");
        assertThat(link("見積りに回答する")).hasCount(0);
    }

    @ならば("社内の見積り 1 は条件の変更で無効と示され、提示と再見積りの操作はない")
    public void 社内の見積りは無効と示される() {
        assertThat(page().locator("main")).containsText("条件の変更で無効");
        assertThat(page().locator("main")).containsText("荷主が輸送条件を変更したため、この見積りは無効です（読み取り専用）。");
        assertThat(link("再見積りする")).hasCount(0);
        assertThat(button("社内承認して提示する")).hasCount(0);
    }
}

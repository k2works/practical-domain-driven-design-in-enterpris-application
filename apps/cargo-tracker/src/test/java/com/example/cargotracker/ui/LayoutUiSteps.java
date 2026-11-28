package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;

/**
 * 共通レイアウト（ナビ、窓口の案内）の画面の層のステップ定義。ナビはランドマーク（nav）の名前で取る。
 */
public class LayoutUiSteps {

    private static final String MENU_BUTTON = "メニュー";

    private final BrowserSession browser;

    public LayoutUiSteps(BrowserSession browser) {
        this.browser = browser;
    }

    private Page page() {
        return browser.page();
    }

    private Locator nav(String name) {
        return page().getByRole(
                        AriaRole.NAVIGATION,
                        new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator navLink(String navName, String item) {
        return nav(navName)
                .getByRole(
                        AriaRole.LINK,
                        new Locator.GetByRoleOptions().setName(item).setExact(true));
    }

    @ならば("{string} のナビに {string} が現在の項目として表示される")
    public void 現在の項目として表示される(String navName, String item) {
        assertThat(navLink(navName, item)).isVisible();
        assertThat(navLink(navName, item)).hasAttribute("aria-current", "page");
    }

    @ならば("{string} のナビに {string} が表示される")
    public void ナビに表示される(String navName, String item) {
        assertThat(navLink(navName, item)).isVisible();
    }

    @ならば("窓口の案内が受付時間とタイムゾーンとともに表示される")
    public void 窓口の案内が表示される() {
        Locator footer = page().getByRole(AriaRole.CONTENTINFO);
        assertThat(footer).containsText("平日 09:00〜18:00");
        assertThat(footer).containsText("Asia/Tokyo");
    }

    @ならば("窓口の案内は表示されない")
    public void 窓口の案内は表示されない() {
        assertThat(page().getByText("お困りのときは")).hasCount(0);
    }

    @もし("キー操作だけで {string} のナビの {string} を選ぶ")
    public void キー操作だけでナビを選ぶ(String navName, String item) {
        Locator target = navLink(navName, item);
        page().locator("body").focus();
        for (int i = 0; i < 20 && !(Boolean) target.evaluate("element => element === document.activeElement"); i++) {
            page().keyboard().press("Tab");
        }
        assertThat(target).isFocused();
        String href = target.getAttribute("href");
        page().keyboard().press("Enter");
        // 画面が移り終わってから検査する（移る途中で検査しない）
        page().waitForURL("**" + href);
        browser.checkAccessibility();
    }

    @ならば("ナビがメニューのボタンに畳まれている")
    public void ナビが畳まれている() {
        Locator button = page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MENU_BUTTON));
        assertThat(button).isVisible();
        assertThat(button).hasAttribute("aria-expanded", "false");
    }

    @もし("メニューのボタンを押す")
    public void メニューのボタンを押す() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MENU_BUTTON))
                .click();
        browser.checkAccessibility();
    }
}

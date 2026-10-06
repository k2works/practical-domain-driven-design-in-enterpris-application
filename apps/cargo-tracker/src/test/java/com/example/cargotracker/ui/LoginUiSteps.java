package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.shared.domain.Role;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * ログイン（A-01）・ログアウト・権限なし（A-04）・準備中の画面の画面の層のステップ（US-18 の password の段、Bolt 14）。
 */
public class LoginUiSteps {

    private final BrowserSession browser;
    private final UiUsers users;
    private final String baseUrl;
    private UiUsers.Credentials credentials;

    public LoginUiSteps(
            BrowserSession browser,
            UiUsers users,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private Locator header() {
        return page().getByRole(AriaRole.BANNER);
    }

    @前提("荷主担当者の利用者が登録されている")
    public void 荷主担当者の利用者が登録されている() {
        credentials = users.of(Role.SHIPPER);
    }

    @前提("営業担当者の利用者が登録されている")
    public void 営業担当者の利用者が登録されている() {
        credentials = users.of(Role.SALES);
    }

    @もし("ログインの画面でメールアドレスと password を入れてログインする")
    public void ログインする() {
        submitLogin(credentials.password());
    }

    @もし("ログインの画面でメールアドレスと誤った password を入れてログインする")
    public void 誤ったpasswordでログインする() {
        submitLogin("wrong-" + credentials.password());
    }

    private void submitLogin(String password) {
        page().navigate(baseUrl + "/login");
        browser.checkAccessibility();
        page().getByLabel("メールアドレス").fill(credentials.email());
        page().getByLabel("password").fill(password);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @もし("ログインの画面でキー操作だけでメールアドレスと password を入れてログインする")
    public void キー操作だけでログインする() {
        page().navigate(baseUrl + "/login");
        page().getByLabel("メールアドレス").focus();
        page().keyboard().type(credentials.email());
        page().keyboard().press("Tab");
        page().keyboard().type(credentials.password());
        page().keyboard().press("Tab");
        page().keyboard().press("Enter");
        page().waitForURL(url -> !url.contains("/login"));
        browser.checkAccessibility();
    }

    @ならば("ヘッダーに {string} と {string} とログアウトが表示される")
    public void ヘッダーに表示される(String first, String second) {
        assertThat(header()).containsText(first);
        assertThat(header()).containsText(second);
        assertThat(header().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("ログアウト")))
                .isVisible();
    }

    @もし("社内の受付一覧の URL を直接開く")
    public void 社内の受付一覧のURLを直接開く() {
        page().navigate(baseUrl + "/staff/transport-requests");
        browser.checkAccessibility();
    }

    @もし("荷主の見積依頼の一覧の URL を直接開く")
    public void 荷主の見積依頼の一覧のURLを直接開く() {
        page().navigate(baseUrl + "/customer/transport-requests");
        browser.checkAccessibility();
    }

    @もし("ログインせずに見積依頼の作成画面の URL を開く")
    public void ログインせずに見積依頼の作成画面のURLを開く() {
        page().navigate(baseUrl + "/customer/transport-requests/new");
        browser.checkAccessibility();
    }

    @ならば("権限がないと表示される")
    public void 権限がないと表示される() {
        assertThat(page().getByText("この画面を表示する権限がありません。")).isVisible();
        assertThat(page().getByText("荷主の方は担当営業に、社内の方はシステム管理者にお問い合わせください。")).isVisible();
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ホームへ戻る")))
                .isVisible();
    }

    @ならば("{string} と表示される")
    public void と表示される(String text) {
        assertThat(page().getByText(text)).isVisible();
    }

    @かつ("入れたメールアドレスが残り password は空になっている")
    public void 入れたメールアドレスが残りpasswordは空になっている() {
        assertThat(page().getByLabel("メールアドレス")).hasValue(credentials.email());
        assertThat(page().getByLabel("password")).hasValue("");
    }

    @もし("ヘッダーのログアウトを押す")
    public void ヘッダーのログアウトを押す() {
        header().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("ログアウト"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @かつ("{string} のナビの {string} を選ぶ")
    public void ナビの項目を選ぶ(String nav, String item) {
        page().getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName(nav))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(item))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }
}

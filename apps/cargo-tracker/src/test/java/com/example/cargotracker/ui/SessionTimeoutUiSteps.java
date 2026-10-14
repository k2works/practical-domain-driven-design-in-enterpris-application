package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.identity.SpringSessionRows;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * session の警告と延長（Bolt 32b、ACC-06、横断の受入条件）の画面の層のステップ定義。ブラウザの時計だけを進め、サーバーの session は
 * 実時間のままにする。8 時間の上限は、サーバーの時計を進められないので session の属性（ログインの時刻）を前にずらして作る。
 */
public class SessionTimeoutUiSteps {

    /** ログインの時刻を置く session の属性の名前（認証の入口の {@code SessionLifetime.AUTHENTICATED_AT} と同じ値）。 */
    private static final String AUTHENTICATED_AT = "cargotracker.session.authenticatedAt";

    private static final String NEW_TRANSPORT_REQUEST = "/customer/transport-requests/new";

    private final BrowserSession browser;
    private final JdbcTemplate jdbc;
    private final String baseUrl;

    public SessionTimeoutUiSteps(
            BrowserSession browser,
            JdbcTemplate jdbc,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.jdbc = jdbc;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private Locator warning() {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName("まもなくログアウトします"));
    }

    private Locator warningButton(String name) {
        return warning().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name));
    }

    private Locator origin() {
        return page().getByLabel("出発地（UN/LOCODE）");
    }

    @前提("ブラウザの時計を差し替える")
    public void ブラウザの時計を差し替える() {
        browser.installClock();
    }

    @前提("見積依頼の作成画面で出発地に {string} を入れる")
    public void 見積依頼の作成画面で出発地に_を入れる(String value) {
        browser.navigate(baseUrl + NEW_TRANSPORT_REQUEST);
        origin().fill(value);
    }

    @もし("操作しないまま {int} 分たつ")
    public void 操作しないまま_分たつ(int minutes) {
        browser.fastForward(Duration.ofMinutes(minutes));
    }

    @もし("警告の {string} を押す")
    public void 警告の_を押す(String name) {
        warningButton(name).click();
    }

    @もし("ログインから 7 時間 56 分たった session で見積依頼の作成画面を開き直す")
    public void ログインから7時間56分たったsessionで見積依頼の作成画面を開き直す() {
        new SpringSessionRows(jdbc)
                .putAttribute(
                        browser.sessionCookieValue(),
                        AUTHENTICATED_AT,
                        Instant.now().minus(Duration.ofHours(7).plusMinutes(56)));
        page().navigate(baseUrl + NEW_TRANSPORT_REQUEST);
    }

    @もし("同じブラウザの別のタブで見積依頼の一覧を開く")
    public void 同じブラウザの別のタブで見積依頼の一覧を開く() {
        browser.openTab(baseUrl + "/customer/transport-requests");
    }

    @ならば("操作がないためにログアウトする警告が出て {string} にフォーカスがある")
    public void 操作がないためにログアウトする警告が出て_にフォーカスがある(String name) {
        assertThat(warning()).isVisible();
        assertThat(warning()).containsText("操作がないため、あと 5 分でログアウトします。");
        assertThat(warningButton(name)).isFocused();
        browser.checkAccessibility();
    }

    @ならば("ログインから 8 時間がたつ警告が出て延長のボタンはなく {string} にフォーカスがある")
    public void ログインから8時間がたつ警告が出て延長のボタンはなく_にフォーカスがある(String name) {
        assertThat(warning()).isVisible();
        assertThat(warning()).containsText("ログインから 8 時間がたつため");
        assertThat(warning()).containsText("延長はできません。");
        assertThat(warningButton("ログインを延長する")).isHidden();
        assertThat(warningButton(name)).isFocused();
        browser.checkAccessibility();
    }

    @ならば("警告が閉じる")
    public void 警告が閉じる() {
        assertThat(warning()).isHidden();
    }

    @ならば("警告は出ていない")
    public void 警告は出ていない() {
        assertThat(warning()).isHidden();
    }

    @かつ("出発地に {string} が残っている")
    public void 出発地に_が残っている(String value) {
        assertThat(origin()).hasValue(value);
    }

    @ならば("警告が閉じ {string} と {string} が示される")
    public void 警告が閉じ_と_が示される(String message, String link) {
        assertThat(warning()).isHidden();
        Locator notice = page().getByRole(AriaRole.ALERT).filter(new Locator.FilterOptions().setHasText(message));
        assertThat(notice).isVisible();
        assertThat(notice.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(link)))
                .hasAttribute("href", "/session-expired");
        browser.checkAccessibility();
    }
}

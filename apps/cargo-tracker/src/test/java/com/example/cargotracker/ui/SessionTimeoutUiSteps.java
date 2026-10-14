package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.identity.SpringSessionRows;
import com.example.cargotracker.shared.domain.Role;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
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

    @もし("ログインから {int} 時間 {int} 分たった session で見積依頼の作成画面を開き直す")
    public void ログインから_時間_分たったsessionで見積依頼の作成画面を開き直す(int hours, int minutes) {
        new SpringSessionRows(jdbc)
                .putAttribute(
                        browser.sessionCookieValue(),
                        AUTHENTICATED_AT,
                        Instant.now().minus(Duration.ofHours(hours).plusMinutes(minutes)));
        page().navigate(baseUrl + NEW_TRANSPORT_REQUEST);
    }

    @もし("Esc キーを押す")
    public void Escキーを押す() {
        page().keyboard().press("Escape");
    }

    @もし("延長の送信がサーバーの誤りで失敗する状態で警告の {string} を押す")
    public void 延長の送信がサーバーの誤りで失敗する状態で警告の_を押す(String name) {
        page().route("**/session/extend", route -> route.fulfill(new Route.FulfillOptions().setStatus(500)));
        warningButton(name).click();
    }

    @もし("サーバーの session が無操作で切れた後に警告の {string} を押す")
    public void サーバーのsessionが無操作で切れた後に警告の_を押す(String name) {
        new SpringSessionRows(jdbc).idle(browser.sessionCookieValue(), Duration.ofMinutes(31));
        warningButton(name).click();
    }

    @もし("サーバーの session が無操作で切れる")
    public void サーバーのsessionが無操作で切れる() {
        new SpringSessionRows(jdbc).idle(browser.sessionCookieValue(), Duration.ofMinutes(31));
    }

    @もし("案内の {string} を押す")
    public void 案内の_を押す(String name) {
        page().getByRole(AriaRole.ALERT)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name))
                .click();
    }

    @もし("見積依頼の作成画面を開き直す")
    public void 見積依頼の作成画面を開き直す() {
        page().navigate(baseUrl + NEW_TRANSPORT_REQUEST);
    }

    @もし("再認証の案内からもう一度ログインする")
    public void 再認証の案内からもう一度ログインする() {
        // 失効の扱いが作った新しい session に覚えた要求があるので、Cookie を消さずにログインする（signInAs は Cookie を消す）
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ログインへ"))
                .click();
        browser.signInHere(Role.SHIPPER);
    }

    @ならば("ログインの画面が表示される")
    public void ログインの画面が表示される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("ログイン")))
                .isVisible();
    }

    @もし("ログインの画面からもう一度ログインする")
    public void ログインの画面からもう一度ログインする() {
        browser.signInHere(Role.SHIPPER);
    }

    @ならば("再認証の案内が表示される")
    public void 再認証の案内が表示される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("もう一度ログインしてください")))
                .isVisible();
    }

    @ならば("ログインしたままである")
    public void ログインしたままである() {
        assertThat(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログアウト")))
                .isVisible();
    }

    @もし("同じブラウザの別のタブで見積依頼の一覧を開く")
    public void 同じブラウザの別のタブで見積依頼の一覧を開く() {
        browser.openTab(baseUrl + "/customer/transport-requests");
    }

    @ならば("操作がないためにログアウトする警告が出て {string} にフォーカスがある")
    public void 操作がないためにログアウトする警告が出て_にフォーカスがある(String name) {
        assertThat(warning()).isVisible();
        assertThat(warning()).containsText("画面の移動や送信がないため、あと 5 分でログアウトします。");
        assertThat(warningButton(name)).isFocused();
        browser.checkAccessibility();
    }

    @ならば("ログインから 8 時間になる警告が出て延長のボタンはなく {string} にフォーカスがある")
    public void ログインから8時間になる警告が出て延長のボタンはなく_にフォーカスがある(String name) {
        assertThat(warning()).isVisible();
        assertThat(warning()).containsText("ログインから 8 時間になるため");
        assertThat(warning()).containsText("延長はできません。");
        assertThat(warningButton("ログインを延長する")).isHidden();
        assertThat(warningButton(name)).isFocused();
        browser.checkAccessibility();
    }

    @ならば("警告が閉じる")
    public void 警告が閉じる() {
        assertThat(warning()).isHidden();
    }

    @ならば("警告が閉じ出発地にフォーカスが戻る")
    public void 警告が閉じ出発地にフォーカスが戻る() {
        assertThat(warning()).isHidden();
        assertThat(origin()).isFocused();
    }

    @ならば("警告の中に {string} と示され {string} が残る")
    public void 警告の中に_と示され_が残る(String message, String name) {
        assertThat(warning()).isVisible();
        assertThat(warning().getByRole(AriaRole.ALERT)).containsText(message);
        assertThat(warningButton(name)).isVisible();
        assertThat(warningButton(name)).isEnabled();
        browser.checkAccessibility();
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
                .hasAttribute("href", NEW_TRANSPORT_REQUEST);
        browser.checkAccessibility();
    }
}

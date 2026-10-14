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

    // --- 入力の控え（Bolt 32f） ---

    private static final String SAVED_INPUT = "cargotracker.session.savedInput";

    private Locator status() {
        return page().locator("#session-input-status");
    }

    private Object savedInput() {
        return page().evaluate("key => window.sessionStorage.getItem(key)", SAVED_INPUT);
    }

    @もし("見積依頼の作成画面のフォームを送信する")
    public void 見積依頼の作成画面のフォームを送信する() {
        // 段階入力では提出のボタンが最後の段階にしか出ないので、フォームの送信を直接起こす（submit のイベントは出る）
        page().locator("main form[data-stepwise]").evaluate("form => form.requestSubmit()");
    }

    @もし("CSRF のトークンが合わない状態で見積依頼の作成画面のフォームを送信する")
    public void CSRFのトークンが合わない状態で見積依頼の作成画面のフォームを送信する() {
        page().locator("main form[data-stepwise] input[name='_csrf']")
                .evaluate("input => { input.value = 'invalid'; }");
        見積依頼の作成画面のフォームを送信する();
    }

    @もし("見積依頼の一覧を開く")
    public void 見積依頼の一覧を開く() {
        page().navigate(baseUrl + "/customer/transport-requests");
    }

    @もし("知らせの {string} を押す")
    public void 知らせの_を押す(String name) {
        status().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name))
                .or(status().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name)))
                .click();
    }

    @もし("社内の見積依頼の一覧を開く")
    public void 社内の見積依頼の一覧を開く() {
        page().navigate(baseUrl + "/staff/transport-requests");
    }

    @もし("再認証の案内から営業担当者でもう一度ログインする")
    public void 再認証の案内から営業担当者でもう一度ログインする() {
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ログインへ"))
                .click();
        browser.signInHere(Role.SALES);
    }

    @もし("入力を控えてから {int} 時間 {int} 分たったことにする")
    public void 入力を控えてから_時間_分たったことにする(int hours, int minutes) {
        long elapsed = Duration.ofHours(hours).plusMinutes(minutes).toMillis();
        page().evaluate(
                        "([key, elapsed]) => { const saved = JSON.parse(window.sessionStorage.getItem(key)); "
                                + "saved.savedAt = Date.now() - elapsed; window.sessionStorage.setItem(key, JSON.stringify(saved)); }",
                        java.util.List.of(SAVED_INPUT, (double) elapsed));
    }

    @もし("商業送り状のファイルを選ぶ")
    public void 商業送り状のファイルを選ぶ() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("invoice", ".pdf");
        java.nio.file.Files.writeString(file, "%PDF-1.4");
        page().locator("#commercialInvoice").setInputFiles(file);
    }

    @ならば("再認証の案内が表示され入力を戻す旨が示される")
    public void 再認証の案内が表示され入力を戻す旨が示される() {
        再認証の案内が表示される();
        assertThat(page().locator("main")).containsText("このタブでログインし直すと、ログアウトの前に入力していた内容を戻します。");
    }

    @ならば("出発地に {string} が戻り {string} と示される")
    public void 出発地に_が戻り_と示される(String value, String message) {
        assertThat(status()).containsText(message);
        assertThat(origin()).hasValue(value);
        browser.checkAccessibility();
    }

    @ならば("出発地は空で {string} と示される")
    public void 出発地は空で_と示される(String message) {
        assertThat(status()).containsText(message);
        assertThat(origin()).hasValue("");
    }

    @ならば("出発地は空である")
    public void 出発地は空である() {
        assertThat(origin()).hasValue("");
    }

    @ならば("{string} と示される")
    public void _と示される(String message) {
        assertThat(status()).containsText(message);
        browser.checkAccessibility();
    }

    @ならば("{string} とは示されない")
    public void _とは示されない(String message) {
        assertThat(page().locator("main")).isVisible();
        assertThat(status()).not().containsText(message);
    }

    @ならば("権限がない画面が表示される")
    public void 権限がない画面が表示される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("権限がありません")))
                .isVisible();
    }

    @ならば("入力の誤りが示される")
    public void 入力の誤りが示される() {
        assertThat(page().locator("#error-summary")).isVisible();
    }

    @ならば("入力の控えがある")
    public void 入力の控えがある() {
        org.assertj.core.api.Assertions.assertThat(savedInput()).as("入力の控え").isNotNull();
    }

    @ならば("入力の控えが残っていない")
    public void 入力の控えが残っていない() {
        org.assertj.core.api.Assertions.assertThat(savedInput()).as("入力の控え").isNull();
    }

    @ならば("入力の控えに CSRF のトークンとファイルが写っていない")
    public void 入力の控えにCSRFのトークンとファイルが写っていない() {
        Object saved = savedInput();
        org.assertj.core.api.Assertions.assertThat(saved).as("入力の控え").isNotNull();
        org.assertj.core.api.Assertions.assertThat(saved.toString())
                .contains("JPTYO")
                .doesNotContain("_csrf")
                .doesNotContain("commercialInvoice")
                .doesNotContain("invoice");
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

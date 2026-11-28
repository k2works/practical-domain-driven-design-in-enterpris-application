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
 * ログイン（A-01）・認証コードの入力（A-02）・ログアウト・権限なし（A-04）・準備中の画面の画面の層のステップ（US-18、Bolt 14。
 * A-02 は Bolt 31）。
 */
public class LoginUiSteps {

    private final BrowserSession browser;
    private final UiUsers users;
    private final String baseUrl;
    private UiUsers.Credentials credentials;
    private Role role;

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
        role = Role.SHIPPER;
        credentials = users.of(role);
    }

    @前提("荷主担当者の未使用の回復コードをログインの前に {int} 個にしておく")
    public void 未使用の回復コードをログインの前に減らしておく(int count) {
        users.leaveUnusedRecoveryCodesBeforeLogin(count);
    }

    @ならば("認証アプリの再登録の画面に {string} と示される")
    public void 再登録の画面に示される(String text) {
        assertThat(page().locator("#remaining-recovery-codes")).hasText(text);
        browser.checkAccessibility();
    }

    @ならば("回復コードでログインした印の知らせに {string} と示される")
    public void 印の知らせに示される(String text) {
        assertThat(page().locator(".app-recovery-notice .app-remaining-recovery-codes"))
                .hasText(text);
        browser.checkAccessibility();
    }

    @前提("認証アプリを登録していない荷主担当者の利用者が登録されている")
    public void 認証アプリを登録していない荷主担当者の利用者が登録されている() {
        role = Role.SHIPPER;
        credentials = users.ofWithoutTotp(role);
    }

    @もし("認証アプリの登録の画面で手入力のキーから作ったコードを入れて登録する")
    public void 手入力のキーから作ったコードを入れて登録する() {
        page().waitForURL(url -> url.contains("/login/totp/enroll"));
        browser.checkAccessibility();
        String key = page().locator("code.app-totp-key").getAttribute("data-manual-key");
        page().getByLabel("認証コード").fill(users.codeFromKey(role, key));
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登録する"))
                .click();
        // 登録の応答は同じ URL で返る（PRG にしない）ので、URL ではなく回復コードの見出しを待つ
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("回復コードの保存")))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("認証アプリの再登録の画面が表示される")
    public void 認証アプリの再登録の画面が表示される() {
        page().waitForURL(url -> url.contains("/account/authenticator"));
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("認証アプリの再登録")))
                .isVisible();
        browser.checkAccessibility();
    }

    @もし("認証アプリの再登録の画面で手入力のキーから作ったコードを入れて登録する")
    public void 再登録の画面で手入力のキーから作ったコードを入れて登録する() {
        String key = page().locator("code.app-totp-key").getAttribute("data-manual-key");
        page().getByLabel("認証コード").fill(users.codeFromKey(role, key));
        // 「後で登録する」と区別するため、名前は完全一致で探す
        page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("登録する").setExact(true))
                .click();
        // 登録し直しの応答は同じ URL で返る（PRG にしない）ので、URL ではなく回復コードの見出しを待つ
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("回復コードの保存")))
                .isVisible();
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("認証アプリを登録し直しました。前の回復コードは使えなくなりました。");
        browser.checkAccessibility();
    }

    @もし("認証アプリの再登録の画面で後で登録する")
    public void 再登録の画面で後で登録する() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("後で登録する"))
                .click();
        page().waitForURL(url -> !url.contains("/account/authenticator"));
    }

    @もし("回復コードを安全な場所に保存したに印を付けて続ける")
    public void 回復コードを保存したに印を付けて続ける() {
        page().getByLabel("回復コードを安全な場所に保存した").check();
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("続ける"))
                .click();
        page().waitForURL(url -> !url.contains("/account/authenticator"));
    }

    @ならば("回復コードでログインした印の知らせが表示される")
    public void 印の知らせが表示される() {
        assertThat(page().locator(".app-recovery-notice")).containsText("回復コードでログインしました。認証アプリを登録し直してください。");
        browser.checkAccessibility();
    }

    @ならば("回復コードでログインした印の知らせは表示されない")
    public void 印の知らせは表示されない() {
        assertThat(page().locator(".app-recovery-notice")).hasCount(0);
    }

    @もし("回復コードでログインした印の知らせの {string} を開く")
    public void 印の知らせのリンクを開く(String name) {
        page().locator(".app-recovery-notice")
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name))
                .click();
    }

    @ならば("回復コードが {int} 個表示される")
    public void 回復コードが表示される(int count) {
        assertThat(page().locator("ol.app-recovery-codes li")).hasCount(count);
    }

    @もし("回復コードを安全な場所に保存したに印を付けてログインを続ける")
    public void 回復コードを保存したに印を付けてログインを続ける() {
        page().getByLabel("回復コードを安全な場所に保存した").check();
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログインを続ける"))
                .click();
        page().waitForURL(url -> url.contains("/login/totp") && !url.contains("enroll"));
    }

    @前提("営業担当者の利用者が登録されている")
    public void 営業担当者の利用者が登録されている() {
        role = Role.SALES;
        credentials = users.of(role);
    }

    @もし("ログインの画面でメールアドレスと password を入れてログインする")
    public void ログインする() {
        submitLogin(credentials.password());
    }

    @もし("ログインの画面でメールアドレスと誤った password を入れてログインする")
    public void 誤ったpasswordでログインする() {
        submitLogin("wrong-" + credentials.password());
    }

    @もし("ログインの画面でメールアドレスと誤った password を {int} 回入れてログインする")
    public void 誤ったpasswordで続けてログインする(int times) {
        // 同じ画面の繰り返しなので、axe の検査は 1 回目だけにする（テスト戦略のアクセシビリティ。Bolt 32d）。ロックの後の画面は
        // 次の手順（正しい password を入れる）で検査する
        for (int i = 0; i < times; i++) {
            submitLogin("wrong-" + credentials.password(), i == 0);
        }
    }

    /** 正しい password を入れても、ロック中は A-02 に進まず A-01 の誤りに戻る（US-18 AC3。Bolt 32）。 */
    @もし("ログインの画面でメールアドレスと正しい password を入れる")
    public void 正しいpasswordを入れる() {
        submitLogin(credentials.password());
    }

    private void submitLogin(String password) {
        submitLogin(password, true);
    }

    private void submitLogin(String password, boolean checkAccessibility) {
        page().navigate(baseUrl + "/login");
        if (checkAccessibility) {
            browser.checkAccessibility();
        }
        page().getByLabel("メールアドレス").fill(credentials.email());
        page().getByLabel("password").fill(password);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン"))
                .click();
        page().waitForLoadState();
        if (checkAccessibility) {
            browser.checkAccessibility();
        }
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
        page().waitForURL(url -> url.contains("/login/totp"));
        browser.checkAccessibility();
    }

    @もし("認証コードの画面で認証アプリのコードを入れて確認する")
    public void 認証アプリのコードを入れて確認する() {
        submitTotp(users.totpCode(role));
        page().waitForURL(url -> !url.contains("/login"));
        browser.checkAccessibility();
    }

    @もし("認証コードの画面で認証アプリを使えない場合を開き回復コードを入れて確認する")
    public void 回復コードを入れて確認する() {
        String code = users.recoveryCode(role);
        page().waitForURL(url -> url.contains("/login/totp"));
        page().getByText("認証アプリを使えない場合").click();
        browser.checkAccessibility();
        page().getByLabel("回復コード", new Page.GetByLabelOptions().setExact(true)).fill(code);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("回復コードで確認"))
                .click();
        page().waitForURL(url -> !url.contains("/login"));
        browser.checkAccessibility();
    }

    @もし("認証コードの画面で誤ったコードを入れて確認する")
    public void 誤ったコードを入れて確認する() {
        submitTotp(users.wrongTotpCode(role));
        page().waitForURL(url -> url.contains("totp-error"));
        browser.checkAccessibility();
    }

    @もし("認証コードの画面でキー操作だけでコードを入れて確認する")
    public void キー操作だけでコードを入れて確認する() {
        page().waitForURL(url -> url.contains("/login/totp"));
        page().getByLabel("認証コード").focus();
        page().keyboard().type(users.totpCode(role));
        page().keyboard().press("Enter");
        page().waitForURL(url -> !url.contains("/login"));
        browser.checkAccessibility();
    }

    private void submitTotp(String code) {
        page().waitForURL(url -> url.contains("/login/totp"));
        browser.checkAccessibility();
        page().getByLabel("認証コード").fill(code);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("確認"))
                .click();
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

    @もし("管理者がその利用者の利用を止める")
    public void 管理者がその利用者の利用を止める() {
        users.suspend(role);
    }

    @ならば("業務データを出さずに再ログインの案内が表示される")
    public void 業務データを出さずに再ログインの案内が表示される() {
        page().waitForURL(url -> url.endsWith("/session-expired"));
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("もう一度ログインしてください")))
                .isVisible();
        assertThat(page().getByRole(AriaRole.NAVIGATION)).hasCount(0);
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("ログインへ")))
                .isVisible();
        browser.checkAccessibility();
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

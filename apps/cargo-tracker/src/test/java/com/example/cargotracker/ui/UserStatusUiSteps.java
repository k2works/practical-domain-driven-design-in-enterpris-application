package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Role;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 利用者の管理（S-20）の利用停止と再開の画面の層のステップ定義（Bolt 34d。US-16 AC5）。詳細を開くステップは
 * {@link UserAdministrationUiSteps} のものを使う。
 */
public class UserStatusUiSteps {

    private static final String SUSPEND_BUTTON = "利用停止の内容を確認する";

    private final BrowserSession browser;
    private final UiUsers users;
    private final JdbcTemplate jdbc;
    private final String baseUrl;

    public UserStatusUiSteps(
            BrowserSession browser,
            UiUsers users,
            JdbcTemplate jdbc,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.jdbc = jdbc;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    @かつ("停止の理由 {string} を選んで利用停止の内容を確認する")
    public void 理由を選んで確認する(String reason) {
        page().getByLabel(reason, new Page.GetByLabelOptions().setExact(true)).check();
        submit(SUSPEND_BUTTON);
    }

    @かつ("停止の理由を選ばずに利用停止の内容を確認する")
    public void 理由を選ばずに確認する() {
        submit(SUSPEND_BUTTON);
    }

    @かつ("利用再開の内容を確認する")
    public void 再開を確認する() {
        submit("利用再開の内容を確認する");
    }

    @ならば("利用状態の変更の確認に現在の状態 {string} と変更内容 {string} と影響と回復手段と実行者が示される")
    public void 確認の領域が示される(String current, String change) {
        Locator confirmation = page().locator("main");
        boolean suspending = change.startsWith("利用を停止");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText(suspending ? "利用を停止しますか" : "利用を再開しますか");
        assertThat(confirmation.locator("dd#current-status")).hasText(current);
        assertThat(confirmation.locator("dd#status-change")).hasText(change);
        assertThat(confirmation).containsText(suspending ? "相手の次の操作から使えなくなります。" : "相手は次のログインから使えます。");
        assertThat(confirmation).containsText(suspending ? "再開できます。" : "もう一度停止できます。");
        assertThat(confirmation).containsText("システム管理者 " + users.displayNameOf(Role.SYSTEM_ADMIN));
    }

    /** 確認ポイント 19（Bolt 38g の行 8）。付け替えは担当範囲の領域で行うので、示すだけ。 */
    @ならば("利用状態の変更の確認にこの利用者の担当企業として荷主の企業が示される")
    public void 確認の領域に担当企業が示される() {
        assertThat(page().locator("main dd#assigned-companies"))
                .hasText("担当企業: " + users.companyNameOf(Role.SHIPPER) + "。");
        browser.checkAccessibility();
    }

    @もし("利用状態の変更の確認で {string} を押す")
    public void 確定する(String button) {
        submit(button);
    }

    @かつ("利用状態の変更の確認から戻る")
    public void 戻る() {
        page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName("戻る").setExact(true))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("{string} と示され、利用状態に {string} が示される")
    public void 知らせと利用状態が示される(String notice, String status) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText(notice);
        assertThat(page().locator("main dl")).containsText(status);
        assertThat(statusOfSales()).isEqualTo(status.equals("利用中") ? "ACTIVE" : "SUSPENDED");
    }

    @ならば("営業担当者の利用者はログインできない")
    public void ログインできない() {
        browser.signOut();
        UiUsers.Credentials sales = users.of(Role.SALES);
        page().navigate(baseUrl + "/login");
        page().getByLabel("メールアドレス").fill(sales.email());
        page().getByLabel("password").fill(sales.password());
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン"))
                .click();
        page().waitForLoadState();
        // 止めた利用者には、誤った認証情報と同じ応答を返す（IA-INV-09）
        assertThat(URI.create(page().url()).getPath()).isEqualTo("/login");
        assertThat(page().locator("main")).containsText("メールアドレスまたは password が正しくありません。");
        browser.checkAccessibility();
    }

    @ならば("営業担当者の利用者はログインできる")
    public void ログインできる() {
        browser.signInAs(Role.SALES, baseUrl + "/login");
        assertThat(URI.create(page().url()).getPath()).doesNotContain("/login");
    }

    @ならば("利用状態の変更の誤り {string} がエラー要約に示され、利用状態は変わっていない")
    public void 誤りが示され変わっていない(String message) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).containsText("利用状態を変更できませんでした");
        assertThat(summary).containsText(message);
        assertThat(statusOfSales()).isEqualTo("ACTIVE");
    }

    @ならば("停止の理由 {string} が選ばれている")
    public void 理由が選ばれている(String reason) {
        assertThat(page().getByLabel(reason, new Page.GetByLabelOptions().setExact(true)))
                .isChecked();
    }

    @ならば("利用状態の領域に {string} と示され、利用停止の操作はない")
    public void 案内が示され操作はない(String message) {
        assertThat(page().locator("#status-note")).hasText(message);
        assertThat(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUSPEND_BUTTON)))
                .hasCount(0);
        assertThat(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("利用再開の内容を確認する")))
                .hasCount(0);
    }

    /** 停止の記録（Bolt 38c2）。理由と停止した人は全文で、停止した日時は実際の時刻になるので社内の形で確かめる。 */
    @ならば("利用状態の領域に停止の理由 {string} と停止した人と停止した日時が示される")
    public void 停止の記録が示される(String reason) {
        Locator status = statusSection();
        assertThat(status.locator("#suspension-record-reason")).hasText("停止の理由: " + reason);
        assertThat(status.locator("#suspension-record-actor"))
                .hasText("停止した人: " + users.displayNameOf(Role.SYSTEM_ADMIN));
        assertThat(status.locator("#suspension-record-at"))
                .hasText(Pattern.compile("^停止した日時: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} Asia/Tokyo（UTC\\+09:00）"
                        + "（UTC \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）$"));
    }

    @ならば("利用状態の領域に停止の記録は示されない")
    public void 停止の記録は示されない() {
        assertThat(statusSection().locator("[id^=suspension-record]")).hasCount(0);
    }

    private Locator statusSection() {
        return page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("利用状態"));
    }

    private void submit(String button) {
        page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(button).setExact(true))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    private String statusOfSales() {
        return jdbc.queryForObject(
                "SELECT status FROM identity.app_user WHERE id = ?",
                String.class,
                users.of(Role.SALES).userId().value());
    }
}

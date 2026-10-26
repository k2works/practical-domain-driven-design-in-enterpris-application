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
import io.cucumber.java.ja.前提;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 利用者の登録（S-20。Bolt 38b3b、US-16 AC1）の画面の層のステップ定義。登録するメールアドレスはシナリオごとに作る。企業は、準備で
 * 足す名前の違う運用する会社（「B 運輸」）を選ぶ（利用者ごとに作る「A 社」と名前が重ならない）。
 */
public class UserRegistrationUiSteps {

    private final BrowserSession browser;
    private final JdbcTemplate jdbc;
    private final UiUsers users;
    private String registeredEmail;

    public UserRegistrationUiSteps(BrowserSession browser, JdbcTemplate jdbc, UiUsers users) {
        this.browser = browser;
        this.jdbc = jdbc;
        this.users = users;
    }

    @前提("管理範囲に運用する会社 {string} がある")
    public void 管理範囲に運用する会社がある(String name) {
        users.administeredOperator(name);
    }

    private Page page() {
        return browser.page();
    }

    private void openEntry() {
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("利用者を登録する"))
                .click();
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("利用者の登録");
        browser.checkAccessibility();
    }

    private void next() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("次へ"))
                .click();
        browser.checkAccessibility();
    }

    @かつ("利用者を登録するを開き、企業 {string} を選んで次へ進む")
    public void 開いて企業を選んで次へ(String company) {
        openEntry();
        企業を選んで次へ(company);
    }

    @かつ("利用者を登録するを開く")
    public void 利用者を登録するを開く() {
        openEntry();
    }

    @かつ("企業 {string} を選んで次へ進む")
    public void 企業を選んで次へ(String company) {
        page().getByLabel(company, new Page.GetByLabelOptions().setExact(true)).check();
        next();
        assertThat(page().locator("main")).containsText(company);
    }

    @かつ("利用者を登録するを開き、企業を選ばずに次へ進む")
    public void 企業を選ばずに次へ() {
        openEntry();
        next();
    }

    @かつ("付ける役割 {string} と新しいメールアドレスと表示名 {string} を入れて確認する")
    public void 入れて確認する(String role, String displayName) {
        registeredEmail = "registered-" + UUID.randomUUID() + "@ui.example";
        page().getByLabel(role, new Page.GetByLabelOptions().setExact(true)).check();
        page().getByLabel("メールアドレス", new Page.GetByLabelOptions().setExact(true))
                .fill(registeredEmail);
        page().getByLabel("表示名", new Page.GetByLabelOptions().setExact(true)).fill(displayName);
        confirm();
    }

    @かつ("付ける役割 {string} とメールアドレス {string} と表示名 {string} を入れて確認する")
    public void 決めた値を入れて確認する(String role, String email, String displayName) {
        registeredEmail = email;
        page().getByLabel(role, new Page.GetByLabelOptions().setExact(true)).check();
        page().getByLabel("メールアドレス", new Page.GetByLabelOptions().setExact(true))
                .fill(email);
        page().getByLabel("表示名", new Page.GetByLabelOptions().setExact(true)).fill(displayName);
        confirm();
    }

    @かつ("何も入れずに確認する")
    public void 何も入れずに確認する() {
        confirm();
    }

    private void confirm() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("確認する"))
                .click();
        browser.checkAccessibility();
    }

    @ならば("利用者の登録の確認に対象 {string} と変更内容と影響と実行者と回復手段が示される")
    public void 確認が示される(String company) {
        Locator main = page().locator("main");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("利用者を登録しますか");
        assertThat(main).containsText(company);
        assertThat(main).containsText(registeredEmail);
        assertThat(main).containsText("登録したメールアドレスへ、初回の設定の案内（24 時間有効）を送ります。");
        assertThat(main).containsText("システム管理者 " + users.displayNameOf(Role.SYSTEM_ADMIN));
        assertThat(main).containsText("誤って登録したときは、利用者の詳細から利用を停止してください（送った案内のリンクも使えなくなります）。");
    }

    @もし("利用者の登録の確認で登録する")
    public void 登録する() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登録する"))
                .click();
    }

    @ならば("利用者を登録したと示され、詳細に表示名 {string} と役割 {string} が示される")
    public void 登録したと示される(String displayName, String role) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText("利用者を登録しました。登録したメールアドレスへ初回の設定の案内を送ります。");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .containsText(displayName);
        assertThat(page().locator("dd#current-roles")).hasText(role);
        browser.checkAccessibility();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.app_user WHERE email = ?", Integer.class, registeredEmail))
                .isEqualTo(1);
    }

    @ならば("登録の誤り {string} がエラー要約に示される")
    public void 誤りが示される(String message) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).containsText("利用者を登録できませんでした");
        assertThat(summary).containsText(message);
    }

    @ならば("登録の誤り {string} と {string} と {string} がエラー要約に示される")
    public void 誤りが3つ示される(String first, String second, String third) {
        Locator links = page().getByRole(AriaRole.ALERT).getByRole(AriaRole.LINK);
        assertThat(page().getByRole(AriaRole.ALERT)).containsText("利用者を登録できませんでした");
        assertThat(links).hasText(new String[] {first, second, third});
    }

    @かつ("エラー要約にフォーカスがあり、最初の誤りのリンクを押すと {string} にフォーカスが移る")
    public void 最初のリンクで欄へ移る(String firstOption) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).isFocused();
        summary.getByRole(AriaRole.LINK).first().click();
        assertThat(page().getByLabel(firstOption, new Page.GetByLabelOptions().setExact(true)))
                .isFocused();
        browser.checkAccessibility();
    }
}

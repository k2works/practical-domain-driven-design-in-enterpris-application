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
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 利用者の管理（S-20）の画面の層のステップ定義（Bolt 34。US-16 の一部）。一覧にはほかのシナリオの利用者も並ぶので、利用者はメール
 * アドレス（シナリオごとに一意）で探し、詳細は利用者 ID の URL で開く（T-59）。
 */
public class UserAdministrationUiSteps {

    private final BrowserSession browser;
    private final UiUsers users;
    private final JdbcTemplate jdbc;
    private final String baseUrl;

    public UserAdministrationUiSteps(
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

    @もし("システム管理者が利用者の一覧を開く")
    public void 一覧を開く() {
        browser.navigate(baseUrl + "/staff/users");
        browser.checkAccessibility();
    }

    @ならば("利用者の一覧に営業担当者の利用者が示される")
    public void 一覧に営業担当者が示される() {
        Locator row = page().locator("tbody tr")
                .filter(new Locator.FilterOptions()
                        .setHasText(users.of(Role.SALES).email()));
        assertThat(row).hasCount(1);
        assertThat(row).containsText("営業担当者");
        assertThat(row).containsText("利用中");
    }

    @もし("システム管理者が営業担当者の利用者の詳細を開く")
    public void 営業担当者の詳細を開く() {
        openDetail(Role.SALES);
    }

    @前提("監査担当者の利用者が登録されている")
    public void 監査担当者が登録されている() {
        users.of(Role.AUDITOR);
    }

    @もし("システム管理者が監査担当者の利用者の詳細を開く")
    public void 監査担当者の詳細を開く() {
        openDetail(Role.AUDITOR);
    }

    @前提("管理範囲外の企業の利用者が登録されている")
    public void 管理範囲外の利用者が登録されている() {
        users.outsideAdministration();
    }

    @ならば("利用者の一覧に管理範囲外の利用者は示されない")
    public void 一覧に範囲外の利用者は示されない() {
        assertThat(page().locator("main"))
                .not()
                .containsText(users.outsideAdministration().email());
    }

    @もし("システム管理者が管理範囲外の利用者の詳細の URL を開く")
    public void 範囲外の詳細を開く() {
        browser.navigate(baseUrl + "/staff/users/"
                + users.outsideAdministration().userId().value());
    }

    @ならば("画面が見つからないと示され、試行が {int} 件記録される")
    public void 見つからないと示され記録される(int count) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("画面が見つかりません");
        assertThat(page().locator("main")).containsText("お探しの画面は見つかりません。");
        assertThat(page().locator("main"))
                .not()
                .containsText(users.outsideAdministration().email());
        browser.checkAccessibility();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE action = 'ADMIN_SCOPE_DENIED' AND target_id = ?",
                        Integer.class,
                        users.outsideAdministration().userId().value().toString()))
                .isEqualTo(count);
    }

    @もし("システム管理者が自分の利用者の詳細を開く")
    public void 自分の詳細を開く() {
        openDetail(Role.SYSTEM_ADMIN);
    }

    private void openDetail(Role role) {
        browser.navigate(baseUrl + "/staff/users/" + users.of(role).userId().value());
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .containsText("利用者の詳細");
        browser.checkAccessibility();
    }

    @かつ("役割 {string} を選んで変更内容を確認する")
    public void 役割を選んで確認する(String role) {
        page().getByLabel(role, new Page.GetByLabelOptions().setExact(true)).check();
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("変更内容を確認する"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @かつ("役割 {string} を外して変更内容を確認する")
    public void 役割を外して確認する(String role) {
        page().getByLabel(role, new Page.GetByLabelOptions().setExact(true)).uncheck();
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("変更内容を確認する"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("{string} と示され、役割を選ぶ欄はない")
    public void 案内が示され欄がない(String message) {
        assertThat(page().locator("main")).containsText(message);
        assertThat(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("変更内容を確認する")))
                .hasCount(0);
    }

    @ならば("役割の変更の誤り {string} がエラー要約に示され、監査担当者の役割は変わっていない")
    public void 誤りが示され監査担当者は変わっていない(String message) {
        誤りが示される(message);
        assertThat(rolesOf(Role.AUDITOR)).containsExactly("AUDITOR");
    }

    @ならば("役割の変更の確認に足す役割 {string} と外す役割 {string} と影響と実行者と回復手段が示される")
    public void 確認の領域が示される(String added, String removed) {
        Locator confirmation = page().locator("main");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("役割を変更しますか");
        assertThat(confirmation.locator("dd#added-roles")).hasText(added);
        assertThat(confirmation.locator("dd#removed-roles")).hasText(removed);
        assertThat(confirmation).containsText("相手の次の操作から効きます");
        assertThat(confirmation).containsText("システム管理者 " + users.displayNameOf(Role.SYSTEM_ADMIN));
        assertThat(confirmation).containsText("元に戻すには、もう一度役割を変更してください");
    }

    @もし("役割の変更の確認で変更する")
    public void 変更する() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("変更する"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @かつ("役割の変更の確認から戻る")
    public void 戻る() {
        page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName("戻る").setExact(true))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @かつ("その間に営業担当者の利用者の情報がほかで更新される")
    public void ほかで更新される() {
        jdbc.update(
                "UPDATE identity.app_user SET version = version + 1 WHERE id = ?",
                users.of(Role.SALES).userId().value());
    }

    @ならば("役割を変更したと示され、今の役割に {string} と {string} が示される")
    public void 変更したと示される(String first, String second) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText("役割を変更しました。相手の次の操作から効きます。");
        Locator current = page().locator("dd#current-roles");
        assertThat(current).containsText(first);
        assertThat(current).containsText(second);
        assertThat(rolesOf(Role.SALES)).containsExactlyInAnyOrder("SALES", "ROUTE_DESIGNER");
    }

    @ならば("役割の変更の誤り {string} がエラー要約に示され、役割は変わっていない")
    public void 誤りが示され変わっていない(String message) {
        誤りが示される(message);
        assertThat(rolesOf(Role.SALES)).containsExactly("SALES");
    }

    @ならば("役割の変更の誤り {string} がエラー要約に示される")
    public void 誤りが示される(String message) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).containsText("役割を変更できませんでした");
        assertThat(summary).containsText(message);
    }

    @ならば("役割 {string} と {string} が選ばれている")
    public void 選ばれている(String first, String second) {
        assertThat(page().getByLabel(first, new Page.GetByLabelOptions().setExact(true)))
                .isChecked();
        assertThat(page().getByLabel(second, new Page.GetByLabelOptions().setExact(true)))
                .isChecked();
    }

    private List<String> rolesOf(Role role) {
        return jdbc.queryForList(
                "SELECT role FROM identity.user_role WHERE user_id = ? ORDER BY role",
                String.class,
                users.of(role).userId().value());
    }
}

package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.identity.domain.model.aggregates.Company;
import com.example.cargotracker.identity.domain.model.aggregates.CompanyRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.shared.domain.CompanyId;
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
 * 業務担当者の担当範囲（S-20 の担当範囲の領域。Bolt 38e。US-16 AC1）の画面の層のステップ定義。担当企業は、シナリオのシステム管理者の
 * 管理範囲に入れた名前の決まった荷主の企業から選ぶ（T-59。一覧にはほかのシナリオの企業は出ない）。
 */
public class UserAssignmentUiSteps {

    private final BrowserSession browser;
    private final UiUsers users;
    private final CompanyRepository companies;
    private final JdbcTemplate jdbc;

    public UserAssignmentUiSteps(
            BrowserSession browser, UiUsers users, CompanyRepository companies, JdbcTemplate jdbc) {
        this.browser = browser;
        this.users = users;
        this.companies = companies;
        this.jdbc = jdbc;
    }

    private Page page() {
        return browser.page();
    }

    private Locator region() {
        return page().getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName("担当範囲").setExact(true));
    }

    @かつ("システム管理者の管理範囲に荷主 {string} と {string} がある")
    public void 管理範囲に荷主がある(String first, String second) {
        users.administeredShipper(first);
        users.administeredShipper(second);
    }

    /**
     * 営業担当者に、シナリオの荷主担当者の企業を担当させる（Bolt 38f）。営業担当者が見積りの側の社内の画面を通る feature の背景に置く
     * 共通の前提。
     */
    @前提("営業担当者は荷主の企業を担当している")
    public void 営業担当者は荷主の企業を担当している() {
        users.assignShipperCompanyToSales();
    }

    /** 経路設計者に、シナリオの荷主担当者の企業を担当させる（Bolt 38g）。経路設計の社内の画面を通る feature の背景に置く共通の前提。 */
    @前提("経路設計者は荷主の企業を担当している")
    public void 経路設計者は荷主の企業を担当している() {
        users.assignShipperCompanyTo(Role.ROUTE_DESIGNER);
    }

    /** 追跡管理者に、シナリオの荷主担当者の企業を担当させる（Bolt 38g）。追跡の社内の画面を通る feature の背景に置く共通の前提。 */
    @前提("追跡管理者は荷主の企業を担当している")
    public void 追跡管理者は荷主の企業を担当している() {
        users.assignShipperCompanyTo(Role.TRACKING_MANAGER);
    }

    @前提("営業担当者に管理範囲の外の荷主が担当企業として割り当てられている")
    public void 範囲の外の荷主が割り当てられている() {
        Company outside = Company.of(new CompanyId(UUID.randomUUID()), "範囲外の荷主", CompanyKind.SHIPPER, true);
        companies.add(outside);
        jdbc.update(
                "INSERT INTO identity.user_assignment (user_id, scope_kind, scope_id, granted_at)"
                        + " VALUES (?, 'COMPANY', ?, CURRENT_TIMESTAMP)",
                users.of(Role.SALES).userId().value(),
                outside.id().value());
    }

    @ならば("担当範囲の領域に担当企業 {string} と {string} の選択肢があり、担当企業はまだないと示される")
    public void 選択肢があり担当企業はまだない(String first, String second) {
        Locator assignments = region();
        assertThat(assignments.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2)))
                .hasText("担当範囲");
        assertThat(assignments.getByRole(
                        AriaRole.GROUP,
                        new Locator.GetByRoleOptions().setName("担当企業").setExact(true)))
                .isVisible();
        assertThat(assignments.getByLabel(first, new Locator.GetByLabelOptions().setExact(true)))
                .not()
                .isChecked();
        assertThat(assignments.getByLabel(second, new Locator.GetByLabelOptions().setExact(true)))
                .not()
                .isChecked();
        assertThat(assignments).containsText("担当企業はまだありません。担当企業は、業務の画面で扱える企業を絞るために使います。");
        assertThat(assignments).not().containsText("今後の更新から");
    }

    @ならば("担当範囲の領域に {string} と示される")
    public void 領域に示される(String message) {
        assertThat(region()).containsText(message);
    }

    @もし("担当企業 {string} を選んで担当範囲の変更内容を確認する")
    public void 選んで確認する(String company) {
        region().getByLabel(company, new Locator.GetByLabelOptions().setExact(true))
                .check();
        確認する();
    }

    @かつ("担当範囲の変更内容を確認する")
    public void 確認する() {
        region().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("担当範囲の変更内容を確認する"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("担当範囲の変更の確認に追加 {string} と解除 {string} と影響と実行者と回復手段が示される")
    public void 確認の領域が示される(String added, String removed) {
        Locator confirmation = page().locator("main");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("担当範囲を変更しますか");
        assertThat(confirmation.locator("#added-companies")).hasText("追加: " + added);
        assertThat(confirmation.locator("#removed-companies")).hasText("解除: " + removed);
        assertThat(confirmation).containsText("この利用者の担当企業が変わります。業務の画面では、次の操作から変更後の担当企業で絞られます。");
        assertThat(confirmation).containsText("システム管理者 " + users.displayNameOf(Role.SYSTEM_ADMIN));
        assertThat(confirmation).containsText("もう一度変更すれば戻せます。");
    }

    @もし("担当範囲の変更の確認で変更する")
    public void 変更する() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("変更する"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("担当範囲を変更したと示され、担当企業 {string} に印が付いている")
    public void 変更したと示される(String company) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText("担当範囲を変更しました。");
        assertThat(region().getByLabel(company, new Locator.GetByLabelOptions().setExact(true)))
                .isChecked();
    }

    @かつ("営業担当者の担当企業は {int} 社である")
    public void 担当企業の数(int count) {
        Integer assigned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM identity.user_assignment WHERE user_id = ? AND scope_kind = 'COMPANY'",
                Integer.class,
                users.of(Role.SALES).userId().value());
        assertThat(assigned).isEqualTo(count);
    }

    @ならば("担当範囲の変更の誤り {string} がエラー要約に示される")
    public void 誤りが示される(String message) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).containsText("担当範囲を変更できませんでした");
        assertThat(summary).containsText(message);
    }

    @ならば("担当範囲の領域はない")
    public void 領域はない() {
        assertThat(page().locator("main")).containsText("利用者の詳細");
        assertThat(region()).hasCount(0);
    }
}

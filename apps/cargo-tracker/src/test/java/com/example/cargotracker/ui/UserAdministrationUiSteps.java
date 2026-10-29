package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseFixture;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseNumberIssuer;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    private final RoutingCaseRepository routingCases;
    private final RoutingCaseNumberIssuer routingCaseNumbers;
    private final String baseUrl;
    /** 役割を外される経路設計者の session の Cookie（Bolt 38g の行 9）。 */
    private List<Cookie> designerSession;
    /** 役割を外される前に経路設計者が経路を確定した案件の番号（Bolt 38g の開発レビューの中 1）。 */
    private String confirmedBeforeRemoval;

    public UserAdministrationUiSteps(
            BrowserSession browser,
            UiUsers users,
            JdbcTemplate jdbc,
            RoutingCaseRepository routingCases,
            RoutingCaseNumberIssuer routingCaseNumbers,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.jdbc = jdbc;
        this.routingCases = routingCases;
        this.routingCaseNumbers = routingCaseNumbers;
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

    /**
     * AC5 の役割の取消し（Bolt 38g の行 9）。役割を 0 にはできないので、外される利用者に 2 つ目の役割を付けておく（テストの準備として表に
     * 入れる。request ごとの確かめが次の request から読む）。
     */
    @前提("経路設計者の利用者に追跡管理者の役割も付いている")
    public void 経路設計者に追跡管理者の役割も付いている() {
        jdbc.update(
                "INSERT INTO identity.user_role (user_id, role, granted_by, granted_at) VALUES (?, 'TRACKING_MANAGER', NULL,"
                        + " CURRENT_TIMESTAMP)",
                users.of(Role.ROUTE_DESIGNER).userId().value());
    }

    /**
     * AC5 の「過去の操作履歴は保持される」の前提（開発レビューの中 1）。担当企業の案件を、経路設計者の利用者が確定した状態で保存する
     * （確定までの画面の流れは重いので、テストの準備として集約を直接保存する。38f2 の予約の前例）。経路版の行が選んだ候補の行を参照する
     * ので、算出した案件を一度保存し、読み直して確定してから更新する（確定のコマンドと同じ口）。
     */
    @かつ("経路設計者の利用者が役割を外す前に担当企業の案件の経路を確定している")
    public void 役割を外す前に経路を確定している() {
        RoutingCase routingCase = RoutingCase.open(
                new RoutingCaseId(UUID.randomUUID()),
                routingCaseNumbers.next(2085),
                UUID.randomUUID(),
                "TR-2085-0001",
                1,
                UUID.randomUUID(),
                List.of(),
                new RouteSpecification(
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        new UtcInstant(Instant.parse("2099-10-31T00:00:00Z")),
                        "GENERAL"),
                new UtcInstant(Instant.parse("2026-10-06T02:00:00Z")),
                new UtcInstant(Instant.parse("2099-11-30T00:00:00Z")),
                UUID.randomUUID(),
                users.companyOf(Role.SHIPPER));
        RoutingCaseFixture.calculateDirect(routingCase);
        routingCases.save(routingCase);
        RoutingCase calculated = routingCases.findByNumber(routingCase.number()).orElseThrow();
        UUID designer = users.of(Role.ROUTE_DESIGNER).userId().value();
        RoutingCaseFixture.confirmCalculated(calculated, new RouteApprover(designer, true));
        routingCases.update(calculated, designer);
        confirmedBeforeRemoval = calculated.number().text();
    }

    /** 外される利用者の session を控える（管理者の操作の間も生かし、後で同じ session で次の request を送る。T-216。前例 38c）。 */
    @かつ("経路設計者の利用者がログインして経路設計の案件一覧を開いている")
    public void 経路設計者がログインして案件一覧を開いている() {
        browser.navigate(baseUrl + "/staff/routing-cases");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("経路設計案件一覧");
        designerSession = browser.cookies();
    }

    @もし("システム管理者が経路設計者の利用者の詳細を開く")
    public void 経路設計者の詳細を開く() {
        openDetail(Role.ROUTE_DESIGNER);
    }

    @ならば("役割を変更したと示され、今の役割は {string} だけになる")
    public void 役割は一つだけになる(String remaining) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText("役割を変更しました。相手の次の操作から効きます。");
        assertThat(page().locator("dd#current-roles")).hasText(remaining);
        assertThat(rolesOf(Role.ROUTE_DESIGNER)).containsExactly("TRACKING_MANAGER");
    }

    @もし("経路設計者の利用者が同じ session で経路設計の案件一覧を開く")
    public void 同じsessionで案件一覧を開く() {
        browser.restoreCookies(designerSession);
        page().navigate(baseUrl + "/staff/routing-cases");
        page().waitForLoadState();
    }

    @もし("経路設計者の利用者が同じ session で追跡一覧を開く")
    public void 同じsessionで追跡一覧を開く() {
        page().navigate(baseUrl + "/staff/tracking-records");
        page().waitForLoadState();
    }

    @ならば("追跡一覧が表示される")
    public void 追跡一覧が表示される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("追跡一覧");
        browser.checkAccessibility();
    }

    /**
     * AC5 の「過去の操作履歴は保持される」。役割の取消しは、その利用者がした業務の記録（経路の確定の承認者）と監査の記録を消さない
     * （Bolt 38g の行 9、開発レビューの中 1）。
     */
    @かつ("役割を外す前のその利用者の経路の確定の記録と、ログインの記録と役割の変更の記録が残っている")
    public void 過去の記録が残っている() {
        UUID designer = users.of(Role.ROUTE_DESIGNER).userId().value();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM routing.route_version v JOIN routing.routing_case c ON v.routing_case_id = c.id"
                                + " WHERE c.case_number = ? AND v.status = 'CONFIRMED' AND v.approved_by = ?",
                        Integer.class,
                        confirmedBeforeRemoval,
                        designer))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE action = 'LOGIN_SUCCEEDED' AND actor_user_id = ?",
                        Integer.class,
                        designer))
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.audit_record WHERE action = 'ROLES_CHANGED' AND target_id = ?",
                        Integer.class,
                        designer.toString()))
                .isOne();
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

    @ならば("存在しない利用者の URL を開いたときと同じく画面が見つからないと示される")
    public void 存在しない利用者と同じく見つからない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("画面が見つかりません");
        assertThat(page().locator("main")).containsText("お探しの画面は見つかりません。");
        assertThat(page().locator("main"))
                .not()
                .containsText(users.outsideAdministration().email());
        browser.checkAccessibility();
        String outOfScope = page().locator("main").innerText();
        browser.navigate(baseUrl + "/staff/users/" + java.util.UUID.randomUUID());
        assertThat(page().locator("main").innerText()).isEqualTo(outOfScope);
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
        // 担当範囲の領域（Bolt 38e）のボタン「担当範囲の変更内容を確認する」にも名前が部分一致するので、役割の領域の中で押す
        region("役割")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("変更内容を確認する"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @かつ("役割 {string} を外して変更内容を確認する")
    public void 役割を外して確認する(String role) {
        page().getByLabel(role, new Page.GetByLabelOptions().setExact(true)).uncheck();
        // 担当範囲の領域（Bolt 38e）のボタン「担当範囲の変更内容を確認する」にも名前が部分一致するので、役割の領域の中で押す
        region("役割")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("変更内容を確認する"))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("詳細に見出し {string} の領域と見出し {string} の領域があり、役割の領域の欄の名前は {string} である")
    public void 役割の領域の見出しと欄の名前(String roles, String status, String field) {
        Locator rolesRegion = region(roles);
        assertThat(rolesRegion.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2)))
                .hasText(roles);
        assertThat(rolesRegion.getByRole(
                        AriaRole.GROUP,
                        new Locator.GetByRoleOptions().setName(field).setExact(true)))
                .isVisible();
        assertThat(region(status).getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2)))
                .hasText(status);
    }

    private Locator region(String name) {
        return page().getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    @ならば("{string} と示され、役割を選ぶ欄はない")
    public void 案内が示され欄がない(String message) {
        // 案内は見出し「役割」の領域の中に出す（Bolt 34f）
        assertThat(region("役割")).containsText(message);
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

    @かつ("エラー要約にフォーカスがあり、誤りのリンクを押すと {string} にフォーカスが移る")
    public void 要約から欄へ移る(String firstOption) {
        // 行 6・7: 要約へのフォーカスはスクリプト、リンクの先へはブラウザの素の移動（Bolt 38）
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).isFocused();
        // 詳細の誤りは 1 つ（誤りが複数の確認の画面は「最初の誤り」のステップを使う）
        assertThat(summary.getByRole(AriaRole.LINK)).hasCount(1);
        summary.getByRole(AriaRole.LINK).click();
        assertThat(page().getByLabel(firstOption, new Page.GetByLabelOptions().setExact(true)))
                .isFocused();
        browser.checkAccessibility();
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

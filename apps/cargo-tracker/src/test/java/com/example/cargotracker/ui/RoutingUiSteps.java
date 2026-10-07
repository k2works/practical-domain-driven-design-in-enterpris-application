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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 経路設計案件一覧（S-05）と経路候補の比較（S-06）、経路の確定（S-07）の画面の層のステップ定義（US-06・US-07。Bolt 17・19）。
 * 案件は、同じシナリオで荷主が提出して詳細経路設計を依頼した見積依頼（{@link UiScenarioState}）のもの。
 * 航海はシナリオごとに一意の航海番号で DB に入れる（画面の層は実際の時計で動くため、2099 年の運航予定にする。
 * 荷主の希望到着期限は 2099-11-02 09:00 JST）。
 */
public class RoutingUiSteps {

    private static final String CALCULATE = "候補を算出";
    private static final String CONFIRM = "この経路で確定する";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final JdbcTemplate jdbc;
    private final String baseUrl;
    private final String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);

    public RoutingUiSteps(
            BrowserSession browser,
            UiScenarioState state,
            JdbcTemplate jdbc,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.state = state;
        this.jdbc = jdbc;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private String voyage(String name) {
        return "UI-" + suffix + "-" + name;
    }

    /**
     * シナリオで入れた航海を消す。航海は DB をシナリオの間で共有するため、残すとほかのシナリオの航海とつながって候補が増え、
     * 上限（20 件）で切られる（Bolt 17 レビュー D-62）。接続時間規則は同じ値なので残しても判定は変わらない。
     * 案件の候補の区間が航海を参照する外部キーはないため、航海だけを消す（候補は案件ごとに残る）。
     */
    @io.cucumber.java.After("(@US-06 or @US-07) and @ui")
    public void シナリオの航海を消す() {
        jdbc.update("DELETE FROM routing.port_call WHERE voyage_number LIKE ?", "UI-" + suffix + "-%");
        jdbc.update("DELETE FROM routing.voyage WHERE voyage_number LIKE ?", "UI-" + suffix + "-%");
    }

    /**
     * 直行（適合）、東京 → シンガポールと、シンガポールで 8 時間ちょうどで接続し期限と同時刻に着く航海（適合）、
     * 期限の 1 日 12 時間後に着く直行（期限超過）。シンガポールの必要最小接続時間は 8 時間。
     */
    @前提("経路の比較に使う航海と接続時間規則がある")
    public void 航海と接続時間規則がある() {
        insertVoyage("DIRECT", "JPTYO", "2099-10-12 00:00:00+00", "NLRTM", "2099-10-30 09:00:00+00");
        insertVoyage("FEEDER", "JPTYO", "2099-10-10 00:00:00+00", "SGSIN", "2099-10-20 00:00:00+00");
        insertVoyage("MAIN", "SGSIN", "2099-10-20 08:00:00+00", "NLRTM", "2099-11-02 00:00:00+00");
        insertVoyage("LATE", "JPTYO", "2099-10-12 00:00:00+00", "NLRTM", "2099-11-03 12:00:00+00");
        jdbc.update(
                "INSERT INTO routing.connection_rule (id, route_scope, port_unlocode, min_connection_minutes, valid_from,"
                        + " valid_to, version) VALUES (?, '*', 'SGSIN', 480, TIMESTAMP WITH TIME ZONE"
                        + " '2026-01-01 00:00:00+00', NULL, 0)",
                UUID.randomUUID());
    }

    private void insertVoyage(String name, String load, String departure, String discharge, String arrival) {
        String number = voyage(name);
        jdbc.update(
                "INSERT INTO routing.voyage (voyage_number, adopted_info_version, source_kind, source_ref, acquired_at,"
                        + " version, updated_at) VALUES (?, ?, 'MANUAL_ENTRY', '画面の層のテスト',"
                        + " TIMESTAMP WITH TIME ZONE '2026-10-01 06:10:00+00', 0, CURRENT_TIMESTAMP)",
                number,
                number + "@1");
        jdbc.update(
                "INSERT INTO routing.port_call VALUES (?, 1, ?, NULL, CAST(? AS TIMESTAMP WITH TIME ZONE))",
                number,
                load,
                departure);
        jdbc.update(
                "INSERT INTO routing.port_call VALUES (?, 2, ?, CAST(? AS TIMESTAMP WITH TIME ZONE), NULL)",
                number,
                discharge,
                arrival);
    }

    /** 案件は DE-16 を受けて非同期に作られるため、行が出るまで開き直す。 */
    @もし("経路設計者が案件一覧から提出した見積依頼の案件を開く")
    public void 経路設計者が案件を開く() {
        Locator row = page().getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions().setHasText(state.transportRequestNumber()));
        for (int i = 0; i < 20; i++) {
            browser.navigate(baseUrl + "/staff/routing-cases");
            if (row.count() > 0) {
                break;
            }
            page().waitForTimeout(500);
        }
        if (row.count() == 0) {
            throw new AssertionError("案件一覧に " + state.transportRequestNumber() + " の案件が出ない（DE-16 の受け取りを待ちきれない）");
        }
        browser.checkAccessibility();
        row.getByRole(AriaRole.LINK).first().click();
        page().waitForURL("**/staff/routing-cases/RC-*");
        assertThat(page().locator("main")).containsText("まだ候補を算出していません");
        browser.checkAccessibility();
    }

    @かつ("キー操作だけで候補を算出する")
    public void キー操作だけで候補を算出する() {
        page().locator("body").focus();
        Locator button = page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(CALCULATE).setExact(true));
        for (int i = 0; i < 60 && !isFocused(button); i++) {
            page().keyboard().press("Tab");
        }
        if (!isFocused(button)) {
            throw new AssertionError("キー操作で「" + CALCULATE + "」に届かない");
        }
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/routing-cases/RC-*");
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("算出しました");
        browser.checkAccessibility();
    }

    private static boolean isFocused(Locator locator) {
        return (Boolean) locator.evaluate("element => element === document.activeElement");
    }

    @ならば("算出した件数が示される")
    public void 算出した件数が示される() {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("候補を");
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("件算出しました");
    }

    @ならば("直行の航海の候補は {string} と示される")
    public void 直行の候補(String judgement) {
        assertThat(candidate(voyage("DIRECT"))).containsText(judgement);
    }

    @ならば("期限と同時刻に着き接続時間が必要最小接続時間と同値の候補は {string} と示される")
    public void 同時刻と同値の候補(String judgement) {
        Locator candidate = candidate(voyage("FEEDER"), voyage("MAIN"));
        assertThat(candidate).containsText(judgement);
        assertThat(candidate).containsText("余裕 0 分");
    }

    @ならば("期限に間に合わない候補は {string} と示され、理由に {string} と参照情報版が示される")
    public void 期限超過の候補(String judgement, String reason) {
        Locator candidate = candidate(voyage("LATE"));
        assertThat(candidate).containsText(judgement);
        assertThat(candidate).containsText(reason);
        assertThat(candidate).containsText("参照情報版 " + voyage("LATE") + "@1");
    }

    /**
     * 航海番号をすべて含む候補のまとまり（候補ごとの article）。航海は DB をシナリオの間で共有するため、ほかのシナリオの航海と
     * つないだ候補もある。このシナリオの航海番号だけで絞る。
     */
    private Locator candidate(String... voyageNumbers) {
        Locator found = page().locator("article.route-candidate");
        for (String voyageNumber : voyageNumbers) {
            found = found.filter(new Locator.FilterOptions().setHasText(voyageNumber));
        }
        assertThat(found).hasCount(1);
        return found;
    }

    @もし("直行の航海の候補の確定へ進む")
    public void 直行の候補の確定へ進む() {
        candidate(voyage("DIRECT"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("この候補で確定へ"))
                .click();
        page().waitForURL("**/confirmation?candidate=*");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .containsText("経路の確定");
        assertThat(page().locator("main")).containsText("この経路で確定しますか");
        assertThat(page().locator("main")).containsText(voyage("DIRECT"));
        browser.checkAccessibility();
    }

    @かつ("判断根拠 {string} を入れてキー操作だけで確定する")
    public void 判断根拠を入れてキー操作だけで確定する(String rationale) {
        page().getByLabel("判断根拠").fill(rationale);
        Locator button = page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(CONFIRM).setExact(true));
        page().getByLabel("判断根拠").focus();
        for (int i = 0; i < 10 && !isFocused(button); i++) {
            page().keyboard().press("Tab");
        }
        if (!isFocused(button)) {
            throw new AssertionError("キー操作で「" + CONFIRM + "」に届かない");
        }
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/routing-cases/RC-*");
    }

    @かつ("判断根拠を入れずに確定する")
    public void 判断根拠を入れずに確定する() {
        page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(CONFIRM).setExact(true))
                .click();
        page().waitForLoadState();
    }

    @ならば("経路を確定したと示され、直行の航海の候補に確定と判断根拠が示される")
    public void 経路を確定したと示される() {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText("経路を確定しました");
        Locator confirmed = candidate(voyage("DIRECT"));
        assertThat(confirmed).containsText("確定した経路");
        assertThat(confirmed).containsText("直行で期限まで 3 日あり");
        assertThat(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("候補を再算出")))
                .hasCount(0);
        browser.checkAccessibility();
    }

    @かつ("案件一覧で案件の状態は {string} と示される")
    public void 案件一覧の状態(String status) {
        browser.navigate(baseUrl + "/staff/routing-cases");
        assertThat(page().getByRole(AriaRole.ROW)
                        .filter(new Locator.FilterOptions().setHasText(state.transportRequestNumber())))
                .containsText(status);
        browser.checkAccessibility();
    }

    @ならば("判断根拠の誤りがエラー要約に示され、経路はまだ確定していない")
    public void 判断根拠の誤りが示される() {
        assertThat(page().locator("#error-summary")).containsText("判断根拠を入れてください");
        assertThat(page().locator("main")).containsText("この経路で確定しますか");
        browser.checkAccessibility();
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("経路候補の比較へ戻る"))
                .click();
        assertThat(candidate(voyage("DIRECT"))).not().containsText("確定した経路");
    }

    @もし("営業担当者が経路設計の案件一覧の URL を直接開く")
    public void 営業担当者が案件一覧を開く() {
        browser.signInAs(Role.SALES, baseUrl + "/login");
        page().navigate(baseUrl + "/staff/routing-cases");
    }

    @ならば("権限なしと示される")
    public void 権限なしと示される() {
        assertThat(page().locator("main")).containsText("この画面を表示する権限がありません");
        browser.checkAccessibility();
    }
}

package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 追跡管理者の S-11 追跡一覧・S-12 追跡の詳細（Bolt 26）と S-13 主要実績の登録（US-12 AC1・AC2。Bolt 26c）の画面の層のステップ定義。追跡記録は、同じシナリオで営業担当者が
 * 確定した予約の追跡番号（{@link UiScenarioState}）のものとする。「一覧の先頭」は、シナリオを逐次に実行し、追跡の開始時刻が実時間で
 * 増えることを前提にしている（Bolt 25b の予約一覧と同じ）。
 */
public class TrackingUiSteps {

    private static final String LIST_CAPTION = "追跡記録（追跡の開始時刻の新しい順）";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public TrackingUiSteps(
            BrowserSession browser,
            UiScenarioState state,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.state = state;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private Locator trackingList() {
        return page().getByRole(AriaRole.TABLE, new Page.GetByRoleOptions().setName(LIST_CAPTION));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    /** 追跡管理者でログインし直してから、ホーム（{@code /}）を開く。ホームは追跡管理者を S-11 へ移す。 */
    @もし("追跡管理者がホームを開く")
    public void 追跡管理者がホームを開く() {
        browser.navigate(baseUrl + "/staff/tracking-records");
        page().navigate(baseUrl + "/");
        page().waitForURL("**/staff/tracking-records");
    }

    @ならば("追跡一覧の先頭に確定した予約の追跡番号が現在状態 {string} とともに示される")
    public void 追跡一覧の先頭に示される(String status) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡一覧")))
                .isVisible();
        Locator firstRow = trackingList().locator("tbody tr").first();
        assertThat(firstRow.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())))
                .isVisible();
        assertThat(firstRow.locator("td").nth(1)).hasText(status);
        browser.checkAccessibility();
    }

    @もし("キー操作だけで追跡一覧の先頭の追跡の詳細を開く")
    public void 追跡一覧から追跡の詳細を開く() {
        page().locator("body").focus();
        tabUntilFocused(trackingList()
                .locator("tbody tr")
                .first()
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/tracking-records/" + state.trackingNumber());
    }

    @ならば("追跡の詳細に現在状態 {string} と予定区間と主要実績がまだないことが示される")
    public void 追跡の詳細が示される(String status) {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("追跡の詳細")))
                .isVisible();
        assertThat(definition("追跡番号")).hasText(state.trackingNumber());
        assertThat(definition("現在状態")).hasText(status);
        Locator legs = page().getByRole(AriaRole.LIST, new Page.GetByRoleOptions().setName("予定区間"));
        assertThat(legs.getByRole(AriaRole.LISTITEM).first()).containsText("区間 1");
        assertThat(page().getByText("主要実績はまだありません。")).isVisible();
        browser.checkAccessibility();
    }

    @もし("キー操作だけで追跡の詳細から追跡一覧へ戻る")
    public void 追跡の詳細から追跡一覧へ戻る() {
        page().locator("body").focus();
        tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("追跡一覧").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/tracking-records");
    }

    // S-13 主要実績の登録（US-12 AC1・AC2。Bolt 26c）

    @もし("キー操作だけで追跡の詳細から実績の登録を開く")
    public void 追跡の詳細から実績の登録を開く() {
        page().locator("body").focus();
        tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("実績を登録").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/tracking-records/" + state.trackingNumber() + "/milestones/new");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("主要実績の登録")))
                .isVisible();
        browser.checkAccessibility();
    }

    @もし("キー操作だけで実績 {string} を場所 {string}・発生時刻 {string}・出典 {string} の参照 {string} で登録する")
    public void 実績を登録する(String kind, String location, String occurredAt, String sourceKind, String reference) {
        page().locator("body").focus();
        chooseRadio(kind);
        typeInto(page().getByLabel("場所", new Page.GetByLabelOptions().setExact(true)), location);
        typeInto(page().getByLabel("発生時刻（日本時間）"), occurredAt);
        chooseRadio(sourceKind);
        typeInto(page().getByLabel("出典の参照", new Page.GetByLabelOptions().setExact(true)), reference);
        tabUntilFocused(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登録する")));
        // リダイレクトの途中で検査しないよう、行き先の画面（登録の結果は追跡の詳細、入力の誤りは送り先の URL）に移り終わってから検査する
        page().keyboard().press("Enter");
        page().waitForURL(url -> !url.endsWith("/milestones/new"));
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("追跡の詳細に結果 {string} が示される")
    public void 追跡の詳細に結果が示される(String message) {
        page().waitForURL("**/staff/tracking-records/" + state.trackingNumber());
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(message);
    }

    @ならば("結果のそばに既存の実績へのリンク {string} が示される")
    public void 既存の実績へのリンクが示される(String label) {
        Locator link = page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName(label).setExact(true));
        assertThat(link).isVisible();
        assertThat(link).hasAttribute("href", "#milestone-1");
    }

    @ならば("主要実績の一覧に {string} が出典 {string} と状態 {string} とともに示される")
    public void 主要実績の一覧に示される(String summary, String source, String milestoneState) {
        Locator item = milestones().getByRole(AriaRole.LISTITEM).first();
        assertThat(item).containsText(summary);
        assertThat(item).containsText(source);
        assertThat(item).containsText(milestoneState);
    }

    @ならば("主要実績の一覧の実績は {int} 件だけである")
    public void 主要実績の件数(int count) {
        assertThat(milestones().getByRole(AriaRole.LISTITEM)).hasCount(count);
    }

    @ならば("追跡の詳細の現在状態は {string} である")
    public void 追跡の詳細の現在状態(String status) {
        assertThat(definition("現在状態")).hasText(status);
    }

    @ならば("実績の登録のエラー要約にフォーカスが移り、{string} と {string} の誤りが示される")
    public void エラー要約が示される(String first, String second) {
        Locator summary = page().locator("#error-summary");
        assertThat(summary).isFocused();
        assertThat(summary).containsText(first);
        assertThat(summary).containsText(second);
    }

    @ならば("実績の登録の場所に {string} が残っている")
    public void 場所が残っている(String location) {
        assertThat(page().getByLabel("場所", new Page.GetByLabelOptions().setExact(true)))
                .hasValue(location);
    }

    private Locator milestones() {
        return page().getByRole(
                        AriaRole.LIST,
                        new Page.GetByRoleOptions().setName("主要実績").setExact(true));
    }

    /** ラジオボタンのグループに Tab で入り、矢印キーで選ぶ（入ったときに選ばれる最初の項目なら Space で選ぶ）。 */
    private void chooseRadio(String label) {
        Locator target = page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
        Locator group = target.locator("xpath=ancestor::fieldset[1]").getByRole(AriaRole.RADIO);
        tabUntilFocused(group.first());
        for (int i = 0; i < 10 && !(Boolean) target.evaluate("element => element === document.activeElement"); i++) {
            page().keyboard().press("ArrowDown");
        }
        page().keyboard().press("Space");
        assertThat(target).isChecked();
    }

    private void typeInto(Locator field, String text) {
        tabUntilFocused(field);
        page().keyboard().press("ControlOrMeta+a");
        page().keyboard().press("Delete");
        if (!text.isEmpty()) {
            page().keyboard().type(text);
        }
    }

    private void tabUntilFocused(Locator target) {
        for (int i = 0; i < 80; i++) {
            page().keyboard().press("Tab");
            if ((Boolean) target.evaluate("element => element === document.activeElement")) {
                return;
            }
        }
        assertThat(target).isFocused();
    }
}

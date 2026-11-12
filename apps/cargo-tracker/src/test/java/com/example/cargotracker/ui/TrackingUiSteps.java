package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 追跡管理者の S-11 追跡一覧・S-12 追跡の詳細（Bolt 26）と S-13 主要実績の登録（US-12 AC1・AC2。Bolt 26c）の画面の層のステップ定義。追跡記録は、同じシナリオで営業担当者が
 * 確定した予約の追跡番号（{@link UiScenarioState}）のものとする。「一覧の先頭」は、シナリオを逐次に実行し、追跡の開始時刻が実時間で
 * 増えることを前提にしている（Bolt 25b の予約一覧と同じ）。
 */
public class TrackingUiSteps {

    private static final String LIST_CAPTION = "追跡記録（追跡の開始時刻の新しい順）";

    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter FORM_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 社内の日時の表記（利用者のタイムゾーンと UTC の併記。Bolt 39g3 の最終取得時刻は実の時計の時刻なので形で確かめる）。 */
    private static final Pattern STAFF_DATE_TIME = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} Asia/Tokyo（UTC\\+09:00）（UTC \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）$");

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final JdbcTemplate jdbc;
    private final String baseUrl;

    /** シナリオでずらした最初の区間の出発予定（Bolt 39g3 の行 22。分の単位にそろえる）。 */
    private ZonedDateTime firstDeparture;

    public TrackingUiSteps(
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

    /**
     * Bolt 39g3 の行 22。画面の層の航海は 2099 年に出発するので、予定より遅れた出発は今より後になり登録できない（T-INV-13）。追跡の予定の最初の区間の出発予定を
     * 今の n 時間前（分の単位）にずらして、遅れた出発を今より前に登録できるようにする（到着予定と当初の到着予定は変えない。予定の規則の「到着は出発の後」は保つ）。
     */
    @前提("追跡の予定の最初の区間の出発予定が今の {int} 時間前になっている")
    public void 最初の区間の出発予定をずらす(int hoursAgo) {
        firstDeparture =
                ZonedDateTime.now(JAPAN).truncatedTo(ChronoUnit.MINUTES).minusHours(hoursAgo);
        int updated = jdbc.update(
                "UPDATE tracking.scheduled_leg SET departure_at = ? WHERE tracking_number = ? AND leg_no = 1",
                firstDeparture.toOffsetDateTime(),
                state.trackingNumber());
        org.assertj.core.api.Assertions.assertThat(updated).isEqualTo(1);
    }

    /** Bolt 39g3 の行 22。発生時刻はずらした最初の区間の出発予定の n 時間後（今より前）。 */
    @もし("キー操作だけで実績 {string} を場所 {string}・発生時刻は最初の区間の出発予定の {int} 時間後・出典 {string} の参照 {string} で登録する")
    public void 出発予定の後の発生時刻で実績を登録する(String kind, String location, int hoursLater, String sourceKind, String reference) {
        実績を登録する(kind, location, firstDeparture.plusHours(hoursLater).format(FORM_DATE_TIME), sourceKind, reference);
    }

    /** Bolt 39g3 の行 22。行を追跡番号で引く（一覧の先頭を前提にしない。Bolt 39gc の学び）。 */
    @もし("キー操作だけで追跡一覧から確定した予約の追跡の詳細を開く")
    public void 追跡一覧から確定した予約の追跡の詳細を開く() {
        page().locator("body").focus();
        browser.tabUntilFocused(trackingList()
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/tracking-records/" + state.trackingNumber());
        browser.checkAccessibility();
    }

    @ならば("追跡の詳細の最新の到着見込みは {string} で、最終取得時刻は {string} である")
    public void 追跡の詳細の見込みと最終取得時刻(String latestEta, String lastAcquiredAt) {
        assertThat(definition("最新の到着見込み")).hasText(latestEta);
        assertThat(definition("最終取得時刻")).hasText(lastAcquiredAt);
    }

    @ならば("追跡の詳細の当初の到着予定は {string} のままである")
    public void 追跡の詳細の当初の到着予定(String originalEta) {
        assertThat(definition("当初の到着予定")).hasText(originalEta);
    }

    /** 最終取得時刻は登録した時刻（実の時計）なので、日時の表記の形で確かめる。 */
    @ならば("追跡の詳細の最新の到着見込みは {string} で、最終取得時刻が示される")
    public void 追跡の詳細の見込みと示された最終取得時刻(String latestEta) {
        assertThat(definition("最新の到着見込み")).hasText(latestEta);
        assertThat(definition("最終取得時刻")).hasText(STAFF_DATE_TIME);
        browser.checkAccessibility();
    }

    @ならば("追跡一覧の確定した予約の行に最新の到着見込み {string} と最終取得時刻が示される")
    public void 追跡一覧の行の見込みと最終取得時刻(String latestEta) {
        Locator row = trackingList()
                .locator("tbody tr")
                .filter(new Locator.FilterOptions()
                        .setHas(page().getByRole(
                                        AriaRole.LINK, new Page.GetByRoleOptions().setName(state.trackingNumber()))));
        assertThat(row.locator("td").nth(3)).hasText(latestEta);
        assertThat(row.locator("td").nth(4)).hasText(STAFF_DATE_TIME);
        browser.checkAccessibility();
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
        browser.tabUntilFocused(trackingList()
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
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.LINK,
                        new Page.GetByRoleOptions().setName("追跡一覧").setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/tracking-records");
    }

    // S-13 主要実績の登録（US-12 AC1・AC2。Bolt 26c）

    @もし("キー操作だけで追跡の詳細から実績の登録を開く")
    public void 追跡の詳細から実績の登録を開く() {
        page().locator("body").focus();
        browser.tabUntilFocused(page().getByRole(
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
        browser.tabUntilFocused(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登録する")));
        // リダイレクトの途中で検査しないよう、行き先の画面（登録の結果は追跡の詳細、入力の誤りは送り先の URL）に移り終わってから検査する
        page().keyboard().press("Enter");
        page().waitForURL(url -> !url.endsWith("/milestones/new"));
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    /**
     * 発生時刻を今の n 時間前（日本時間）にして登録する（Bolt 39g2。固定の日付を実の時計と比べるテストにしない。未来の発生時刻の拒否に当たらない）。
     */
    @もし("キー操作だけで実績 {string} を場所 {string}・発生時刻は今の {int} 時間前・出典 {string} の参照 {string} で登録する")
    public void 今より前の発生時刻で実績を登録する(String kind, String location, int hoursAgo, String sourceKind, String reference) {
        String occurredAt = ZonedDateTime.now(JAPAN).minusHours(hoursAgo).format(FORM_DATE_TIME);
        実績を登録する(kind, location, occurredAt, sourceKind, reference);
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
        browser.checkAccessibility();
    }

    /** Bolt 39gb の行 62。実績番号の項目（id は milestone-N）の文。 */
    @ならば("主要実績の一覧の実績 {int} に {string} が示される")
    public void 主要実績の一覧の実績に示される(int milestoneNo, String text) {
        assertThat(page().locator("#milestone-" + milestoneNo)).containsText(text);
        browser.checkAccessibility();
    }

    @ならば("主要実績の一覧の実績 {int} に採用のボタン {string} が示される")
    public void 採用のボタンが示される(int milestoneNo, String label) {
        assertThat(page().locator("#milestone-" + milestoneNo)
                        .getByRole(
                                AriaRole.BUTTON,
                                new Locator.GetByRoleOptions().setName(label).setExact(true)))
                .isVisible();
    }

    /**
     * Bolt 39gb の行 62。Tab でボタンに移り Enter で押す。送る前と戻った後の URL が同じ（PRG で同じ追跡の詳細に戻る）ので URL では待てない。リダイレクトの後の
     * 追跡の詳細の GET の応答と読み込みを待ってから検査する（遷移の途中で axe を動かさない）。
     */
    @もし("キー操作だけで採用のボタン {string} を押す")
    public void キー操作だけで押す(String label) {
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(label).setExact(true)));
        String detail = "/staff/tracking-records/" + state.trackingNumber();
        page().waitForResponse(
                        response -> "GET".equals(response.request().method())
                                && response.url().endsWith(detail),
                        () -> page().keyboard().press("Enter"));
        page().waitForLoadState();
        assertThat(page().getByRole(AriaRole.STATUS).or(page().getByRole(AriaRole.ALERT)))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("主要実績の一覧の実績は {int} 件だけである")
    public void 主要実績の件数(int count) {
        assertThat(milestones().getByRole(AriaRole.LISTITEM)).hasCount(count);
    }

    @ならば("追跡の詳細の現在状態は {string} である")
    public void 追跡の詳細の現在状態(String status) {
        assertThat(definition("現在状態")).hasText(status);
    }

    /** Bolt 39gc の行 28。確認中は状態バッジ（文字とアイコン）と理由の文で示す。 */
    @ならば("追跡の詳細の現在状態は確認中のバッジで、{string} が示される")
    public void 追跡の詳細の現在状態は確認中(String note) {
        Locator status = definition("現在状態");
        assertThat(status.locator(".badge")).hasText("⚠ 確認中");
        assertThat(status).containsText(note);
        browser.checkAccessibility();
    }

    /** Bolt 39gc の行 28。 */
    @ならば("追跡一覧の先頭の追跡の現在状態は確認中のバッジで示される")
    public void 追跡一覧の先頭の現在状態は確認中() {
        Locator firstRow = trackingList().locator("tbody tr").first();
        assertThat(firstRow.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(state.trackingNumber())))
                .isVisible();
        assertThat(firstRow.locator("td").nth(1).locator(".badge")).hasText("⚠ 確認中");
        browser.checkAccessibility();
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
        browser.tabUntilFocused(group.first());
        for (int i = 0; i < 10 && !(Boolean) target.evaluate("element => element === document.activeElement"); i++) {
            page().keyboard().press("ArrowDown");
        }
        page().keyboard().press("Space");
        assertThat(target).isChecked();
    }

    private void typeInto(Locator field, String text) {
        browser.tabUntilFocused(field);
        page().keyboard().press("ControlOrMeta+a");
        page().keyboard().press("Delete");
        if (!text.isEmpty()) {
            page().keyboard().type(text);
        }
    }
}

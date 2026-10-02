package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 見積依頼の提出の画面の層のステップ定義。利用者と同じく、画面の文言（ラベル・ボタン名）とキー操作で操作する。
 * 入力欄はラベルで取る（Bolt 3 レビュー R-13）。入力の値はこのクラスの 1 か所（{@link #fillRequiredTerms()}）にまとめる。
 * {@code ui.base-url} を与えるとその URL（例: ステージング）を操作し、なければ起動したアプリを操作する。
 */
public class SubmitTransportRequestUiSteps {

    private static final Duration KPI_LIST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration KPI_LIST_POLL_INTERVAL = Duration.ofMillis(500);
    private static final int VIEWPORT_HEIGHT = 800;

    /** 画面の層は実際の時計で動くため、希望到着期限は十分に先の日時にする。 */
    private static final String ARRIVAL_DEADLINE = "2099-11-02 09:00";

    private static final Pattern TRANSPORT_REQUEST_NUMBER = Pattern.compile("TR-\\d{4}-\\d{4,}");
    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final String CONSIGNEE = "荷受人";
    private static final String ORIGIN = "出発地（UN/LOCODE）";
    private static final String DESTINATION = "目的地（UN/LOCODE）";
    private static final String ARRIVAL_DEADLINE_LABEL = "希望到着期限（日本時間）";
    private static final String CARGO_CATEGORY = "貨物種別";
    private static final String PACKAGE_TYPE = "荷姿";
    private static final String PACKAGE_COUNT = "個数";
    private static final String GROSS_WEIGHT = "総重量（kg）";
    private static final String VOLUME = "容積（m3）";
    private static final String SUBMIT = "提出する";

    private final BrowserSession browser;
    private final String baseUrl;
    private String transportRequestNumber;

    public SubmitTransportRequestUiSteps(
            BrowserSession browser, @LocalServerPort int port, @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private void open(String path) {
        page().navigate(baseUrl + path);
        browser.checkAccessibility();
    }

    private Locator field(String label) {
        return page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }

    private Locator submitButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT));
    }

    @前提("荷主が見積依頼の作成画面を開いている")
    public void 荷主が見積依頼の作成画面を開いている() {
        open("/customer/transport-requests/new");
    }

    @もし("荷主が{word}を開く")
    public void 荷主が画面を開く(String screen) {
        switch (screen) {
            case "見積依頼の作成画面" -> open("/customer/transport-requests/new");
            case "見積依頼を提出した完了画面" -> {
                open("/customer/transport-requests/new");
                fillRequiredTerms();
                submit();
            }
            default -> throw new IllegalArgumentException("シナリオの画面名: " + screen);
        }
    }

    @もし("必須条件をキー操作だけで入力して提出する")
    public void 必須条件をキー操作だけで入力して提出する() {
        tabToGroup(CONSIGNEE);
        page().keyboard().press("Space");
        tabTo(ORIGIN);
        page().keyboard().type("JPTYO");
        tabTo(DESTINATION);
        page().keyboard().type("NLRTM");
        tabTo(ARRIVAL_DEADLINE_LABEL);
        page().keyboard().type(ARRIVAL_DEADLINE);
        tabToGroup(CARGO_CATEGORY);
        tabToGroup(PACKAGE_TYPE);
        page().keyboard().press("Space");
        tabTo(PACKAGE_COUNT);
        page().keyboard().type("12");
        tabTo(GROSS_WEIGHT);
        page().keyboard().type("8400");
        tabTo(VOLUME);
        page().keyboard().type("32.5");
        page().keyboard().press("Tab");
        assertThat(submitButton()).isFocused();
        page().keyboard().press("Enter");
        page().waitForURL("**/submitted");
        browser.checkAccessibility();
    }

    @もし("必須条件を入力し、目的地を {string} に、希望到着期限を空にして提出する")
    public void 誤りのある入力で提出する(String destination) {
        fillRequiredTerms();
        field(DESTINATION).fill(destination);
        field(ARRIVAL_DEADLINE_LABEL).fill("");
        submit();
    }

    @もし("必須条件を入力し、貨物種別に {string} を選ぶ")
    public void 貨物種別を選ぶ(String category) {
        fillRequiredTerms();
        radio(category).check();
    }

    @もし("エラー要約の {string} のリンクを選ぶ")
    public void エラー要約のリンクを選ぶ(String item) {
        errorSummary()
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(Pattern.compile("^" + item)))
                .click();
    }

    @もし("目的地を {string} に、希望到着期限を {string} に直して提出する")
    public void 直して提出する(String destination, String deadline) {
        field(DESTINATION).fill(destination);
        field(ARRIVAL_DEADLINE_LABEL).fill(deadline);
        submit();
        page().waitForURL("**/submitted");
        browser.checkAccessibility();
    }

    @もし("提出ボタンを押す")
    public void 提出ボタンを押す() {
        // aria-disabled のボタンは Playwright のクリックが待ち続けるため、利用者のキー操作と同じくフォーカスして Enter で押す
        submitButton().focus();
        page().keyboard().press("Enter");
        // 提出は POST の後に同じ画面（/customer/transport-requests）を返す。移り終わってから検査する
        page().waitForURL("**/customer/transport-requests");
        browser.checkAccessibility();
    }

    @ならば("完了画面に業務番号と {string} と状態 {string} が表示される")
    public void 完了画面に業務番号と版と状態が表示される(String version, String status) {
        Locator number = page().locator("dt:text-is('業務番号') + dd");
        assertThat(number).hasText(Pattern.compile("^" + TRANSPORT_REQUEST_NUMBER.pattern() + " " + version + "$"));
        transportRequestNumber = number.textContent().strip().split(" ")[0];
        assertThat(page().locator("dt:text-is('状態') + dd")).hasText(status);
    }

    @かつ("完了画面にもアドレスバーにも内部の ID が出ない")
    public void 内部のIDが出ない() {
        Assertions.assertThat(page().url()).doesNotContainPattern(UUID_TEXT).contains(transportRequestNumber);
        Assertions.assertThat(page().content()).doesNotContainPattern(UUID_TEXT);
    }

    @かつ("社内の KPI 計測記録の一覧に同じ業務番号が表示される")
    public void KPI計測記録の一覧に同じ業務番号が表示される() {
        open("/staff/kpi-observations");
        // DE-01 の購読は非同期のため、一覧に出るまで間隔を置いて読み直す。
        // Playwright とシナリオの範囲の部品はスレッドに結び付くため、同じスレッドで評価する
        await().atMost(KPI_LIST_TIMEOUT)
                .pollInterval(KPI_LIST_POLL_INTERVAL)
                .pollInSameThread()
                .until(() -> {
                    if (page().getByText(transportRequestNumber, new Page.GetByTextOptions().setExact(true))
                                    .count()
                            > 0) {
                        return true;
                    }
                    page().reload();
                    return false;
                });
        browser.checkAccessibility();
        Assertions.assertThat(page().content()).doesNotContainPattern(UUID_TEXT);
    }

    @ならば("エラー要約にフォーカスが移り、誤りが {int} 件示される")
    public void エラー要約にフォーカスが移る(int count) {
        assertThat(errorSummary()).isFocused();
        assertThat(errorSummary()).containsText("入力内容に " + count + " 件の誤りがあります");
        assertThat(errorSummary().getByRole(AriaRole.LINK)).hasCount(count);
    }

    @かつ("入力した出発地 {string} と目的地 {string} が残っている")
    public void 入力した値が残っている(String origin, String destination) {
        assertThat(field(ORIGIN)).hasValue(origin);
        assertThat(field(DESTINATION)).hasValue(destination);
    }

    @ならば("目的地の入力欄にフォーカスが移る")
    public void 目的地の入力欄にフォーカスが移る() {
        assertThat(field(DESTINATION)).isFocused();
    }

    @ならば("MVP の対象外であることと手動窓口への相談が示される")
    public void 対象外と手動窓口が示される() {
        Locator notice = page().locator("#cargo-category-notice");
        assertThat(notice).isVisible();
        assertThat(notice).containsText("この画面では受け付けていません");
        assertThat(notice).containsText("営業窓口");
        browser.checkAccessibility();
    }

    @かつ("提出ボタンが使えないことが理由とともに示される")
    public void 提出ボタンが使えないことが示される() {
        assertThat(submitButton()).hasAttribute("aria-disabled", "true");
        assertThat(submitButton()).hasAttribute("aria-describedby", Pattern.compile("cargo-category-notice"));
    }

    @ならば("エラー要約に貨物種別の誤りが示され、提出されない")
    public void エラー要約に貨物種別の誤りが示される() {
        assertThat(errorSummary()).isFocused();
        assertThat(errorSummary()
                        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(Pattern.compile("^貨物種別"))))
                .isVisible();
        Assertions.assertThat(page().url()).doesNotContain("/submitted");
    }

    @前提("画面の幅が {int} CSS px である")
    public void 画面の幅を設定する(int width) {
        page().setViewportSize(width, VIEWPORT_HEIGHT);
    }

    @ならば("横スクロールが出ない")
    public void 横スクロールが出ない() {
        Object overflow =
                page().evaluate("() => document.documentElement.scrollWidth > document.documentElement.clientWidth");
        Assertions.assertThat(overflow).isEqualTo(false);
    }

    private Locator errorSummary() {
        return page().locator("#error-summary");
    }

    /** 必須条件をそろえて入力する（キー操作の流れを確かめない場面で使う）。 */
    private void fillRequiredTerms() {
        radio("荷受人 A（仮）").check();
        field(ORIGIN).fill("JPTYO");
        field(DESTINATION).fill("NLRTM");
        field(ARRIVAL_DEADLINE_LABEL).fill(ARRIVAL_DEADLINE);
        radio("一般").check();
        radio("パレット").check();
        field(PACKAGE_COUNT).fill("12");
        field(GROSS_WEIGHT).fill("8400");
        field(VOLUME).fill("32.5");
    }

    private void submit() {
        submitButton().click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    private Locator radio(String label) {
        return page().getByRole(
                        AriaRole.RADIO,
                        new Page.GetByRoleOptions().setName(label).setExact(true));
    }

    /**
     * Tab で次のラジオボタンのグループへ移り、そのグループの選択肢にフォーカスがあることを確かめる。
     * 選んだ選択肢があればそれに、なければ最初の選択肢にフォーカスが移る（Space で選ぶ）。
     */
    private void tabToGroup(String legend) {
        page().keyboard().press("Tab");
        assertThat(page().getByRole(
                                AriaRole.GROUP,
                                new Page.GetByRoleOptions().setName(legend).setExact(true))
                        .locator("input:focus"))
                .hasCount(1);
    }

    /** Tab で次の要素へ移り、その要素がラベルの入力欄であることを確かめる（フォーカスの順序の確認）。 */
    private void tabTo(String label) {
        page().keyboard().press("Tab");
        assertThat(field(label)).isFocused();
    }
}

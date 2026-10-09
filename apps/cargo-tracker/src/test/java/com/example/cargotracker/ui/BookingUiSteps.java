package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 本予約の確定（S-09）・予約の詳細（S-24）と、受付一覧（S-02）の予約の確定待ちの表の画面の層のステップ定義（US-04 AC1・AC2・AC4。
 * Bolt 23b・24）。見積依頼は、同じシナリオで荷主が提出したもの（{@link UiScenarioState}）とする。
 *
 * <p>二重送信と別のタブの確定は、ブラウザーの「戻る」に頼ると読み直しの有無で結果が揺れるため、控えた S-09 のフォームの値（隠し項目と
 * CSRF のトークン）を、ブラウザーと同じ Cookie の HTTP の要求で送る（Bolt 24）。
 */
public class BookingUiSteps {

    private static final String AWAITING_BOOKING = "予約の確定待ちの見積り（有効期限の近い順）";
    private static final String CONFIRM = "本予約を確定する";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;
    private Map<String, String> savedForm;
    private String firstTrackingNumber;
    private String otherTabTrackingNumber;

    public BookingUiSteps(
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

    private String subject(int quotationNo) {
        return state.transportRequestNumber() + " 見積 " + quotationNo;
    }

    private Locator awaitingBooking() {
        return page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName(AWAITING_BOOKING));
    }

    private Locator awaitingRow(int quotationNo) {
        return awaitingBooking().locator("tr", new Locator.LocatorOptions().setHasText(subject(quotationNo)));
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    @もし("営業担当者が受付一覧の予約の確定待ちから提出した見積依頼の見積り {int} の本予約の確定を開く")
    public void 予約の確定待ちから開く(int quotationNo) {
        browser.navigate(baseUrl + "/staff/transport-requests");
        assertThat(awaitingRow(quotationNo)).hasCount(1);
        browser.checkAccessibility();
        awaitingRow(quotationNo)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(subject(quotationNo) + " の本予約の確定へ"))
                .click();
        page().waitForURL("**/staff/bookings/new?**");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("本予約の確定 " + subject(quotationNo));
        assertThat(page().locator("main")).containsText("この表示は開いた時刻での確認です");
        assertThat(page().locator("main")).containsText("本予約を確定しますか");
        browser.checkAccessibility();
    }

    @もし("キー操作だけで契約条件と照合したことを示して本予約を確定する")
    public void キー操作だけで確定する() {
        page().locator("body").focus();
        tabUntilFocused(page().getByLabel("契約条件と照合した（営業担当者の確認）"));
        page().keyboard().press("Space");
        assertThat(page().getByLabel("契約条件と照合した（営業担当者の確認）")).isChecked();
        tabUntilFocused(page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(CONFIRM).setExact(true)));
        page().keyboard().press("Enter");
        page().waitForURL(url -> !url.contains("/staff/bookings/new"));
    }

    /**
     * 追跡の開始は DE-07 を受けた追跡の listener が非同期で進めるので、確定の直後の表示は処理中か完了のどちらか（Bolt 25）。処理中の表示は
     * 画面の単体テストで確かめる。
     */
    @ならば("予約の詳細に本予約を確定したことと追跡番号が示され、追跡の開始が {string} か {string} である")
    public void 予約の詳細が示される(String inProgress, String completed) {
        page().waitForURL("**/staff/bookings/CT*");
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(subject(1) + " で本予約を確定しました。追跡番号は CT");
        assertThat(definition("追跡番号")).hasText(Pattern.compile("CT[A-HJKMNP-Z2-9]{12}"));
        assertThat(definition("業務番号")).hasText(state.transportRequestNumber());
        assertThat(definition("状態")).hasText("確定済み");
        assertThat(definition("追跡の開始"))
                .hasText(Pattern.compile("^(" + literal(inProgress) + "|" + literal(completed) + ")$"));
        browser.checkAccessibility();
    }

    /**
     * 正規表現のメタ文字だけをエスケープする。Playwright は Java の正規表現をブラウザーの JavaScript の正規表現に変えて照合するため、
     * JavaScript にない {@code Pattern.quote} の {@code \Q…\E} は使えない（Bolt 25）。
     */
    private static String literal(String text) {
        return text.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }

    /** 予約サガの完了は DE-07 と DE-22 の 2 つの非同期の処理の後なので、完了と出るまで開き直す（ADR-015。Bolt 25）。 */
    @もし("予約の詳細を更新して追跡の開始が完了するのを待つ")
    public void 追跡の開始の完了を待つ() {
        String url = page().url();
        for (int i = 0; i < 20; i++) {
            if ("完了".equals(definition("追跡の開始").textContent().strip())) {
                break;
            }
            page().waitForTimeout(500);
            browser.navigate(url);
        }
    }

    @ならば("予約の詳細の追跡の開始が {string} で、画面の更新の案内は出ない")
    public void 追跡の開始が完了と出る(String completed) {
        assertThat(definition("追跡の開始")).hasText(completed);
        assertThat(page().getByText("画面を更新して確かめてください")).hasCount(0);
        browser.checkAccessibility();
    }

    /** 予約の確定待ちの表から行がなくなるのは DE-07 を受けた輸送要求の更新（非同期）の後なので、行がなくなるまで開き直す。 */
    @ならば("受付一覧の予約の確定待ちから提出した見積依頼の見積り {int} がなくなる")
    public void 予約の確定待ちからなくなる(int quotationNo) {
        for (int i = 0; i < 20; i++) {
            browser.navigate(baseUrl + "/staff/transport-requests");
            if (awaitingRow(quotationNo).count() == 0) {
                break;
            }
            page().waitForTimeout(500);
        }
        assertThat(awaitingRow(quotationNo)).hasCount(0);
        browser.checkAccessibility();
    }

    @ならば("受付一覧に {string} と示され、見積り {int} は {string} で本予約の確定の入口がない")
    public void 受付一覧に理由が示される(String message, int quotationNo, String status) {
        page().waitForURL("**/staff/transport-requests");
        assertThat(page().getByRole(AriaRole.ALERT)).containsText(subject(quotationNo) + " " + message);
        assertThat(awaitingRow(quotationNo)).containsText(status);
        assertThat(awaitingRow(quotationNo)
                        .getByRole(
                                AriaRole.LINK,
                                new Locator.GetByRoleOptions().setName(subject(quotationNo) + " の本予約の確定へ")))
                .hasCount(0);
        assertThat(awaitingRow(quotationNo)
                        .getByRole(
                                AriaRole.LINK,
                                new Locator.GetByRoleOptions().setName(subject(quotationNo) + " の見積りを開く")))
                .hasCount(1);
        browser.checkAccessibility();
    }

    @もし("送る前の本予約の確定のフォームを控える")
    public void フォームを控える() {
        savedForm = currentForm();
    }

    /** 控えたフォームを同じ値（同じコマンド ID）でもう一度送る。リダイレクト先を、ブラウザーで開いて示す。 */
    @もし("控えた本予約の確定のフォームをもう一度送る")
    public void もう一度送る() {
        firstTrackingNumber = definition("追跡番号").textContent().strip();
        APIResponse response = post(savedForm, 0);
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(302);
        page().navigate(URI.create(baseUrl + "/")
                .resolve(response.headers().get("location"))
                .toString());
    }

    @ならば("最初の確定と同じ追跡番号の予約の詳細が示される")
    public void 最初と同じ追跡番号の予約の詳細() {
        String first = firstTrackingNumber;
        page().waitForURL("**/staff/bookings/" + first);
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(subject(1) + " で本予約を確定しました。追跡番号は " + first + " です。");
        assertThat(definition("追跡番号")).hasText(first);
        browser.checkAccessibility();
    }

    /** いま開いている S-09 の値で、別のコマンド ID の確定を先に送る（別のタブで確定した状態）。リダイレクトは要求の側で追う。 */
    @もし("別のタブで同じ見積り {int} の本予約が確定されている")
    public void 別のタブで確定されている(int quotationNo) {
        Map<String, String> form = currentForm();
        org.assertj.core.api.Assertions.assertThat(form).containsEntry("quotation", Integer.toString(quotationNo));
        form.put("commandId", UUID.randomUUID().toString());
        APIResponse response = post(form, 5);
        String path = URI.create(response.url()).getPath();
        org.assertj.core.api.Assertions.assertThat(path).startsWith("/staff/bookings/CT");
        otherTabTrackingNumber = path.substring(path.lastIndexOf('/') + 1);
    }

    @ならば("受付一覧に見積り {int} はすでに予約に使われていることと別のタブで確定した予約の追跡番号が示される")
    public void 既に予約に使われていると示される(int quotationNo) {
        page().waitForURL("**/staff/transport-requests");
        assertThat(page().getByRole(AriaRole.STATUS))
                .hasText(subject(quotationNo) + " はすでに予約に使われています（追跡番号 " + otherTabTrackingNumber + "）。");
        assertThat(detailLink()).isVisible();
        browser.checkAccessibility();
    }

    @もし("キー操作だけで予約の詳細を開く")
    public void キー操作だけで予約の詳細を開く() {
        page().locator("body").focus();
        tabUntilFocused(detailLink());
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/bookings/CT*");
    }

    @ならば("別のタブで確定した予約の詳細が示される")
    public void 別のタブで確定した予約の詳細() {
        assertThat(definition("追跡番号")).hasText(otherTabTrackingNumber);
        assertThat(definition("業務番号")).hasText(state.transportRequestNumber());
        browser.checkAccessibility();
    }

    private Locator detailLink() {
        return page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName(otherTabTrackingNumber + " の予約の詳細を開く"));
    }

    /** S-09 のフォームの隠し項目と CSRF のトークン。営業担当者の確認は送る値として入れる。 */
    private Map<String, String> currentForm() {
        Locator form = page().locator("main form[method='post']");
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : List.of("_csrf", "commandId", "transportRequest", "quotation")) {
            values.put(name, form.locator("input[name='" + name + "']").inputValue());
        }
        values.put("staffConfirmed", "true");
        return values;
    }

    private APIResponse post(Map<String, String> form, int maxRedirects) {
        FormData data = FormData.create();
        form.forEach(data::set);
        return page().request()
                .post(
                        baseUrl + "/staff/bookings",
                        RequestOptions.create().setForm(data).setMaxRedirects(maxRedirects));
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

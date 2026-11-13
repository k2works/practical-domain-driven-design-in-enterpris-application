package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseOpening;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceDeadlines;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 社内の運用役割の S-14 有人案件の一覧と S-15 有人案件の詳細・受領（US-20 AC2・AC3。Bolt 40-11）の画面の層のステップ定義。案件は、起票の経路
 * （S-23 の引き継ぎ。Bolt 40-15）が画面にまだないので、リポジトリへテストの準備として保存する。案件はシナリオごとに一意な内容で作り、受付番号で行を引く
 * （一覧の先頭を前提にしない）。
 */
public class ServiceCaseUiSteps {

    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    private final BrowserSession browser;
    private final UiUsers users;
    private final ServiceCaseRepository repository;
    private final ServiceCaseNumberIssuer issuer;
    private final String baseUrl;

    private String caseNumber;

    public ServiceCaseUiSteps(
            BrowserSession browser,
            UiUsers users,
            ServiceCaseRepository repository,
            ServiceCaseNumberIssuer issuer,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.repository = repository;
        this.issuer = issuer;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    @前提("受領期限がまだ先の受付済みの有人案件がある")
    public void 受領期限がまだ先の案件() {
        seed(Instant.now().plus(2, ChronoUnit.DAYS));
    }

    @前提("受領期限を過ぎた受付済みの有人案件がある")
    public void 受領期限を過ぎた案件() {
        seed(Instant.now().minus(2, ChronoUnit.HOURS));
    }

    /** 受領期限を指定して受付済みの案件を保存する（テストの準備）。内容は一意にして、重複の判定に掛からないようにする。 */
    private void seed(Instant receiptDue) {
        Instant opened = receiptDue.minus(4, ChronoUnit.HOURS);
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "UI 受領 " + UUID.randomUUID(), "CS")
                .withShipper(new CompanyId(UUID.randomUUID()));
        ServiceDeadlines deadlines = new ServiceDeadlines(opened, receiptDue, receiptDue.plus(1, ChronoUnit.DAYS));
        CaseNumber number = issuer.next(opened.atZone(JAPAN).getYear());
        ServiceCase serviceCase = ((ServiceCaseOpening.Opened)
                        ServiceCase.open(draft, number, deadlines, new UtcInstant(opened)))
                .serviceCase();
        repository.save(serviceCase, users.of(Role.CUSTOMER_SUPPORT).userId());
        caseNumber = number.value();
    }

    @もし("カスタマーサポートが有人案件の一覧を開く")
    public void 一覧を開く() {
        browser.navigate(baseUrl + "/staff/service-cases");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("有人案件")))
                .isVisible();
        browser.checkAccessibility();
    }

    /** マニュアルの撮影用。準備した案件の詳細（S-15）を、カスタマーサポートでログインして開く。 */
    @もし("準備した有人案件の詳細を開く")
    public void 準備した案件の詳細を開く() {
        browser.navigate(baseUrl + "/staff/service-cases/" + caseNumber);
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("有人案件 " + caseNumber)))
                .isVisible();
    }

    @ならば("有人案件の一覧に準備した案件が状態 {string} で示される")
    public void 一覧に案件が示される(String status) {
        if (row().count() == 0) {
            throw new AssertionError(
                    caseNumber + " の行がない。画面: " + page().locator("main").innerText());
        }
        assertThat(row()).isVisible();
        assertThat(row().getByRole(AriaRole.CELL).nth(4)).containsText(status);
    }

    @ならば("有人案件の一覧に準備した案件は示されない")
    public void 一覧に案件は示されない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("有人案件")))
                .isVisible();
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(caseNumber)))
                .hasCount(0);
        browser.checkAccessibility();
    }

    @ならば("準備した案件に期限超過の印は示されない")
    public void 期限超過の印は示されない() {
        assertThat(row()).isVisible();
        assertThat(row()).not().containsText("期限超過");
    }

    @ならば("準備した案件に期限超過の印が示される")
    public void 期限超過の印が示される() {
        assertThat(row()).containsText("⚠");
        assertThat(row()).containsText("期限超過");
        browser.checkAccessibility();
    }

    @もし("キー操作だけで一覧から準備した案件の詳細を開く")
    public void 詳細を開く() {
        page().locator("body").focus();
        browser.tabUntilFocused(row().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(caseNumber)));
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/service-cases/" + caseNumber);
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("有人案件 " + caseNumber)))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("有人案件の詳細の担当は {string} と示される")
    public void 詳細の担当(String assignee) {
        assertThat(definition("担当")).hasText(assignee);
    }

    @ならば("有人案件の詳細の状態は {string} と示される")
    public void 詳細の状態(String status) {
        assertThat(definition("状態")).containsText(status);
    }

    @もし("キー操作だけで準備した案件を受領する")
    public void 受領する() {
        Locator button = page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(caseNumber + " を受領する"));
        browser.tabUntilFocused(button);
        page().keyboard().press("Enter");
        // PRG: 受領の後に同じ URL の詳細へ戻る。結果の文が出るまで待つ
        assertThat(page().getByRole(AriaRole.STATUS)).isVisible();
        browser.checkAccessibility();
    }

    @ならば("有人案件の詳細に結果 {string} が示される")
    public void 詳細に結果が示される(String text) {
        assertThat(page().getByRole(AriaRole.STATUS)).containsText(caseNumber + " " + text.strip());
    }

    @もし("キー操作だけで進捗 {string} を次回更新日時 {string} で更新する")
    public void 進捗を更新する(String content, String nextUpdateAt) {
        type("進捗の内容", content);
        type("次回更新日時（日本時間。変えないときは空）", nextUpdateAt);
        submit("進捗を更新する");
    }

    @もし("キー操作だけで回答 {string} を記録する")
    public void 回答を記録する(String content) {
        type("回答の内容", content);
        submit("回答を記録する");
    }

    @もし("キー操作だけで準備した案件を終結する")
    public void 終結する() {
        submit(caseNumber + " を終結する");
    }

    @もし("キー操作だけで顧客の回答 {string} で準備した案件を再開する")
    public void 再開する(String customerResponse) {
        type("顧客の回答（未解決の内容）", customerResponse);
        submit(caseNumber + " を再開する");
    }

    @ならば("有人案件の詳細に結果 {string} だけが示される")
    public void 詳細に結果だけが示される(String text) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText(text);
        browser.checkAccessibility();
    }

    @ならば("有人案件の詳細の進捗に {string} が示される")
    public void 詳細の進捗(String content) {
        assertThat(page().locator("#progress-heading + ul")).containsText(content);
    }

    @ならば("有人案件の詳細の次回更新に {string} が示される")
    public void 詳細の次回更新(String text) {
        assertThat(definition("次回更新")).containsText(text);
    }

    @ならば("有人案件の詳細の回答に顧客の回答 {string} が示される")
    public void 詳細の顧客の回答(String text) {
        assertThat(page().locator("#answers-heading + ul")).containsText("顧客の回答 " + text);
    }

    /** ラベルで引いた入力欄へキー操作で移り、文字を打つ。 */
    private void type(String label, String text) {
        Locator field = page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
        browser.tabUntilFocused(field);
        page().keyboard().type(text);
    }

    /** 名前のボタンへキー操作で移って押し、結果の文が出るまで待つ（PRG）。 */
    private void submit(String buttonName) {
        Locator button = page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(buttonName));
        Locator status = page().getByRole(AriaRole.STATUS);
        // 前の操作の結果の文が残っていても、PRG で画面が替わったことを待てるよう、押す前の文を覚える
        String before = status.count() == 0 ? "" : status.first().innerText();
        browser.tabUntilFocused(button);
        page().keyboard().press("Enter");
        if (before.isEmpty()) {
            assertThat(status).isVisible();
        } else {
            assertThat(status).not().hasText(before);
        }
    }

    @ならば("有人案件の詳細に受領のボタンは示されない")
    public void 受領のボタンは示されない() {
        assertThat(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("を受領する"))))
                .hasCount(0);
    }

    @もし("キー操作だけで有人案件の一覧へ戻り {string} の絞り込みを選ぶ")
    public void 一覧へ戻って絞り込む(String filter) {
        keyboardLink("有人案件の一覧");
        page().waitForURL("**/staff/service-cases");
        filterLink(filter);
    }

    @もし("キー操作だけで有人案件の一覧の {string} の絞り込みを選ぶ")
    public void 絞り込みを選ぶ(String filter) {
        filterLink(filter);
    }

    private void filterLink(String filter) {
        keyboardLink(filter);
        page().waitForURL(url -> url.contains("filter="));
        assertThat(page().getByRole(
                                AriaRole.LINK,
                                new Page.GetByRoleOptions().setName(filter).setExact(true)))
                .hasAttribute("aria-current", "page");
        browser.checkAccessibility();
    }

    private void keyboardLink(String name) {
        page().locator("body").focus();
        browser.tabUntilFocused(page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true)));
        page().keyboard().press("Enter");
    }

    private Locator row() {
        return page().locator("tbody tr").filter(new Locator.FilterOptions().setHasText(caseNumber));
    }

    /** 定義リストを {@code dt} の名前で引く。 */
    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    private Page page() {
        return browser.page();
    }
}

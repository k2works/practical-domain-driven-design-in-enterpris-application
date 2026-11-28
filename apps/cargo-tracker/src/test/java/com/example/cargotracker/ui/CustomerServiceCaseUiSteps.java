package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 荷主担当者の C-14 問い合わせ一覧と C-15 問い合わせの詳細・再開の依頼（US-23 AC1〜AC4。Bolt 42-3）の画面の層のステップ定義。問い合わせは、荷主が出す
 * 経路（画面）がまだないので、リポジトリへテストの準備として保存する（回答済みまで進めておく）。案件はシナリオごとに一意な内容で作り、受付番号で行を引く。
 */
public class CustomerServiceCaseUiSteps {

    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    private final BrowserSession browser;
    private final UiUsers users;
    private final ServiceCaseRepository repository;
    private final ServiceCaseNumberIssuer issuer;
    private final String baseUrl;

    private String caseNumber;

    public CustomerServiceCaseUiSteps(
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

    @前提("荷主の企業に回答済みの問い合わせがある")
    public void 荷主の企業の問い合わせ() {
        seedAnswered(users.companyOf(Role.SHIPPER));
    }

    @前提("他社に回答済みの問い合わせがある")
    public void 他社の問い合わせ() {
        seedAnswered(new CompanyId(UUID.randomUUID()));
    }

    /** 回答済みの顧客照会を保存する（テストの準備）。内容は一意にして、重複の判定に掛からないようにする。 */
    private void seedAnswered(CompanyId company) {
        UserId staff = users.of(Role.CUSTOMER_SUPPORT).userId();
        Instant opened = Instant.now().minus(1, ChronoUnit.HOURS);
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "UI 問い合わせ " + UUID.randomUUID(), "CS")
                .withShipper(company);
        ServiceDeadlines deadlines =
                new ServiceDeadlines(opened, opened.plus(4, ChronoUnit.HOURS), opened.plus(1, ChronoUnit.DAYS));
        CaseNumber number = issuer.next(opened.atZone(JAPAN).getYear());
        ServiceCase serviceCase = ((ServiceCaseOpening.Opened)
                        ServiceCase.open(draft, number, deadlines, new UtcInstant(opened)))
                .serviceCase();
        repository.save(serviceCase, staff);
        ServiceCase received = serviceCase.receive(staff, new UtcInstant(opened.plus(10, ChronoUnit.MINUTES)));
        repository.update(received, staff);
        // 更新のたびに集約の版が進むので、読み直してから続きの更新をする
        ServiceCase answered = repository
                .findByNumber(number)
                .orElseThrow()
                .recordAnswer("港湾から再送されました", staff, new UtcInstant(opened.plus(30, ChronoUnit.MINUTES)));
        repository.update(answered, staff);
        caseNumber = number.value();
    }

    @もし("荷主担当者が問い合わせの一覧を開く")
    public void 一覧を開く() {
        browser.navigate(baseUrl + "/customer/inquiries");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("問い合わせ"))
                        .first())
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("問い合わせの一覧に準備した問い合わせが状態 {string} と最新の回答 {string} で示される")
    public void 一覧に示される(String status, String answer) {
        assertThat(row()).isVisible();
        assertThat(row().getByRole(AriaRole.CELL).nth(1)).hasText(status);
        assertThat(row().getByRole(AriaRole.CELL).nth(3)).hasText(answer);
    }

    @ならば("問い合わせの受付時間は {string} と示される")
    public void 受付時間が示される(String hours) {
        assertThat(page().locator("main")).containsText("問い合わせの受付時間: " + hours);
    }

    @もし("キー操作だけで一覧から準備した問い合わせの詳細を開く")
    public void 詳細を開く() {
        page().locator("body").focus();
        browser.tabUntilFocused(row().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(caseNumber)));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/inquiries/" + caseNumber);
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("問い合わせ " + caseNumber)))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("問い合わせの詳細に回答 {string} が示される")
    public void 詳細に回答が示される(String answer) {
        assertThat(page().locator("#answers-heading + ul")).containsText(answer);
    }

    @もし("未解決の内容 {string} を入れて再開を依頼する")
    public void 再開を依頼する(String text) {
        Locator field = page().getByLabel("まだ解決していない内容", new Page.GetByLabelOptions().setExact(true));
        browser.tabUntilFocused(field);
        page().keyboard().type(text);
        Locator button = page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("未解決として再開を依頼する"));
        browser.tabUntilFocused(button);
        page().keyboard().press("Enter");
        // PRG: 同じ URL の詳細へ戻る。結果の文が出るまで待つ
        assertThat(page().getByRole(AriaRole.STATUS)).isVisible();
        browser.checkAccessibility();
    }

    @ならば("問い合わせの詳細に結果 {string} と状態 {string} が示される")
    public void 詳細に結果と状態(String result, String status) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText(result);
        assertThat(page().locator("dt:text-is('状態') + dd")).hasText(status);
    }

    @ならば("問い合わせの詳細に回答 {string} とお客様の回答 {string} が残っている")
    public void 履歴が残っている(String answer, String response) {
        assertThat(page().locator("#answers-heading + ul")).containsText(answer);
        assertThat(page().locator("#answers-heading + ul")).containsText("お客様の回答: " + response);
    }

    @もし("荷主担当者が準備した問い合わせの詳細を直接開く")
    public void 詳細を直接開く() {
        browser.navigate(baseUrl + "/customer/inquiries/" + caseNumber);
    }

    @ならば("画面が見つかりませんと示され問い合わせの内容は示されない")
    public void 見つからない画面() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("画面が見つかりません")))
                .isVisible();
        assertThat(page().locator("body")).not().containsText("港湾から再送されました");
        assertThat(page().locator("body")).not().containsText(caseNumber);
        browser.checkAccessibility();
    }

    private Locator row() {
        return page().getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(caseNumber));
    }

    private Page page() {
        return browser.page();
    }
}

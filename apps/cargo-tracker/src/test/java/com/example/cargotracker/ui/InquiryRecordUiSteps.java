package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * カスタマーサポートの S-23 照会の記録（US-11 AC2、IR-INV-02。Bolt 40-15）の画面の層のステップ定義。追跡記録は、予約の確定の流れを通さずに、
 * シナリオの荷主企業（カスタマーサポートの担当企業）のものをテストの準備として保存する。
 */
public class InquiryRecordUiSteps {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Random RANDOM = new Random();
    private static final Pattern CASE_NUMBER = Pattern.compile("(SC-\\d{4}-\\d{4})");

    private final BrowserSession browser;
    private final UiUsers users;
    private final TrackingRecordRepository trackingRecords;
    private final String baseUrl;

    private String trackingNumber;
    private String caseNumber;

    public InquiryRecordUiSteps(
            BrowserSession browser,
            UiUsers users,
            TrackingRecordRepository trackingRecords,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.trackingRecords = trackingRecords;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    @前提("カスタマーサポートは荷主の企業を担当している")
    public void カスタマーサポートは荷主の企業を担当している() {
        users.assignShipperCompanyTo(Role.CUSTOMER_SUPPORT);
    }

    @前提("荷主の企業の追跡記録がある")
    public void 荷主の企業の追跡記録がある() {
        CompanyId shipper = users.companyOf(Role.SHIPPER);
        StringBuilder value = new StringBuilder("CT");
        for (int i = 0; i < 12; i++) {
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        TrackingRecord trackingRecord = TrackingRecord.start(
                        new TrackingNumber(value.toString()),
                        UUID.randomUUID(),
                        shipper,
                        new CompanyId(UUID.randomUUID()),
                        TrackingFixture.schedule(),
                        new UtcInstant(Instant.parse("2099-01-01T00:00:00Z")))
                .trackingRecord();
        trackingRecords.save(trackingRecord);
        trackingNumber = trackingRecord.trackingNumber().value();
    }

    @もし("カスタマーサポートが照会の記録を開く")
    public void 照会の記録を開く() {
        browser.navigate(baseUrl + "/staff/inquiries/new");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("照会の記録")))
                .isVisible();
        browser.checkAccessibility();
    }

    @もし("キー操作だけで経路 {string}・照会者 {string} の照会を追跡番号で引き継ぎなしで記録する")
    public void 引き継ぎなしで記録する(String channel, String inquirer) {
        choose(channel);
        type("追跡番号", trackingNumber);
        choose(inquirer);
        submit();
    }

    @もし("キー操作だけで経路 {string}・照会者 {string} の照会を追跡番号で内容 {string} ・出典 {string} ・担当部門 {string} で引き継いで記録する")
    public void 引き継いで記録する(String channel, String inquirer, String content, String source, String department) {
        choose(channel);
        type("追跡番号", trackingNumber);
        choose(inquirer);
        type("照会内容", content);
        type("参照した出典（任意）", source);
        type("担当部門（引き継ぐとき）", "".equals(department) ? "" : department);
        toggleHandOff();
        submit();
    }

    @もし("キー操作だけで経路 {string}・照会者 {string} の照会を追跡番号で内容なしで引き継いで記録する")
    public void 内容なしで引き継いで記録する(String channel, String inquirer) {
        choose(channel);
        type("追跡番号", trackingNumber);
        choose(inquirer);
        toggleHandOff();
        submit();
    }

    @ならば("照会の記録に結果 {string} だけが示される")
    public void 結果だけが示される(String text) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText(text);
        browser.checkAccessibility();
    }

    @ならば("照会の記録に有人案件の受付番号と回答期限と次回更新が示される")
    public void 受付番号と期限が示される() {
        Locator status = page().getByRole(AriaRole.STATUS);
        assertThat(status).containsText("として引き継ぎました。回答期限は ");
        assertThat(status).containsText("、次回更新は ");
        Matcher matcher = CASE_NUMBER.matcher(status.innerText());
        if (!matcher.find()) {
            throw new AssertionError("受付番号が示されていない: " + status.innerText());
        }
        caseNumber = matcher.group(1);
        browser.checkAccessibility();
    }

    @もし("キー操作だけで結果の有人案件を開く")
    public void 結果の有人案件を開く() {
        Locator link =
                page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("有人案件 " + caseNumber + " を開く"));
        page().locator("body").focus();
        browser.tabUntilFocused(link);
        page().keyboard().press("Enter");
        page().waitForURL("**/staff/service-cases/" + caseNumber);
        browser.checkAccessibility();
    }

    @ならば("有人案件の詳細の状態は受付済みで担当は {string} と示される")
    public void 詳細の状態と担当(String assignee) {
        assertThat(definition("状態")).containsText("受付済み");
        assertThat(definition("担当")).hasText(assignee);
    }

    @ならば("有人案件の詳細の内容は {string} で担当部門は {string} である")
    public void 詳細の内容と担当部門(String content, String department) {
        assertThat(definition("内容")).hasText(content);
        assertThat(definition("担当部門")).hasText(department);
    }

    @ならば("照会の記録のエラー要約に {string} が示される")
    public void エラー要約に示される(String message) {
        assertThat(page().locator("#error-summary")).containsText(message);
        browser.checkAccessibility();
    }

    /**
     * ラジオボタンをキー操作で選ぶ。Tab で止まるのはグループの先頭だけなので、先頭へ移ってから矢印キーで目的の選択肢まで進める
     * （矢印キーは移った先を選ぶ）。先頭の選択肢はスペースで選ぶ。
     */
    private void choose(String label) {
        Locator target = page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
        String name = target.getAttribute("name");
        Locator first = page().locator("input[type=radio][name='" + name + "']").first();
        int index = ((Number)
                        target.evaluate(
                                "el => Array.from(document.querySelectorAll('input[type=radio][name=\"' + el.name + '\"]')).indexOf(el)"))
                .intValue();
        page().locator("body").focus();
        browser.tabUntilFocused(first);
        if (index == 0) {
            page().keyboard().press("Space");
        }
        for (int i = 0; i < index; i++) {
            page().keyboard().press("ArrowDown");
        }
        assertThat(target).isChecked();
    }

    private void toggleHandOff() {
        browser.tabUntilFocused(
                page().getByLabel("有人案件へ引き継ぐ（受付番号を発行します）", new Page.GetByLabelOptions().setExact(true)));
        page().keyboard().press("Space");
    }

    /** 入力欄をキー操作で選び、既定の値を消してから打つ。 */
    private void type(String label, String text) {
        Locator field = page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
        browser.tabUntilFocused(field);
        page().keyboard().press("Control+A");
        page().keyboard().press("Delete");
        page().keyboard().type(text);
    }

    private void submit() {
        Locator status = page().getByRole(AriaRole.STATUS);
        Locator summary = page().locator("#error-summary");
        browser.tabUntilFocused(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("記録する")));
        page().keyboard().press("Enter");
        // PRG の結果の文か、エラー要約のどちらかが出るまで待つ
        page().waitForCondition(() -> status.count() > 0 || summary.count() > 0);
    }

    private Locator definition(String term) {
        return page().locator("dt:text-is('" + term + "') + dd");
    }

    private Page page() {
        return browser.page();
    }
}

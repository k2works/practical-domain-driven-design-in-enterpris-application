package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 週次の KPI（S-22。監査担当者。US-21 AC2〜AC4。W9 の Bolt 44-1）の画面の層のステップ定義。照会記録は、荷主が出す経路（電話・メールの記録は
 * カスタマーサポートの画面）の準備をテストでは省くため、リポジトリへ保存する。画面の層は実際の時計で動き、ほかのシナリオの照会も今週の行に
 * 数えられるため、件数は決め打ちせず、自己照会率が「Web で完結した数 ÷ 照会総数」の算式に合うことを確かめる（T-59）。
 */
public class KpiWeeklyUiSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final Pattern DURATION = Pattern.compile("^\\d+ 時間 \\d+ 分$");
    private static final Pattern PERCENT = Pattern.compile("^\\d+\\.\\d%$");

    private final BrowserSession browser;
    private final UiUsers users;
    private final InquiryRecordRepository inquiries;

    public KpiWeeklyUiSteps(BrowserSession browser, UiUsers users, InquiryRecordRepository inquiries) {
        this.browser = browser;
        this.users = users;
        this.inquiries = inquiries;
    }

    private Page page() {
        return browser.page();
    }

    @前提("荷主の企業に顧客 Web の自己照会が {int} 件と電話の照会が {int} 件ある")
    public void 照会がある(int web, int phone) {
        CompanyId shipper = users.companyOf(Role.SHIPPER);
        UserId user = users.of(Role.SHIPPER).userId();
        for (int i = 0; i < web; i++) {
            save(InquiryChannel.WEB_SELF, shipper, Optional.of(user), Optional.empty());
        }
        UserId support = users.of(Role.CUSTOMER_SUPPORT).userId();
        for (int i = 0; i < phone; i++) {
            save(InquiryChannel.PHONE, shipper, Optional.empty(), Optional.of(support));
        }
    }

    private void save(InquiryChannel channel, CompanyId shipper, Optional<UserId> inquirer, Optional<UserId> recorder) {
        inquiries.save(new InquiryRecord(
                UUID.randomUUID(),
                channel,
                shipper,
                Optional.of(new TrackingNumber("CTABCDEFGH2345")),
                InquirerKind.SHIPPER,
                inquirer,
                new UtcInstant(Instant.now()),
                Optional.empty(),
                recorder));
    }

    private Locator thisWeek() {
        return page().locator("table").first().locator("tbody tr").first();
    }

    @ならば("週次の KPI の今週の行に KPI-01 の件数と中央値と 90 パーセンタイルが示される")
    public void 今週の_KPI01() {
        // 最初の提示（DE-03）の購読は非同期のため、今週の行に件数が出るまで読み直す
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).pollInSameThread().until(() -> {
            if (!"0".equals(thisWeek().locator("td").nth(1).innerText().strip())) {
                return true;
            }
            page().reload();
            return false;
        });
        Locator cells = thisWeek().locator("td");
        assertThat(cells.nth(2)).hasText(DURATION);
        assertThat(cells.nth(3)).hasText(DURATION);
        browser.checkAccessibility();
    }

    /** 基準値の登録（US-21 AC5。W9 の Bolt 44-2）。日付の欄は type=date なので、年月日で入れる。 */
    @もし("監査担当者が KPI-02 の基準値を期間 {string} から {string}・割合 {string}・算出方法 {string} で登録する")
    public void 基準値を登録する(String from, String to, String rate, String method) {
        page().getByLabel("KPI", new Page.GetByLabelOptions().setExact(true)).selectOption("KPI-02");
        page().getByLabel("期間の開始日").fill(from);
        page().getByLabel("期間の終了日（開始日から 28 日め）").fill(to);
        page().getByLabel("中央値（KPI-01 は分、KPI-02 は自己照会率のパーセント）").fill(rate);
        page().getByLabel("算出方法（現行業務の記録からどう算出したか）").fill(method);
        browser.tabUntilFocused(page().getByRole(
                        com.microsoft.playwright.options.AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("基準値を登録する").setExact(true)));
        page().keyboard().press("Enter");
        // 同じ URL へ戻る（PRG）ので、URL ではなく結果の表示を待ってから検査する
        assertThat(page().getByRole(com.microsoft.playwright.options.AriaRole.STATUS))
                .isVisible();
        browser.checkAccessibility();
    }

    @ならば("基準値の登録の結果 {string} が示される")
    public void 登録の結果が示される(String message) {
        assertThat(page().getByRole(com.microsoft.playwright.options.AriaRole.STATUS))
                .containsText(message);
    }

    @ならば("基準値との比較に KPI-02 の基準値 {string} と算出方法 {string} と登録者が示され、パイロット中の値と並べて表示される")
    public void 比較が示される(String baseline, String method) {
        Locator row = page().locator("tbody tr").filter(new Locator.FilterOptions().setHasText("KPI-02 自己照会率"));
        assertThat(row.locator("td").nth(0)).hasText(baseline);
        assertThat(row.locator("td").nth(1)).hasText(PERCENT);
        assertThat(page().locator("main")).containsText("KPI-02 の基準値: 期間");
        assertThat(page().locator("main")).containsText(method);
        assertThat(page().locator("main")).containsText("登録者 監査 花子");
        browser.checkAccessibility();
    }

    @ならば("未完了のイベントの発行の件数が示される")
    public void 未完了の発行の件数が示される() {
        assertThat(page().locator("#publications-heading + p")).hasText(Pattern.compile("^\\d+ 件$"));
    }

    @ならば("週次の KPI の今週の行の KPI-02 は照会総数に対する Web で完結した数の割合である")
    public void 今週の_KPI02() {
        Locator cells = thisWeek().locator("td");
        assertThat(cells.nth(5)).hasText(Pattern.compile("^\\d+$"));
        long total = Long.parseLong(cells.nth(4).innerText().strip());
        long selfServed = Long.parseLong(cells.nth(5).innerText().strip());
        assertThat(total).isGreaterThanOrEqualTo(3);
        assertThat(selfServed).isGreaterThanOrEqualTo(2).isLessThanOrEqualTo(total);
        assertThat(cells.nth(6)).hasText(PERCENT);
        assertThat(cells.nth(6).innerText().strip())
                .isEqualTo(String.format(Locale.ROOT, "%.1f%%", selfServed * 100.0 / total));
    }
}

package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * KPI 計測記録の一覧（S-22 の前身）の画面の層のステップ定義（Bolt 21）。
 * 行は提出した見積依頼の業務番号で選び、ほかのシナリオの行の数・順番には頼らない（T-59）。
 */
public class KpiObservationUiSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    /** 日時表示の共通部品（例: 2026-10-05 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 01:00））。 */
    private static final Pattern STAFF_DATE_TIME = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} Asia/Tokyo（UTC\\+09:00）（UTC \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）$");

    private static final Pattern LEAD_TIME = Pattern.compile("^\\d+ 時間 \\d+ 分$");

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final String baseUrl;

    public KpiObservationUiSteps(
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

    @もし("営業担当者が KPI 計測記録を開く")
    public void KPI計測記録を開く() {
        browser.navigate(baseUrl + "/staff/kpi-observations");
        browser.checkAccessibility();
    }

    @ならば("KPI 計測記録の提出した見積依頼の行に提出時刻と最初の提示時刻と KPI-01 リードタイムが表示される")
    public void 提示済みの行が表示される() {
        // DE-01・DE-03 の購読は非同期のため、行に提示時刻が出るまで読み直す
        Locator row = rowWhen(cells ->
                cells.count() == 4 && !"未提示".equals(cells.nth(2).innerText().strip()));
        assertThat(row.locator("td").nth(1)).hasText(STAFF_DATE_TIME);
        assertThat(row.locator("td").nth(2)).hasText(STAFF_DATE_TIME);
        assertThat(row.locator("td").nth(3)).hasText(LEAD_TIME);
        browser.checkAccessibility();
    }

    @ならば("KPI 計測記録の提出した見積依頼の行に未提示と示されリードタイムは算出しない")
    public void 未提示の行が表示される() {
        Locator row = rowWhen(cells -> cells.count() == 4);
        assertThat(row.locator("td").nth(1)).hasText(STAFF_DATE_TIME);
        assertThat(row.locator("td").nth(2)).hasText("未提示");
        assertThat(row.locator("td").nth(3)).containsText("未提示のため算出しない");
        browser.checkAccessibility();
    }

    /** 提出した見積依頼の行が出て、条件を満たすまで読み直す。Playwright の部品はスレッドに結び付くため、同じスレッドで評価する。 */
    private Locator rowWhen(Predicate<Locator> condition) {
        Locator row = page().locator("tbody tr")
                .filter(new Locator.FilterOptions()
                        .setHas(page().getByText(
                                        state.transportRequestNumber(), new Page.GetByTextOptions().setExact(true))));
        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).pollInSameThread().until(() -> {
            if (row.count() == 1 && condition.test(row.locator("td"))) {
                return true;
            }
            page().reload();
            return false;
        });
        return row;
    }
}

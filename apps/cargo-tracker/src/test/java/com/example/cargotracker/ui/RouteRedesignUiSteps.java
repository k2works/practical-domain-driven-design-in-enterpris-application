package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotification;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 割り当てた経路が再設計要になった見積りの、荷主の見積依頼の詳細（C-04）・承認（C-17）、営業担当者の受付一覧（S-02）・本予約の確定
 * （S-09）、経路設計者の確定（S-07）の画面の層のステップ定義（US-07 AC5。Bolt 37e3）。見積依頼は、同じシナリオで荷主が提出したもの
 * （{@link UiScenarioState}）とする。本番には評価の入口がまだない（W7）ので、経路版の再設計要の記録はデータベースで入れ、見積りの印は
 * 見積りの公開 API で付ける（本番の DE-06 の listener と同じ契約を通す）。
 */
public class RouteRedesignUiSteps {

    private static final String AWAITING_BOOKING = "予約の確定待ちの見積り（有効期限の近い順）";
    private static final String TO_APPROVAL = "見積りと経路を確かめて承認へ";

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final JdbcTemplate jdbc;
    private final RouteRedesignNotification notification;
    private final String baseUrl;
    private String caseNumber;
    private int confirmedCandidateNo;

    public RouteRedesignUiSteps(
            BrowserSession browser,
            UiScenarioState state,
            JdbcTemplate jdbc,
            RouteRedesignNotification notification,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.state = state;
        this.jdbc = jdbc;
        this.notification = notification;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private String subject(int quotationNo) {
        return state.transportRequestNumber() + " 見積 " + quotationNo;
    }

    private Locator alert() {
        return page().getByRole(AriaRole.ALERT);
    }

    /**
     * 確定した経路版を再設計要にし（経路設計の記録）、その経路版を割り当てた見積りに印を付ける（見積りの公開 API）。評価と DE-06 の
     * listener が本番で行うことを、画面の層の準備としてこの 2 つで起こす。
     */
    @前提("提出した見積依頼の割り当てた経路が再設計要になっている")
    public void 割り当てた経路が再設計要になっている() {
        Map<String, Object> confirmed = jdbc.queryForMap(
                "SELECT c.case_number, v.route_version_no, v.selected_candidate_no"
                        + " FROM routing.routing_case c JOIN routing.route_version v ON v.routing_case_id = c.id"
                        + " WHERE c.transport_request_number = ? AND v.status = 'CONFIRMED'",
                state.transportRequestNumber());
        caseNumber = (String) confirmed.get("case_number");
        int routeVersionNo = ((Number) confirmed.get("route_version_no")).intValue();
        confirmedCandidateNo = ((Number) confirmed.get("selected_candidate_no")).intValue();
        int updated = jdbc.update(
                "UPDATE routing.route_version v SET status = 'REDESIGN_REQUIRED',"
                        + " redesign_required_at = CURRENT_TIMESTAMP, redesign_reasons = 'DEADLINE_EXCEEDED'"
                        + " FROM routing.routing_case c"
                        + " WHERE v.routing_case_id = c.id AND c.case_number = ? AND v.route_version_no = ?",
                caseNumber,
                routeVersionNo);
        if (updated != 1) {
            throw new IllegalStateException("再設計要にする経路版がありません: " + caseNumber);
        }
        RouteRedesignNotificationReceipt receipt = notification.notifyRedesignRequired(
                new RouteRedesignNotificationRequest(caseNumber, routeVersionNo, new UtcInstant(Instant.now())));
        if (!(receipt instanceof RouteRedesignNotificationReceipt.Marked)) {
            throw new IllegalStateException("見積りに再設計要の印を付けられませんでした: " + receipt);
        }
    }

    @ならば("荷主の見積依頼の詳細に承認の入口がなく {string} と示される")
    public void 荷主の詳細に承認の入口がない(String message) {
        browser.navigate(baseUrl + "/customer/transport-requests/" + state.transportRequestNumber());
        assertThat(page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(TO_APPROVAL)))
                .hasCount(0);
        assertThat(page().locator("main")).containsText(message);
        assertThat(page().locator("main")).not().containsText("再設計要");
        browser.checkAccessibility();
    }

    @もし("荷主が見積り {int} の見積りと経路の承認を直接開く")
    public void 荷主が承認を直接開く(int quotationNo) {
        browser.navigate(baseUrl + "/customer/transport-requests/" + state.transportRequestNumber() + "/quotations/"
                + quotationNo + "/approval");
    }

    @ならば("荷主の見積依頼の詳細に戻り {string} と示される")
    public void 荷主の詳細に戻る(String message) {
        page().waitForURL("**/customer/transport-requests/" + state.transportRequestNumber());
        assertThat(alert()).containsText(state.transportRequestNumber() + " " + message);
        assertThat(alert()).not().containsText("再設計要");
        browser.checkAccessibility();
    }

    @ならば("受付一覧の予約の確定待ちの見積り {int} に {string} と示され、本予約の確定の入口がない")
    public void 予約の確定待ちに再設計要と示される(int quotationNo, String label) {
        browser.navigate(baseUrl + "/staff/transport-requests");
        Locator row = page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName(AWAITING_BOOKING))
                .locator("tr", new Locator.LocatorOptions().setHasText(subject(quotationNo)));
        assertThat(row).hasCount(1);
        assertThat(row).containsText(label);
        assertThat(row.getByRole(
                        AriaRole.LINK, new Locator.GetByRoleOptions().setName(subject(quotationNo) + " の本予約の確定へ")))
                .hasCount(0);
        browser.checkAccessibility();
    }

    @もし("営業担当者が見積り {int} の本予約の確定を直接開く")
    public void 本予約の確定を直接開く(int quotationNo) {
        browser.navigate(baseUrl + "/staff/bookings/new?transportRequest=" + state.transportRequestNumber()
                + "&quotation=" + quotationNo);
    }

    @ならば("受付一覧に戻り {string} と示される")
    public void 受付一覧に戻る(String message) {
        page().waitForURL("**/staff/transport-requests");
        assertThat(alert()).containsText(state.transportRequestNumber() + " " + message);
        browser.checkAccessibility();
    }

    @もし("経路設計者が再設計要の案件の確定した候補の確定を直接開く")
    public void 再設計要の案件の確定を直接開く() {
        browser.navigate(
                baseUrl + "/staff/routing-cases/" + caseNumber + "/confirmation?candidate=" + confirmedCandidateNo);
    }

    @ならば("経路候補の比較に戻り {string} と示される")
    public void 経路候補の比較に戻る(String message) {
        page().waitForURL("**/staff/routing-cases/" + caseNumber);
        assertThat(alert()).containsText(message);
        browser.checkAccessibility();
    }
}

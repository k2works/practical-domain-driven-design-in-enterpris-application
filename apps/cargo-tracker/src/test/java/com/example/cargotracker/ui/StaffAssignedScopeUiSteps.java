package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.shared.domain.UserId;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 営業担当者の担当範囲（Bolt 38f。US-16 AC4）の画面の層のステップ定義。担当企業の外の見積依頼は、テストの準備としてサービスで
 * 提出する（範囲の外の荷主の利用者は作らない）。範囲の外は一覧に出ず、URL を開くと存在しない見積依頼と同じく画面が見つからない。
 */
public class StaffAssignedScopeUiSteps {

    private final BrowserSession browser;
    private final UiUsers users;
    private final TransportRequestCommandService transportRequests;
    private final String baseUrl;
    private String outOfScopeNumber;

    public StaffAssignedScopeUiSteps(
            BrowserSession browser,
            UiUsers users,
            TransportRequestCommandService transportRequests,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.transportRequests = transportRequests;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    @前提("担当企業の外の荷主が見積依頼を提出している")
    public void 担当企業の外の荷主が提出している() {
        SubmissionOutcome.Submitted submitted =
                (SubmissionOutcome.Submitted) transportRequests.submit(new SubmitTransportRequestCommand(
                        users.outOfScopeShipper(),
                        new UserId(UUID.randomUUID()),
                        ShipmentTermsFixture.completeInput()));
        outOfScopeNumber = submitted.number().text();
    }

    @ならば("受付一覧に担当企業の外の見積依頼が表示されない")
    public void 受付一覧に範囲の外が表示されない() {
        assertThat(page().locator("main")).not().containsText(outOfScopeNumber);
    }

    @もし("営業担当者が担当企業の外の見積依頼の審査画面の URL を開く")
    public void 範囲の外の審査画面を開く() {
        browser.navigate(baseUrl + "/staff/transport-requests/" + outOfScopeNumber);
    }

    @ならば("存在しない見積依頼の URL を開いたときと同じく画面が見つからないと示される")
    public void 存在しない見積依頼と同じく見つからない() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("画面が見つかりません");
        assertThat(page().locator("main")).not().containsText(outOfScopeNumber);
        // 行 16b: /error のディスパッチでも主体の担当企業が null で 500 にならず、共通の 404 が出る（認証の前のレイアウト。表示名は出さない）
        assertThat(page().locator("main")).containsText("お探しの画面は見つかりません。");
        browser.checkAccessibility();
        String outOfScope = page().locator("main").innerText();
        browser.navigate(baseUrl + "/staff/transport-requests/TR-2099-9999");
        assertThat(page().locator("main")).hasText(outOfScope);
    }
}

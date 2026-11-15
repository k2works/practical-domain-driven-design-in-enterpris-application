package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 荷主の見積りの条件の相談（C-05。US-24 AC3。W9 の Bolt 46-1）の画面の層のステップ定義。相談すると追跡が購読して有人案件（見積り相談）を別の
 * トランザクションで起票するので、起票されるまで待ち、荷主の問い合わせ一覧（C-14）に載ることを確かめる。見積依頼は、同じシナリオで荷主が
 * 提出したもの（{@link UiScenarioState}）とする。
 */
public class QuotationConsultationUiSteps {

    private static final String RESPOND = "見積りに回答する";
    private static final String CONSULT = "条件を相談する";
    private static final int WAIT_ATTEMPTS = 40;

    private final BrowserSession browser;
    private final UiScenarioState state;
    private final UiUsers users;
    private final ServiceCaseRepository serviceCases;
    private final String baseUrl;

    public QuotationConsultationUiSteps(
            BrowserSession browser,
            UiScenarioState state,
            UiUsers users,
            ServiceCaseRepository serviceCases,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.state = state;
        this.users = users;
        this.serviceCases = serviceCases;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private Locator link(String name) {
        return page().getByRole(
                        AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator button(String name) {
        return page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    @もし("キー操作だけで見積りへの回答を開き、相談内容 {string} で条件を相談する")
    public void キー操作だけで条件を相談する(String content) {
        page().locator("body").focus();
        browser.tabUntilFocused(link(RESPOND));
        page().keyboard().press("Enter");
        page().waitForURL(
                        "**/customer/transport-requests/" + state.transportRequestNumber() + "/quotations/1/response");
        browser.checkAccessibility();
        Locator field = page().getByLabel("相談の内容（必須。500 文字まで）", new Page.GetByLabelOptions().setExact(true));
        browser.tabUntilFocused(field);
        page().keyboard().type(content);
        browser.tabUntilFocused(button(CONSULT));
        page().keyboard().press("Enter");
        page().waitForURL("**/customer/transport-requests/" + state.transportRequestNumber());
        browser.checkAccessibility();
    }

    @ならば("見積依頼の詳細に見積り {int} の条件の相談を受け付けたことが表示され、見積りの回答の入口が残る")
    public void 相談を受け付けたことが表示される(int quotationNo) {
        assertThat(page().getByRole(AriaRole.STATUS))
                .containsText(state.transportRequestNumber() + " 見積 " + quotationNo + " の条件の相談を受け付けました");
        assertThat(link(RESPOND)).isVisible();
        browser.checkAccessibility();
    }

    @ならば("見積り相談の有人案件が起票され、荷主の問い合わせの一覧で確かめられる")
    public void 有人案件が問い合わせの一覧に載る() {
        Optional<ServiceCase> opened = Optional.empty();
        for (int i = 0; i < WAIT_ATTEMPTS && opened.isEmpty(); i++) {
            opened = serviceCases.findByShipper(users.companyOf(Role.SHIPPER), 50).stream()
                    .filter(serviceCase -> serviceCase.kind() == ServiceCaseKind.QUOTATION_CONSULTATION)
                    .filter(serviceCase -> serviceCase.cause().contains(state.transportRequestNumber()))
                    .findFirst();
            if (opened.isEmpty()) {
                page().waitForTimeout(500);
            }
        }
        ServiceCase serviceCase = opened.orElseThrow(
                () -> new AssertionError("見積り相談の有人案件が起票されない（追跡の購読を待ちきれない）: " + state.transportRequestNumber()));
        browser.navigate(baseUrl + "/customer/inquiries");
        assertThat(page().getByRole(
                                AriaRole.LINK,
                                new Page.GetByRoleOptions()
                                        .setName(serviceCase.number().value())))
                .isVisible();
        browser.checkAccessibility();
    }
}

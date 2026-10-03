package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;

/**
 * C-03 の段階入力（UI-HO-04）の画面の層のステップ定義。段階の一覧はナビゲーション「入力の段階」で取る。
 * 入力の値は {@link SubmitTransportRequestUiSteps} と同じものを使う。
 */
public class StepwiseTransportRequestUiSteps {

    private static final String STEPS = "入力の段階";
    private static final String NEXT = "次へ";
    private static final String SUBMIT = "提出する";

    private final BrowserSession browser;
    private final SubmitTransportRequestUiSteps submitSteps;

    public StepwiseTransportRequestUiSteps(BrowserSession browser, SubmitTransportRequestUiSteps submitSteps) {
        this.browser = browser;
        this.submitSteps = submitSteps;
    }

    private Page page() {
        return browser.page();
    }

    private Locator stepLink(String name) {
        return page().getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName(STEPS))
                .getByRole(
                        AriaRole.LINK,
                        new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator field(String label) {
        return page().getByLabel(label, new Page.GetByLabelOptions().setExact(true));
    }

    @ならば("段階 {string} が現在の段階として表示される")
    public void 現在の段階(String step) {
        assertThat(stepLink(step)).hasAttribute("aria-current", "step");
        assertThat(page().getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName(step).setExact(true)))
                .isVisible();
    }

    @もし("キー操作だけで輸送条件と貨物を入力して確認の段階まで進む")
    public void キー操作だけで確認の段階まで進む() {
        submitSteps.キー操作だけで輸送条件と貨物を入力する();
        keyboardNext();
        keyboardNext();
        browser.checkAccessibility();
    }

    @ならば("確認の段階に {string} の {string} と {string} の {string} が表示される")
    public void 確認の段階に表示される(String term1, String value1, String term2, String value2) {
        Locator summary = page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("4 確認"));
        assertThat(summary.locator("dt:text-is('" + term1 + "') + dd")).hasText(value1);
        assertThat(summary.locator("dt:text-is('" + term2 + "') + dd")).hasText(value2);
    }

    @もし("キー操作だけで確認の段階から提出する")
    public void キー操作だけで提出する() {
        tabUntilFocused(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT)));
        page().keyboard().press("Enter");
        page().waitForURL(SubmitTransportRequestUiSteps.DETAIL_URL);
        browser.checkAccessibility();
    }

    @もし("必須条件を入力し、商業送り状に {string} を選んで確認の段階まで進む")
    public void 書類を選んで確認の段階まで進む(String fileName) {
        submitSteps.必須条件と書類を入力して確認の段階まで進む(fileName);
        browser.checkAccessibility();
    }

    @かつ("段階の一覧の {string} を選ぶ")
    public void 段階の一覧から選ぶ(String step) {
        stepLink(step).click();
        browser.checkAccessibility();
    }

    @もし("目的地を {string} に直して確認の段階まで進む")
    public void 目的地を直して確認の段階まで進む(String destination) {
        field("目的地（UN/LOCODE）").fill(destination);
        submitSteps.確認の段階まで進む();
    }

    @もし("必須条件を入力し、個数を {string} にして提出する")
    public void 個数を変えて提出する(String count) {
        submitSteps.項目を変えて提出する("個数", count);
    }

    @ならば("個数の入力欄にフォーカスが移る")
    public void 個数にフォーカスが移る() {
        assertThat(field("個数")).isFocused();
    }

    /** 見えている「次へ」まで Tab で進み、Enter で次の段階へ移る。 */
    private void keyboardNext() {
        tabUntilFocused(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEXT)));
        page().keyboard().press("Enter");
    }

    private void tabUntilFocused(Locator target) {
        for (int i = 0; i < 30; i++) {
            if ((Boolean) target.evaluate("element => element === document.activeElement")) {
                return;
            }
            page().keyboard().press("Tab");
        }
        assertThat(target).isFocused();
    }
}

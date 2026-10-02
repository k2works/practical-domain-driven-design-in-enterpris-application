package com.example.cargotracker.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;

/**
 * 画面の層のシナリオの後始末。失敗したら画面のスクリーンショットを添付し、
 * どのシナリオでも、表示したすべての画面にアクセシビリティの違反がないことを確かめる（テスト戦略）。
 */
public class UiHooks {

    private final BrowserSession browser;

    public UiHooks(BrowserSession browser) {
        this.browser = browser;
    }

    @After(value = "@ui", order = 10_000)
    public void 失敗した画面を残す(Scenario scenario) {
        if (scenario.isFailed()) {
            scenario.attach(browser.page().screenshot(), "image/png", "失敗した画面");
        }
    }

    @After(value = "@ui", order = 1)
    public void 表示したすべての画面にアクセシビリティの違反がない() {
        assertThat(browser.accessibilityViolations()).isEmpty();
    }
}

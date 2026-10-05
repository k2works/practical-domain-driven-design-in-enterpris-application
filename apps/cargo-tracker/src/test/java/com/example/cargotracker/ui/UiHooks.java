package com.example.cargotracker.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;

/**
 * 画面の層のシナリオの後始末。失敗したら画面のスクリーンショットを添付し、
 * どのシナリオでも、表示したすべての画面にアクセシビリティの違反がないことを確かめる（テスト戦略）。
 */
public class UiHooks {

    /** デモの動画の名前を付ける印の始まり。 */
    private static final String DEMO_NAME_PREFIX = "@demo-";

    private final BrowserSession browser;

    public UiHooks(BrowserSession browser) {
        this.browser = browser;
    }

    /**
     * デモ項目のシナリオ（{@code @demo}）の画面の動画を残す（{@code ./gradlew demoVideo -PdemoBolt=bolt-10}）。
     * 動画の名前は、シナリオの {@code @demo-<名前>} の印から {@code <名前>.webm} にする。終了報告のデモ項目からリンクする。
     */
    @After(value = "@demo", order = 5_000)
    public void デモの動画を残す(Scenario scenario) {
        BrowserSession.videoDir()
                .ifPresent(dir -> scenario.getSourceTagNames().stream()
                        .filter(tag -> tag.startsWith(DEMO_NAME_PREFIX))
                        .findFirst()
                        .ifPresent(tag ->
                                browser.saveVideo(dir.resolve(tag.substring(DEMO_NAME_PREFIX.length()) + ".webm"))));
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

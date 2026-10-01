package com.example.cargotracker.ui;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.cucumber.spring.ScenarioScope;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;

/**
 * シナリオごとのブラウザ（Chromium、ヘッドレス）。表示した画面ごとに axe-core で検査し、違反をためておく。
 * Playwright とブラウザの起動は重いため、JVM で 1 つだけ作って使い回す。
 */
@ScenarioScope
public class BrowserSession implements DisposableBean {

    private static final Browser BROWSER = Playwright.create().chromium().launch();

    private final BrowserContext context = BROWSER.newContext();
    private final Page page = context.newPage();
    private final List<String> accessibilityViolations = new ArrayList<>();

    public Page page() {
        return page;
    }

    /**
     * いま表示している画面を axe-core で検査し、違反があればためる。
     */
    public void checkAccessibility() {
        for (Rule violation : new AxeBuilder(page).analyze().getViolations()) {
            accessibilityViolations.add(page.url() + " " + violation.getId() + ": " + violation.getHelp());
        }
    }

    public List<String> accessibilityViolations() {
        return List.copyOf(accessibilityViolations);
    }

    @Override
    public void destroy() {
        context.close();
    }
}

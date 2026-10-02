package com.example.cargotracker.ui;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.cucumber.spring.ScenarioScope;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;

/**
 * シナリオごとのブラウザの文脈とページ。Cookie やストレージをシナリオの間で共有しない。
 * 表示した画面ごとに axe-core で検査し、違反をためる（確かめるのは {@link UiHooks}）。
 */
@ScenarioScope
public class BrowserSession implements DisposableBean {

    /** 検査の基準。テスト戦略・NFR-ACCESS-01 の WCAG 2.2 AA。 */
    private static final List<String> WCAG_TAGS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");

    private final BrowserContext context;
    private final Page page;
    private final List<String> accessibilityViolations = new ArrayList<>();

    public BrowserSession(PlaywrightBrowser browser) {
        this.context = browser.newContext();
        this.page = context.newPage();
    }

    public Page page() {
        return page;
    }

    /**
     * いま表示している画面を axe-core で検査し、違反があればためる。
     */
    public void checkAccessibility() {
        for (Rule violation : new AxeBuilder(page).withTags(WCAG_TAGS).analyze().getViolations()) {
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

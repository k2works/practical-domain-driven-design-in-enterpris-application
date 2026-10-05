package com.example.cargotracker.ui;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Video;
import io.cucumber.spring.ScenarioScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /**
     * デモの動画を残すときの置き場所の設定（{@code ./gradlew demoVideo} が渡す）。指定があれば、シナリオの画面を録画する。
     */
    static final String VIDEO_DIR_PROPERTY = "cargotracker.ui.video-dir";

    /** 録画の画面の大きさ（16:9）。 */
    private static final int VIDEO_WIDTH = 1280;

    private static final int VIDEO_HEIGHT = 720;

    public BrowserSession(PlaywrightBrowser browser) {
        this.context = videoDir()
                .map(dir -> browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(VIDEO_WIDTH, VIDEO_HEIGHT)
                        .setRecordVideoDir(dir.resolve("raw"))
                        .setRecordVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)))
                .orElseGet(browser::newContext);
        this.page = context.newPage();
    }

    /** デモの動画の置き場所（指定がなければ録画しない）。 */
    static Optional<Path> videoDir() {
        String dir = System.getProperty(VIDEO_DIR_PROPERTY, "");
        return dir.isBlank() ? Optional.empty() : Optional.of(Path.of(dir));
    }

    /**
     * 録画を閉じて、指定の場所に保存する（録画していなければ何もしない）。動画はページを閉じたときに書き終わるため、
     * 文脈を閉じてから保存し、録画の一時ファイルを消す。
     */
    public void saveVideo(Path target) {
        Video video = page.video();
        if (video == null) {
            return;
        }
        context.close();
        video.saveAs(target);
        video.delete();
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

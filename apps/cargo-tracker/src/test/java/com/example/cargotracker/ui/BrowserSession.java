package com.example.cargotracker.ui;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.example.cargotracker.shared.domain.Role;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.spring.ScenarioScope;
import java.net.URI;
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
    private final UiUsers users;
    private Role signedIn;

    /**
     * デモの動画を残すときの置き場所の設定（{@code ./gradlew demoVideo} が渡す）。指定があれば、シナリオの画面を録画する。
     */
    static final String VIDEO_DIR_PROPERTY = "cargotracker.ui.video-dir";

    /** 録画の画面の大きさ（16:9）。 */
    private static final int VIDEO_WIDTH = 1280;

    private static final int VIDEO_HEIGHT = 720;

    public BrowserSession(PlaywrightBrowser browser, UiUsers users) {
        this.users = users;
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
     * 録画を閉じて、指定の場所に保存する（録画していなければ何もしない）。1 つのシナリオを複数の Bolt のデモに使うときは、
     * 同じ動画をそれぞれの場所に保存する。動画はページを閉じたときに書き終わるため、文脈を閉じてから保存し、録画の一時ファイルを消す。
     */
    public void saveVideo(List<Path> targets) {
        Video video = page.video();
        if (video == null || targets.isEmpty()) {
            return;
        }
        context.close();
        targets.forEach(video::saveAs);
        video.delete();
    }

    public Page page() {
        return page;
    }

    /**
     * 画面を開く。荷主の画面（{@code /customer/}）は荷主担当者、社内の画面（{@code /staff/}）は営業担当者でログインしてから開く
     * （Bolt 14）。いまの利用者の役割が違えば、Cookie を消して A-01 からログインし直す。
     */
    public void navigate(String url) {
        Role role = url.contains("/customer/") ? Role.SHIPPER : url.contains("/staff/") ? Role.SALES : null;
        if (role != null && role != signedIn) {
            signInAs(role, URI.create(url).resolve("/login").toString());
        }
        page.navigate(url);
    }

    /** A-01 からその役割の利用者でログインする（Cookie を消してから）。 */
    public void signInAs(Role role, String loginUrl) {
        UiUsers.Credentials credentials = users.of(role);
        context.clearCookies();
        page.navigate(loginUrl);
        checkAccessibility();
        page.getByLabel("メールアドレス").fill(credentials.email());
        page.getByLabel("password").fill(credentials.password());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン"))
                .click();
        page.waitForURL(url -> !url.contains("/login"));
        signedIn = role;
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

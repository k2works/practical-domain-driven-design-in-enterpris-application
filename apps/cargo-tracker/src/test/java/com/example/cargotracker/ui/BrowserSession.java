package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.example.cargotracker.shared.domain.Role;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Clock;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Video;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.spring.ScenarioScope;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
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

    /** Tab でフォーカスを進める回数の上限。いちばん遠い要素に届く画面に合わせる（Bolt 29、#45）。 */
    private static final int MAX_TABS = 80;

    /** ラジオボタンのグループまで Tab でフォーカスを進める回数の上限（Bolt 29、#45）。 */
    private static final int MAX_TABS_TO_GROUP = 60;

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
     * 画面を開く。荷主の画面（{@code /customer/}）は荷主担当者、経路設計の画面（{@code /staff/routing-cases}）は経路設計者
     * （Bolt 17）、追跡の画面（{@code /staff/tracking-records}）は追跡管理者（Bolt 26）、ほかの社内の画面（{@code /staff/}）は営業担当者でログインしてから開く（Bolt 14）。いまの利用者の役割が違えば、Cookie を消して A-01 からログインし直す。
     */
    public void navigate(String url) {
        Role role = url.contains("/customer/")
                ? Role.SHIPPER
                : url.contains("/staff/routing-cases")
                        ? Role.ROUTE_DESIGNER
                        : url.contains("/staff/tracking-records")
                                ? Role.TRACKING_MANAGER
                                : url.contains("/staff/") ? Role.SALES : null;
        if (role != null && role != signedIn) {
            signInAs(role, URI.create(url).resolve("/login").toString());
        }
        page.navigate(url);
    }

    /** A-01 からその役割の利用者でログインし、A-02 で TOTP のコードを入れる（Cookie を消してから。Bolt 31 で 2 段階にした）。 */
    public void signInAs(Role role, String loginUrl) {
        context.clearCookies();
        page.navigate(loginUrl);
        signInHere(role);
    }

    /**
     * いま開いている A-01 から、Cookie を消さずにその役割の利用者でログインする（Bolt 32e。失効の扱いが新しい session に覚えた
     * 要求へ、ログインの後に戻ることを確かめる）。
     */
    public void signInHere(Role role) {
        UiUsers.Credentials credentials = users.of(role);
        page.waitForURL(url -> URI.create(url).getPath().equals("/login"));
        checkAccessibility();
        page.getByLabel("メールアドレス").fill(credentials.email());
        page.getByLabel("password").fill(credentials.password());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン"))
                .click();
        // A-02 の URL も "/login" を含むので、A-02 に着いたことを待ってからコードを入れ、A-02 を離れたことを待つ
        page.waitForURL(url -> url.contains("/login/totp"));
        page.getByLabel("認証コード").fill(users.totpCode(role));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("確認"))
                .click();
        page.waitForURL(url -> !url.contains("/login"));
        signedIn = role;
    }

    /**
     * ブラウザの時計を差し替える（Bolt 32b。session の警告を 30 分待たずに確かめる）。同じ文脈のページ（別のタブも）が同じ時計を使う。
     * 読み込んだ後の画面のタイマーは差し替えた時計に乗らないので、ログインの前（画面を開く前）に入れる。時刻は今の実時間から始める
     * （サーバーの時計は実時間のまま。TOTP のコードはサーバーの時刻で作る）。
     */
    public void installClock() {
        context.clock().install(new Clock.InstallOptions().setTime(System.currentTimeMillis()));
    }

    /** ブラウザの時計を進め、その間に期限の来たタイマーを動かす（Bolt 32b）。サーバーの時計は進めない。 */
    public void fastForward(Duration duration) {
        context.clock().fastForward(duration.toMillis());
    }

    /** 同じ文脈（同じ Cookie とストレージ）で別のタブを開き、その URL を開く（Bolt 32b。タブの間の警告の同期）。 */
    public Page openTab(String url) {
        Page tab = context.newPage();
        tab.navigate(url);
        return tab;
    }

    /** いまの session の Cookie の値（Bolt 32b。テストの準備として session の属性を書き換える）。 */
    public String sessionCookieValue() {
        return context.cookies().stream()
                .filter(cookie -> "SESSION".equals(cookie.name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("session の Cookie がない"))
                .value;
    }

    /**
     * いま表示している画面を axe-core で検査し、違反があればためる。
     */
    public void checkAccessibility() {
        for (Rule violation : new AxeBuilder(page).withTags(WCAG_TAGS).analyze().getViolations()) {
            accessibilityViolations.add(page.url() + " " + violation.getId() + ": " + violation.getHelp() + " "
                    + violation.getNodes().stream()
                            .map(node -> String.valueOf(node.getTarget()))
                            .toList());
        }
    }

    /**
     * Tab を押してフォーカスを進め、目的の要素に届いたら止まる（届かなければ失敗する）。すでに目的の要素にフォーカスがあれば押さない。
     * 画面の層のステップ定義がキー操作だけで画面を操作するための部品で、待ち方と上限を直す場所をここ 1 つにする（Bolt 29、#45。T-87）。
     * 上限は失敗までの時間の上限で、フォーカスの順序の仕様ではない（順序はステップ定義の {@code tabTo} で確かめる）。
     * 目的の要素が画面に現れるまでは待ち（画面の遷移の直後でも押し始めない）、現れなければ Playwright の待ち時間で読める失敗にする。
     * 押す間の判定は待たない（{@code count}）。要素の自動待ちで 30 秒の TimeoutError になっていたのを避ける（Bolt 29 の開発レビュー）。
     */
    public void tabUntilFocused(Locator target) {
        assertThat(target).isAttached();
        Locator focused = target.and(page.locator(":focus"));
        for (int i = 0; i < MAX_TABS && focused.count() == 0; i++) {
            page.keyboard().press("Tab");
        }
        assertThat(target).isFocused();
    }

    /** ラジオボタンのグループ（名前は legend）の中の入力欄に届くまで Tab を進める（届かなければ失敗する。Bolt 29、#45）。 */
    public void tabUntilGroup(String legend) {
        Locator focused = page.getByRole(
                        AriaRole.GROUP,
                        new Page.GetByRoleOptions().setName(legend).setExact(true))
                .locator("input:focus");
        for (int i = 0; i < MAX_TABS_TO_GROUP && focused.count() == 0; i++) {
            page.keyboard().press("Tab");
        }
        assertThat(focused).hasCount(1);
    }

    public List<String> accessibilityViolations() {
        return List.copyOf(accessibilityViolations);
    }

    @Override
    public void destroy() {
        context.close();
    }
}

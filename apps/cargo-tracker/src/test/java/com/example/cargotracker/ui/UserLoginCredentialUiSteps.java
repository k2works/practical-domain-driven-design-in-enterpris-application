package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.Role;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 利用者の管理（S-20）のログインの資格（ロックの解除と管理者による TOTP の解除）の画面の層のステップ定義（Bolt 38c。US-18 AC3・AC9）。
 * 詳細を開くステップは {@link UserAdministrationUiSteps}、ログインのステップは {@link LoginUiSteps} のものを使う。
 */
public class UserLoginCredentialUiSteps {

    private static final DateTimeFormatter VERIFIED_AT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final BrowserSession browser;
    private final UiUsers users;
    private final JdbcTemplate jdbc;
    private final String baseUrl;

    /** 営業担当者の別の端末の Cookie（同じブラウザの Cookie を入れ替えて別の端末に見立てる）。 */
    private List<Cookie> otherDevice;

    public UserLoginCredentialUiSteps(
            BrowserSession browser,
            UiUsers users,
            JdbcTemplate jdbc,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.users = users;
        this.jdbc = jdbc;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    private Page page() {
        return browser.page();
    }

    private Locator credentials() {
        return page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("ログインの資格"));
    }

    @ならば("ログインの資格にロック中と期限と {string} が示される")
    public void ロック中と期限が示される(String totp) {
        // ロックの期限は「解ける日時」の項目に分ける（Bolt 38c2）
        assertThat(credentials().locator("#lock-state")).hasText("ロック: ロック中");
        assertThat(credentials().locator("#lock-until")).containsText("解ける日時: ");
        assertThat(credentials().locator("#lock-until")).containsText("Asia/Tokyo（UTC+09:00）");
        assertThat(credentials().locator("#totp-state")).hasText(totp);
    }

    @もし("ログインの資格の {string} を押す")
    public void 操作を押す(String button) {
        credentials()
                .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(button).setExact(true))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("ロックの解除の確認に対象と現在の状態と変更内容と影響と回復手段と実行者が示される")
    public void ロックの解除の確認が示される() {
        Locator main = page().locator("main");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("ロックを解除しますか");
        assertThat(main).containsText(users.of(Role.SALES).email());
        assertThat(main.locator("dd#current-state")).hasText("ロック中");
        assertThat(main.locator("dd#lock-until")).containsText("Asia/Tokyo（UTC+09:00）");
        assertThat(main.locator("dd#credential-change")).hasText("ロックを解除する");
        assertThat(main).containsText("すぐにログインを試せます。失敗の回数は 0 に戻ります。");
        assertThat(main).containsText("ロックの解除は取り消せません（5 回続けて誤ると、また 15 分ロックされます）。");
        assertThat(main).containsText("システム管理者 " + users.displayNameOf(Role.SYSTEM_ADMIN));
    }

    @ならば("認証アプリの登録の解除の確認に対象と現在の状態と変更内容と影響と回復手段と実行者と本人確認の欄が示される")
    public void TOTPの解除の確認が示される() {
        Locator main = page().locator("main");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("認証アプリの登録を解除しますか");
        assertThat(main).containsText(users.of(Role.SALES).email());
        assertThat(main.locator("dd#current-state")).hasText("認証アプリ: 登録済み");
        assertThat(main.locator("dd#credential-change")).hasText("認証アプリの登録と回復コードを消す");
        assertThat(main).containsText("この利用者のログイン中の session はすべて終わります。次のログインで認証アプリを登録し直します。本人への連絡は別に行ってください。");
        assertThat(main).containsText("本人が次のログインで登録し直します。");
        assertThat(main).containsText("システム管理者 " + users.displayNameOf(Role.SYSTEM_ADMIN));
        assertThat(page().getByLabel("登録の電話番号への折り返し")).isVisible();
        assertThat(page().getByLabel("対面", new Page.GetByLabelOptions().setExact(true)))
                .isVisible();
        assertThat(page().getByLabel("所属企業の管理担当者への確認")).isVisible();
        assertThat(page().getByLabel("本人確認の日時")).isVisible();
    }

    @もし("本人確認の手段 {string} を選び、本人確認の日時に今の日本時間を入れる")
    public void 本人確認を入れる(String method) {
        page().getByLabel(method, new Page.GetByLabelOptions().setExact(true)).check();
        // 今から 1 分引く（ui.base-url で外の環境に向けたとき、時計のずれと分の境目が重なっても未来にしない）
        page().getByLabel("本人確認の日時")
                .fill(VERIFIED_AT.format(
                        LocalDateTime.now(ZoneId.of("Asia/Tokyo")).minusMinutes(1)));
    }

    @もし("確認の画面で {string} を押す")
    public void 確認の画面で押す(String button) {
        page().getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(button).setExact(true))
                .click();
        page().waitForLoadState();
        browser.checkAccessibility();
    }

    @ならば("{string} と知らされ、ログインの資格に {string} が示される")
    public void 知らせとログインの資格が示される(String notice, String state) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText(notice);
        assertThat(credentials()).containsText(state);
        if (state.startsWith("ロック")) {
            assertThat(lockedUntilOfSales()).isNull();
        } else {
            assertThat(countOf("identity.totp_credential")).isZero();
            assertThat(countOf("identity.recovery_code")).isZero();
        }
    }

    @もし("営業担当者が別の端末でログインしている")
    public void 別の端末でログインしている() {
        browser.signInAs(Role.SALES, baseUrl + "/login");
        otherDevice = browser.cookies();
    }

    @もし("営業担当者の別の端末で業務の画面を開く")
    public void 別の端末で業務の画面を開く() {
        browser.restoreCookies(otherDevice);
        page().navigate(baseUrl + "/staff/transport-requests");
        page().waitForLoadState();
    }

    @ならば("再認証の案内に認証アプリの登録を解除した理由が示される")
    public void 再認証の案内に理由が示される() {
        page().waitForURL(url -> URI.create(url).getPath().equals("/session-expired"));
        assertThat(page().locator("main")).containsText("またはシステム管理者が認証アプリの登録を解除したため、ログアウトしました。");
        assertThat(page().locator("main")).containsText("システム管理者が認証アプリの登録を解除した場合は、次のログインで認証アプリを登録し直してください。");
        browser.checkAccessibility();
    }

    @ならば("営業担当者の業務の画面が表示される")
    public void 業務の画面が表示される() {
        assertThat(URI.create(page().url()).getPath()).doesNotContain("/login");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .isVisible();
        assertThat(countOf("identity.totp_credential")).isOne();
        browser.checkAccessibility();
    }

    @ならば("確認の画面のエラー要約に {string} と {string} が示され、認証アプリの登録は残っている")
    public void 確認の画面に誤りが示される(String first, String second) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).containsText("認証アプリの登録を解除できませんでした");
        assertThat(summary.getByRole(AriaRole.LINK)).hasText(new String[] {first, second});
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("認証アプリの登録を解除しますか");
        assertThat(countOf("identity.totp_credential")).isOne();
    }

    @ならば("ログインの資格の領域に {string} と示され、解除の操作はない")
    public void 案内が示され操作はない(String message) {
        assertThat(credentials().locator("#credential-note")).hasText(message);
        assertThat(credentials().getByRole(AriaRole.BUTTON)).hasCount(0);
    }

    private Object lockedUntilOfSales() {
        return jdbc.queryForObject(
                "SELECT locked_until FROM identity.login_failure WHERE user_id = ?",
                Object.class,
                users.of(Role.SALES).userId().value());
    }

    private int countOf(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?",
                Integer.class,
                users.of(Role.SALES).userId().value());
        return count == null ? 0 : count;
    }
}

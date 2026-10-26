package com.example.cargotracker.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.MailpitClient;
import com.example.cargotracker.MailpitClient.ReceivedMail;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * 利用者の登録（S-20。Bolt 38b3b、US-16 AC1）の画面の層のステップ定義。登録するメールアドレスはシナリオごとに作る。企業は、準備で
 * 足す名前の違う運用する会社（「B 運輸」）を選ぶ（利用者ごとに作る「A 社」と名前が重ならない）。
 */
public class UserRegistrationUiSteps {

    private final BrowserSession browser;
    private final JdbcTemplate jdbc;
    private final UiUsers users;
    private String registeredEmail;

    /** 招待のメールを待つ時間。 */
    private static final Duration MAIL_WAIT = Duration.ofSeconds(10);

    /** 招待のメールのリンク（公開の base URL は既定の localhost:8080 なので、path だけを取り出してテストのサーバーで開く）。 */
    private static final Pattern INVITATION_LINK =
            Pattern.compile("https?://[^/\\s]+(/password-reset/[A-Za-z0-9_-]{43})");

    /** 初回の設定で入れる password（ログインに使う）。 */
    private static final String INITIAL_PASSWORD = "my first long passphrase";

    private final GenericContainer<?> mailpitContainer;
    private final String baseUrl;
    private UserId registeredUser;

    public UserRegistrationUiSteps(
            BrowserSession browser,
            JdbcTemplate jdbc,
            UiUsers users,
            GenericContainer<?> mailpitContainer,
            @LocalServerPort int port,
            @Value("${ui.base-url:}") String configuredBaseUrl) {
        this.browser = browser;
        this.jdbc = jdbc;
        this.users = users;
        this.mailpitContainer = mailpitContainer;
        this.baseUrl = configuredBaseUrl.isBlank() ? "http://localhost:" + port : configuredBaseUrl;
    }

    @前提("管理範囲に運用する会社 {string} がある")
    public void 管理範囲に運用する会社がある(String name) {
        users.administeredOperator(name);
    }

    private Page page() {
        return browser.page();
    }

    private void openEntry() {
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("利用者を登録する"))
                .click();
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("利用者の登録");
        browser.checkAccessibility();
    }

    private void next() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("次へ"))
                .click();
        browser.checkAccessibility();
    }

    @かつ("利用者を登録するを開き、企業 {string} を選んで次へ進む")
    public void 開いて企業を選んで次へ(String company) {
        openEntry();
        企業を選んで次へ(company);
    }

    @かつ("利用者を登録するを開く")
    public void 利用者を登録するを開く() {
        openEntry();
    }

    @かつ("企業 {string} を選んで次へ進む")
    public void 企業を選んで次へ(String company) {
        page().getByLabel(company, new Page.GetByLabelOptions().setExact(true)).check();
        next();
        assertThat(page().locator("main")).containsText(company);
    }

    @かつ("利用者を登録するを開き、企業を選ばずに次へ進む")
    public void 企業を選ばずに次へ() {
        openEntry();
        next();
    }

    @かつ("付ける役割 {string} と新しいメールアドレスと表示名 {string} を入れて確認する")
    public void 入れて確認する(String role, String displayName) {
        registeredEmail = "registered-" + UUID.randomUUID() + "@ui.example";
        page().getByLabel(role, new Page.GetByLabelOptions().setExact(true)).check();
        page().getByLabel("メールアドレス", new Page.GetByLabelOptions().setExact(true))
                .fill(registeredEmail);
        page().getByLabel("表示名", new Page.GetByLabelOptions().setExact(true)).fill(displayName);
        confirm();
    }

    @かつ("付ける役割 {string} とメールアドレス {string} と表示名 {string} を入れて確認する")
    public void 決めた値を入れて確認する(String role, String email, String displayName) {
        registeredEmail = email;
        page().getByLabel(role, new Page.GetByLabelOptions().setExact(true)).check();
        page().getByLabel("メールアドレス", new Page.GetByLabelOptions().setExact(true))
                .fill(email);
        page().getByLabel("表示名", new Page.GetByLabelOptions().setExact(true)).fill(displayName);
        confirm();
    }

    @かつ("何も入れずに確認する")
    public void 何も入れずに確認する() {
        confirm();
    }

    private void confirm() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("確認する"))
                .click();
        browser.checkAccessibility();
    }

    @ならば("利用者の登録の確認に対象 {string} と変更内容と影響と実行者と回復手段が示される")
    public void 確認が示される(String company) {
        Locator main = page().locator("main");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("利用者を登録しますか");
        assertThat(main).containsText(company);
        assertThat(main).containsText(registeredEmail);
        assertThat(main).containsText("登録したメールアドレスへ、初回の設定の案内（24 時間有効）を送ります。");
        assertThat(main).containsText("システム管理者 " + users.displayNameOf(Role.SYSTEM_ADMIN));
        assertThat(main).containsText("誤って登録したときは、利用者の詳細から利用を停止してください（送った案内のリンクも使えなくなります）。");
    }

    @もし("利用者の登録の確認で登録する")
    public void 登録する() {
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登録する"))
                .click();
    }

    @ならば("利用者を登録したと示され、詳細に表示名 {string} と役割 {string} が示される")
    public void 登録したと示される(String displayName, String role) {
        assertThat(page().getByRole(AriaRole.STATUS)).hasText("利用者を登録しました。登録したメールアドレスへ初回の設定の案内を送ります。");
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .containsText(displayName);
        assertThat(page().locator("dd#current-roles")).hasText(role);
        browser.checkAccessibility();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM identity.app_user WHERE email = ?", Integer.class, registeredEmail))
                .isEqualTo(1);
    }

    @ならば("登録の誤り {string} がエラー要約に示される")
    public void 誤りが示される(String message) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).containsText("利用者を登録できませんでした");
        assertThat(summary).containsText(message);
    }

    @ならば("登録の誤り {string} と {string} と {string} がエラー要約に示される")
    public void 誤りが3つ示される(String first, String second, String third) {
        Locator links = page().getByRole(AriaRole.ALERT).getByRole(AriaRole.LINK);
        assertThat(page().getByRole(AriaRole.ALERT)).containsText("利用者を登録できませんでした");
        assertThat(links).hasText(new String[] {first, second, third});
    }

    @かつ("エラー要約にフォーカスがあり、最初の誤りのリンクを押すと {string} にフォーカスが移る")
    public void 最初のリンクで欄へ移る(String firstOption) {
        Locator summary = page().getByRole(AriaRole.ALERT);
        assertThat(summary).isFocused();
        summary.getByRole(AriaRole.LINK).first().click();
        assertThat(page().getByLabel(firstOption, new Page.GetByLabelOptions().setExact(true)))
                .isFocused();
        browser.checkAccessibility();
    }

    @もし("登録した利用者に届いた招待のメールのリンクを開く")
    public void 招待のメールのリンクを開く() {
        List<ReceivedMail> mails = new MailpitClient(mailpitContainer).awaitMessagesTo(registeredEmail, 1, MAIL_WAIT);
        assertThat(mails)
                .singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("【cargo-tracker】利用者の登録のご案内"));
        Matcher matcher = INVITATION_LINK.matcher(mails.getFirst().text());
        assertThat(matcher.find()).as("本文に初回の設定のリンクがある").isTrue();
        UUID userId =
                jdbc.queryForObject("SELECT id FROM identity.app_user WHERE email = ?", UUID.class, registeredEmail);
        registeredUser = new UserId(userId);
        // メールはハンドラーの確定の前に届くので、トークンが確定するまで待つ（T-127）
        await().atMost(MAIL_WAIT)
                .until(() -> jdbc.queryForObject(
                                "SELECT count(*) FROM identity.password_reset_token WHERE user_id = ?",
                                Integer.class,
                                userId)
                        > 0);
        page().navigate(baseUrl + matcher.group(1));
    }

    @かつ("初回の設定の画面で password を 2 回入れて設定する")
    public void 初回のpasswordを設定する() {
        page().waitForURL(url -> url.endsWith("/password-reset/new"));
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("password の設定");
        assertThat(page().locator("main")).containsText("初めて使う password を入力してください。");
        browser.checkAccessibility();
        page().getByLabel("新しい password", new Page.GetByLabelOptions().setExact(true))
                .fill(INITIAL_PASSWORD);
        page().getByLabel("新しい password（確認）").fill(INITIAL_PASSWORD);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("password を設定する"))
                .click();
    }

    @ならば("ログインの画面に password を設定した知らせが出る")
    public void passwordを設定した知らせが出る() {
        page().waitForURL(url -> url.contains("/login?initial-password-set"));
        Locator notice = page().getByRole(AriaRole.STATUS);
        assertThat(notice).containsText("password を設定しました。");
        assertThat(notice).containsText("このブラウザでログインしていた場合は、ログアウトしました。設定した password でログインし、認証アプリを登録してください。");
        browser.checkAccessibility();
    }

    @もし("登録した利用者が設定した password でログインする")
    public void 設定したpasswordでログインする() {
        page().getByLabel("メールアドレス").fill(registeredEmail);
        page().getByLabel("password", new Page.GetByLabelOptions().setExact(true))
                .fill(INITIAL_PASSWORD);
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン"))
                .click();
        page().waitForURL(url -> url.contains("/login/totp/enroll"));
    }

    @かつ("登録した利用者が認証アプリの登録の画面で手入力のキーから作ったコードを入れて登録する")
    public void 認証アプリを登録する() {
        browser.checkAccessibility();
        String manualKey = page().locator("code.app-totp-key").getAttribute("data-manual-key");
        page().getByLabel("認証コード").fill(users.codeFromKey(registeredUser, manualKey));
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("登録する"))
                .click();
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("回復コードの保存")))
                .isVisible();
        browser.checkAccessibility();
    }

    @かつ("登録した利用者が認証コードの画面で認証アプリのコードを入れて確認する")
    public void 認証コードを入れて確認する() {
        page().waitForURL(url -> url.contains("/login/totp"));
        browser.checkAccessibility();
        page().getByLabel("認証コード").fill(users.totpCode(registeredUser));
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("確認"))
                .click();
        page().waitForURL(url -> !url.contains("/login"));
    }

    @ならば("登録した利用者の業務の画面が表示される")
    public void 業務の画面が表示される() {
        assertThat(page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .isVisible();
        browser.checkAccessibility();
    }
}
